package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.NewUserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetPage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.SongCopySource
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

private const val OWNER = 1L
private const val OTHER = 2L

class CreateUserSheetTests {
    private val now = Instant.parse("2026-11-02T05:00:00.123456789Z")
    private val repository = FakeSheets()
    private val scores = FakeScores()
    private val service = CreateUserSheet(repository, scores, Clock.fixed(now, ZoneOffset.UTC))

    private fun blank(key: String = "k1", title: String = "새 악보 1") = CreateUserSheetCommand(
        userId = OWNER, idempotencyKey = key, title = title, startType = SheetStartType.BLANK, jangdanId = 3, bpm = 60, columnCount = 8,
    )

    private fun copySong(songId: Long = 10, jangdanId: Long? = null, columnCount: Int? = null) = CreateUserSheetCommand(
        userId = OWNER, idempotencyKey = "copy-$songId-$jangdanId-$columnCount", title = "아리랑 연습", startType = SheetStartType.COPY,
        jangdanId = jangdanId, bpm = 72, columnCount = columnCount, copyFromSongId = songId,
    )

    @Test
    fun `blank sheet needs review, keeps requested grid and has no score yet`() {
        val created = assertIs<CreateUserSheetResult.Created>(service.execute(blank(title = "  새 악보 1  ")))

        with(created.sheet) {
            assertEquals("새 악보 1", title)
            assertEquals(SheetStatus.NEEDS_REVIEW, status)
            assertEquals(3, jangdanId)
            assertEquals("JANGDAN_BEAT", bpmUnit)
            assertEquals(60, bpm)
            assertEquals(8, columnCount)
            assertEquals(1, scoreVersion)
            assertNull(scoreJson)
            assertEquals(0, noteCount)
        }
        assertEquals(emptyList(), created.events)
        assertEquals(Instant.parse("2026-11-02T05:00:00.123456Z"), repository.inserted.single().createdAt)
    }

    @Test
    fun `copying a scorable song takes its jangdan and columns and restarts the version`() {
        val created = assertIs<CreateUserSheetResult.Created>(service.execute(copySong()))

        with(created.sheet) {
            assertEquals(SheetStatus.EVALUABLE, status)
            assertEquals(1, jangdanId)
            assertEquals(72, bpm)
            assertEquals(12, columnCount)
            assertEquals("song-10@v1", scoreJson)
            assertEquals(5, noteCount)
        }
        assertEquals(listOf("song-10@v1"), created.events.map { it.eventId })
    }

    @Test
    fun `an unscorable song copy needs review`() {
        val created = assertIs<CreateUserSheetResult.Created>(service.execute(copySong(songId = 11)))

        assertEquals(SheetStatus.NEEDS_REVIEW, created.sheet.status)
    }

    @Test
    fun `copy accepts matching settings and rejects different ones`() {
        assertIs<CreateUserSheetResult.Created>(service.execute(copySong(jangdanId = 1, columnCount = 12)))
        assertEquals(CreateUserSheetResult.InvalidRequest, service.execute(copySong(jangdanId = 3)))
        assertEquals(CreateUserSheetResult.InvalidRequest, service.execute(copySong(columnCount = 8)))
    }

    @Test
    fun `copying my own sheet keeps its content and status, another member's sheet is not found`() {
        repository.store(sheet(id = 50, userId = OWNER, status = SheetStatus.EVALUABLE, json = "mine@v3", version = 3, noteCount = 4))
        repository.store(sheet(id = 51, userId = OTHER, status = SheetStatus.EVALUABLE, json = "theirs@v1", version = 1, noteCount = 4))
        val copy = CreateUserSheetCommand(OWNER, "copy-sheet", "복사본", SheetStartType.COPY, null, 90, null, copyFromSheetId = 50)

        val created = assertIs<CreateUserSheetResult.Created>(service.execute(copy))

        assertEquals(SheetStatus.EVALUABLE, created.sheet.status)
        assertEquals("mine@v1", created.sheet.scoreJson)
        assertEquals(1, created.sheet.scoreVersion)
        assertEquals(4, created.sheet.noteCount)
        assertEquals(CreateUserSheetResult.NotFound, service.execute(copy.copy(idempotencyKey = "other", copyFromSheetId = 51)))
    }

    @Test
    fun `copying an empty sheet stays empty`() {
        repository.store(sheet(id = 52, userId = OWNER, status = SheetStatus.NEEDS_REVIEW, json = null, version = 1, noteCount = 0))
        val copy = CreateUserSheetCommand(OWNER, "copy-empty", "빈 복사본", SheetStartType.COPY, null, 90, null, copyFromSheetId = 52)

        val created = assertIs<CreateUserSheetResult.Created>(service.execute(copy))

        assertNull(created.sheet.scoreJson)
        assertEquals(8, created.sheet.columnCount)
        assertEquals(emptyList(), created.events)
    }

    @Test
    fun `unknown jangdan or song is not found and a broken song score is a conflict`() {
        assertEquals(CreateUserSheetResult.NotFound, service.execute(blank().copy(jangdanId = 99)))
        assertEquals(CreateUserSheetResult.NotFound, service.execute(copySong(songId = 99)))
        assertEquals(CreateUserSheetResult.StateConflict, service.execute(copySong(songId = 12)))
        assertEquals(emptyList(), repository.inserted)
    }

    @Test
    fun `invalid shapes are rejected before anything is stored`() {
        val title50 = "가".repeat(49) + "🎵"
        assertIs<CreateUserSheetResult.Created>(service.execute(blank(key = "ok", title = title50)))
        repository.inserted.clear()
        val invalid = listOf(
            blank(title = "   "),
            blank(title = "가".repeat(51)),
            blank().copy(bpm = 29),
            blank().copy(bpm = 161),
            blank().copy(columnCount = 0),
            blank().copy(columnCount = 41),
            blank().copy(columnCount = null),
            blank().copy(jangdanId = null),
            blank().copy(copyFromSongId = 10),
            copySong().copy(copyFromSheetId = 50),
            copySong().copy(copyFromSongId = null),
            blank().copy(startType = SheetStartType.PDF, pdfSheetId = 5),
            blank().copy(pdfSheetId = 5),
        )
        for (command in invalid) assertEquals(CreateUserSheetResult.InvalidRequest, service.execute(command), command.toString())
        assertEquals(emptyList(), repository.inserted)
    }

    @Test
    fun `the same key replays the first sheet and a different body conflicts`() {
        val first = assertIs<CreateUserSheetResult.Created>(service.execute(blank()))

        val replay = assertIs<CreateUserSheetResult.Created>(service.execute(blank(title = " 새 악보 1")))
        assertEquals(first.sheet, replay.sheet)
        assertEquals(1, repository.inserted.size)
        assertEquals(CreateUserSheetResult.IdempotencyConflict, service.execute(blank().copy(bpm = 62)))
    }

    @Test
    fun `losing the unique key race answers with the winner`() {
        repository.raceWinner = blank().let { sheet(id = 77, userId = OWNER, status = SheetStatus.NEEDS_REVIEW, json = null, version = 1, noteCount = 0, hash = CreateUserSheet.requestHash(it)) }

        val created = assertIs<CreateUserSheetResult.Created>(service.execute(blank()))

        assertEquals(77, created.sheet.id)
    }

    private fun sheet(id: Long, userId: Long, status: SheetStatus, json: String?, version: Int, noteCount: Int, hash: String = "h") = UserSheet(
        id, userId, "원본", "JEONGGANBO", "MANUAL", status, 3, "타령", 70, "JANGDAN_BEAT", 8, version, json, noteCount, 0, hash,
        Instant.parse("2026-11-01T00:00:00Z"),
    )

    private class FakeScores : SheetScores {
        override fun copy(scoreJson: String, scoreVersion: Int): CopiedScore? = when {
            scoreJson == "broken" -> null
            else -> CopiedScore(scoreJson.substringBefore("@") + "@v$scoreVersion", 12, 5)
        }

        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) =
            listOf(ScoreTimelineEvent(scoreJson, "NOTE", 0, "1", 0, 0, 0, 0, 12, 0.0, 1.0))
    }

    private class FakeSheets : UserSheetRepository {
        private val sheets = mutableMapOf<Long, UserSheet>()
        val inserted = mutableListOf<NewUserSheet>()
        private val keys = mutableMapOf<Pair<Long, String>, Long>()
        var raceWinner: UserSheet? = null
        private var nextId = 100L

        fun store(sheet: UserSheet) {
            sheets[sheet.id] = sheet
        }

        override fun findByIdempotencyKey(userId: Long, idempotencyKey: String) =
            keys[userId to idempotencyKey]?.let(sheets::get) ?: raceWinner?.takeIf { inserted.isNotEmpty() }

        override fun findJangdan(jangdanId: Long) = when (jangdanId) {
            1L -> SheetJangdan(1, "DOTTED_QUARTER")
            3L -> SheetJangdan(3, "JANGDAN_BEAT")
            else -> null
        }

        override fun findSongSource(songId: Long) = when (songId) {
            10L -> SongCopySource(1, "DOTTED_QUARTER", "song-10@v4", scorable = true)
            11L -> SongCopySource(1, "DOTTED_QUARTER", "song-11@v1", scorable = false)
            12L -> SongCopySource(1, "DOTTED_QUARTER", "broken", scorable = true)
            else -> null
        }

        override fun find(sheetId: Long, userId: Long) = sheets[sheetId]?.takeIf { it.userId == userId }

        override fun insert(sheet: NewUserSheet): Long? {
            inserted += sheet
            if (raceWinner != null) return null
            val id = nextId++
            val name = if (sheet.jangdanId == 1L) "세마치" else "타령"
            val unit = if (sheet.jangdanId == 1L) "DOTTED_QUARTER" else "JANGDAN_BEAT"
            sheets[id] = UserSheet(
                id, sheet.userId, sheet.title, "JEONGGANBO", "MANUAL", sheet.status, sheet.jangdanId, name, sheet.bpm, unit,
                sheet.columnCount, 1, sheet.scoreJson, sheet.noteCount, 0, sheet.requestHash, sheet.createdAt,
            )
            keys[sheet.userId to sheet.idempotencyKey] = id
            return id
        }

        override fun page(userId: Long, kind: String?, page: Int, size: Int) = SheetPage(emptyList(), 0)
    }
}
