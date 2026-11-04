package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*
import org.junit.jupiter.api.Test

class CreatePracticeSessionTests {
    private val now = Instant.parse("2026-10-28T03:00:00.123456789Z")
    private val repository = MemoryPracticeSessionRepository().apply {
        songs[101] = PracticeSong(101, BpmUnit.DOTTED_QUARTER, noteCount = 72, scoreVersion = 3)
        songs[102] = PracticeSong(102, BpmUnit.JEONGGAN, noteCount = 10, scoreVersion = 1)
    }
    private val service = CreatePracticeSession(repository, Clock.fixed(now, ZoneOffset.UTC))

    private fun command(key: String = "key-1", userId: Long = 1, songId: Long = 101, bpm: Int = 96) =
        CreatePracticeSessionCommand(userId = userId, idempotencyKey = key, songId = songId, bpm = bpm)

    private fun created(result: CreatePracticeSessionResult) = assertIs<CreatePracticeSessionResult.Created>(result).session

    @Test
    fun `defaults whole song range, song bpm unit and current score version`() {
        val session = created(service.execute(command()))
        assertEquals("RECORDING", session.status)
        assertEquals(BpmUnit.DOTTED_QUARTER, session.bpmUnit)
        assertEquals(0 to 71, session.startNoteIdx to session.endNoteIdx)
        assertEquals(Instant.parse("2026-10-28T03:00:00.123456Z"), session.startedAt)
        val saved = repository.inserted.single()
        assertEquals(3, saved.scoreVersion)
        assertEquals(CreatePracticeSession.requestHash(command()), saved.requestHash)
    }

    @Test
    fun `keeps explicit bpm unit and section inside the song`() {
        val session = created(service.execute(command().copy(bpmUnit = BpmUnit.QUARTER, startNoteIdx = 5, endNoteIdx = 5)))
        assertEquals(BpmUnit.QUARTER, session.bpmUnit)
        assertEquals(5 to 5, session.startNoteIdx to session.endNoteIdx)
    }

    @Test
    fun `rejects section outside the song without saving`() {
        for ((start, end) in listOf(-1 to 3, 4 to 3, 0 to 72, 72 to null)) {
            val result = service.execute(command(key = "k$start-$end").copy(startNoteIdx = start, endNoteIdx = end))
            assertEquals(CreatePracticeSessionResult.InvalidRequest, result, "$start..$end")
        }
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `unknown song is not found`() {
        assertEquals(CreatePracticeSessionResult.NotFound, service.execute(command(songId = 999)))
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `request hash keeps the v1 format with the former mission slot`() {
        // 이미 저장된 세션의 request_hash와 같아야 같은 키의 재전송이 409가 되지 않는다.
        val canonical = "v1|101|96|-|0|-|-|-|false"
        val expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()))
        assertEquals(expected, CreatePracticeSession.requestHash(command()))
    }

    @Test
    fun `parent session must be own, same song and done`() {
        repository.sessions[10] = ParentSessionCandidate(10, userId = 1, songId = 101, status = "DONE")
        repository.sessions[11] = ParentSessionCandidate(11, userId = 2, songId = 101, status = "DONE")
        repository.sessions[12] = ParentSessionCandidate(12, userId = 1, songId = 102, status = "DONE")
        repository.sessions[13] = ParentSessionCandidate(13, userId = 1, songId = 101, status = "FAILED")
        assertEquals(CreatePracticeSessionResult.NotFound, service.execute(command(key = "a").copy(parentSessionId = 99)))
        assertEquals(CreatePracticeSessionResult.NotFound, service.execute(command(key = "b").copy(parentSessionId = 11)))
        assertEquals(CreatePracticeSessionResult.InvalidRequest, service.execute(command(key = "c").copy(parentSessionId = 12)))
        assertEquals(CreatePracticeSessionResult.StateConflict, service.execute(command(key = "d").copy(parentSessionId = 13)))
        created(service.execute(command(key = "e").copy(parentSessionId = 10)))
        assertEquals(10, repository.inserted.single().parentSessionId)
    }

    @Test
    fun `same key and same body returns the first session even after the song is hidden`() {
        val first = created(service.execute(command()))
        repository.songs.remove(101)
        // 기본값을 직접 적어도 같은 요청으로 본다.
        val again = created(service.execute(command().copy(startNoteIdx = 0, jangdanSoundOn = false)))
        assertEquals(first, again)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `same key with a different body conflicts, other users and keys are independent`() {
        created(service.execute(command()))
        assertEquals(CreatePracticeSessionResult.IdempotencyConflict, service.execute(command(bpm = 97)))
        assertEquals(CreatePracticeSessionResult.IdempotencyConflict, service.execute(command().copy(jangdanSoundOn = true)))
        created(service.execute(command(userId = 2)))
        created(service.execute(command(key = "key-2")))
        assertEquals(3, repository.inserted.size)
    }

    private val winner = PracticeSession(77, 101, "RECORDING", 96, BpmUnit.DOTTED_QUARTER, 0, 71, now, CreatePracticeSession.requestHash(command()))

    @Test
    fun `losing a concurrent insert with the same body answers the stored winner`() {
        repository.raceWinner = winner
        assertEquals(winner, created(service.execute(command())))
        assertTrue(repository.lostRace)
    }

    @Test
    fun `losing a concurrent insert with a different body conflicts`() {
        repository.raceWinner = winner.copy(requestHash = "other")
        assertEquals(CreatePracticeSessionResult.IdempotencyConflict, service.execute(command()))
        // 시작 조회에서는 없던 키라 경합에 진 뒤 다시 읽은 결과로 판정했다.
        assertTrue(repository.lostRace)
    }
}

class MemoryPracticeSessionRepository : PracticeSessionCreationRepository {
    val songs = mutableMapOf<Long, PracticeSong>()
    val sessions = mutableMapOf<Long, ParentSessionCandidate>()
    val inserted = mutableListOf<NewPracticeSession>()
    private val byKey = mutableMapOf<Pair<Long, String>, PracticeSession>()

    // 설정하면 insert가 유일 제약에 걸린 것처럼 null을 돌려주고, 이후 조회는 이 세션을 돌려준다.
    var raceWinner: PracticeSession? = null

    override fun findByIdempotencyKey(userId: Long, idempotencyKey: String): PracticeSession? =
        byKey[userId to idempotencyKey] ?: raceWinner.takeIf { lostRace }

    var lostRace = false
        private set

    override fun findPlayableSong(songId: Long) = songs[songId]
    override fun findSession(sessionId: Long) = sessions[sessionId]

    override fun insert(session: NewPracticeSession): Long? {
        if (raceWinner != null) {
            lostRace = true
            return null
        }
        inserted += session
        val id = 500L + inserted.size
        byKey[session.userId to session.idempotencyKey] = PracticeSession(
            id, session.songId, "RECORDING", session.bpm, session.bpmUnit, session.startNoteIdx, session.endNoteIdx, session.startedAt, session.requestHash,
        )
        return id
    }
}
