package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.BpmUnit
import com.ssafy.eolssu.backend.feature.practice.domain.NewPracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionCreationRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.HexFormat

data class CreatePracticeSessionCommand(
    val userId: Long,
    val idempotencyKey: String,
    val songId: Long,
    val bpm: Int,
    val bpmUnit: BpmUnit? = null,
    val startNoteIdx: Int? = null,
    val endNoteIdx: Int? = null,
    val parentSessionId: Long? = null,
    val jangdanSoundOn: Boolean? = null,
)

sealed interface CreatePracticeSessionResult {
    data class Created(val session: PracticeSession) : CreatePracticeSessionResult
    data object NotFound : CreatePracticeSessionResult
    data object InvalidRequest : CreatePracticeSessionResult
    data object StateConflict : CreatePracticeSessionResult
    data object IdempotencyConflict : CreatePracticeSessionResult
}

class CreatePracticeSession(
    private val repository: PracticeSessionCreationRepository,
    private val clock: Clock,
) {
    fun execute(command: CreatePracticeSessionCommand): CreatePracticeSessionResult {
        val hash = requestHash(command)
        // 같은 키의 재전송은 곡·부모 세션 상태가 바뀌었어도 처음 결과를 그대로 돌려준다(PRA-01 규칙 5·6).
        repository.findByIdempotencyKey(command.userId, command.idempotencyKey)?.let { return replay(it, hash) }

        val song = repository.findPlayableSong(command.songId) ?: return CreatePracticeSessionResult.NotFound
        command.parentSessionId?.let { parentId ->
            val parent = repository.findSession(parentId)
            if (parent == null || parent.userId != command.userId) return CreatePracticeSessionResult.NotFound
            if (parent.songId != song.id) return CreatePracticeSessionResult.InvalidRequest
            if (parent.status != "DONE") return CreatePracticeSessionResult.StateConflict
        }
        val start = command.startNoteIdx ?: 0
        val end = command.endNoteIdx ?: (song.noteCount - 1)
        if (start < 0 || end < start || end >= song.noteCount) return CreatePracticeSessionResult.InvalidRequest

        val session = NewPracticeSession(
            userId = command.userId,
            songId = song.id,
            parentSessionId = command.parentSessionId,
            scoreVersion = song.scoreVersion,
            bpm = command.bpm,
            bpmUnit = command.bpmUnit ?: song.bpmUnit,
            idempotencyKey = command.idempotencyKey,
            requestHash = hash,
            startNoteIdx = start,
            endNoteIdx = end,
            startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
        )
        val id = repository.insert(session)
            // 같은 키의 동시 요청이 먼저 저장했다. 그 결과로 같은 판정을 한다.
            ?: return replay(checkNotNull(repository.findByIdempotencyKey(command.userId, command.idempotencyKey)), hash)
        return CreatePracticeSessionResult.Created(
            PracticeSession(id, session.songId, "RECORDING", session.bpm, session.bpmUnit, start, end, session.startedAt, hash),
        )
    }

    private fun replay(existing: PracticeSession, hash: String) =
        if (existing.requestHash == hash) CreatePracticeSessionResult.Created(existing)
        else CreatePracticeSessionResult.IdempotencyConflict

    companion object {
        /** 기본값(시작 0, 장단 소리 false)을 채운 본문의 SHA-256. JSON 공백·키 순서가 달라도 같은 요청이면 같다. */
        fun requestHash(command: CreatePracticeSessionCommand): String {
            val canonical = listOf(
                "v1",
                command.songId,
                command.bpm,
                command.bpmUnit?.name ?: "-",
                command.startNoteIdx ?: 0,
                command.endNoteIdx ?: "-",
                command.parentSessionId ?: "-",
                // 미션을 받던 자리다. 저장된 해시와 같게 유지해야 기존 세션의 재전송이 409가 되지 않는다.
                "-",
                command.jangdanSoundOn ?: false,
            ).joinToString("|")
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()))
        }
    }
}
