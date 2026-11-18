package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraft
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraftUpdate
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetPage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetScoreUpdate
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val OWNER = 1L
private const val OTHER = 2L

class EditUserSheetTests {
    private val now = Instant.parse("2026-11-03T03:10:00.123456789Z")
    private val repository = Sheets()
    private val scores = Scores()
    private val service = EditUserSheet(repository, scores, Clock.fixed(now, ZoneOffset.UTC), jangdanValidation = null)

    @Test fun `an unavailable preflight HTTP callback leaves the score and draft untouched`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        var calls = 0
        server.createContext("/v1/estimate") { exchange ->
            calls++
            exchange.requestBody.readAllBytes()
            val body = """{"errorCode":"TIMEOUT"}""".toByteArray()
            exchange.sendResponseHeaders(504, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val prepared = object : SheetScores by scores {
                override fun prepare(scoreJson: String, scoreVersion: Int) =
                    PreparedScore.Ready("""{"score":{"instrument":"DANSO"}}""", 1, 1, "", "QUARTER")
            }
            val client = com.ssafy.eolssu.backend.feature.sheet.infrastructure.HttpPlaybackClient(
                "http://127.0.0.1:${server.address.port}", java.time.Duration.ofSeconds(2), tools.jackson.databind.json.JsonMapper.builder().build())
            val edits = EditUserSheet(repository, prepared, Clock.fixed(now, ZoneOffset.UTC), playback = client, jangdanValidation = null)
            val result = assertIs<SaveSheetScoreResult.Rejected>(edits.save(save(jangdanId = null)))
            assertEquals(com.ssafy.eolssu.backend.common.error.ErrorCode.AUDIO_UNAVAILABLE, result.code)
            assertEquals(1, calls)
            assertTrue(repository.scoreUpdates.isEmpty())
            assertTrue(repository.draftUpdates.isEmpty())
        } finally { server.stop(0) }
    }

    @Test fun `a rejected sigimsae keeps its event and symbol in the confirmation rejection`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/estimate") { exchange ->
            exchange.requestBody.readAllBytes()
            val body = """{"errorCode":"INVALID_REQUEST","details":{"reason":"SIGIMSAE_UNRENDERABLE","eventId":"c0-j1-r0-u0","symbols":["전성"],"message":"미지원 기호"}}"""
                .toByteArray()
            exchange.sendResponseHeaders(400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val prepared = object : SheetScores by scores {
                override fun prepare(scoreJson: String, scoreVersion: Int) =
                    PreparedScore.Ready("""{"score":{"instrument":"DANSO"}}""", 1, 1, "", "QUARTER")
            }
            val client = com.ssafy.eolssu.backend.feature.sheet.infrastructure.HttpPlaybackClient(
                "http://127.0.0.1:${server.address.port}", java.time.Duration.ofSeconds(2), tools.jackson.databind.json.JsonMapper.builder().build())
            val edits = EditUserSheet(repository, prepared, Clock.fixed(now, ZoneOffset.UTC), playback = client, jangdanValidation = null)
            val result = assertIs<SaveSheetScoreResult.Rejected>(edits.save(save(jangdanId = null)))
            assertEquals(com.ssafy.eolssu.backend.common.error.ErrorCode.INVALID_REQUEST, result.code)
            val details = assertIs<tools.jackson.databind.JsonNode>(result.details)
            assertEquals("SIGIMSAE_UNRENDERABLE", details.path("reason").asString())
            assertEquals("c0-j1-r0-u0", details.path("eventId").asString())
            assertEquals("전성", details.path("symbols").get(0).asString())
            assertTrue(repository.scoreUpdates.isEmpty())
        } finally { server.stop(0) }
    }

    @Test fun `omitted expressions retain draft-only tempo through resave and confirmation`() {
        retainDraftTempo(confirmedTempo = null)
    }

    @Test fun `omitted expressions prefer the new draft tempo over the confirmed plan`() {
        retainDraftTempo(confirmedTempo = 60)
    }

    @Test fun `null and missing draft companion still fall back to the confirmed plan`() {
        for (field in listOf("", ",\"expressions\":null")) retainDraftTempo(confirmedTempo = 80, initialDraftField = field)
    }

    private fun retainDraftTempo(confirmedTempo: Int?, initialDraftField: String? = null) {
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val original = checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()
        val expressions = SheetExpressions(json)
        val hash = expressions.hash(original)
        fun companion(bpm: Int) = """{"schema":"common-score-expressions/v1","sourceSha256":"$hash","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$hash","tempos":[{"gak":1,"cell":3,"bpm":$bpm}]}}"""
        repository.scoreUpdates.clear()
        repository.draftUpdates.clear()
        repository.sheets[701] = sheet(701, OWNER).copy(scoreJson = original, bpm = 100,
            expressionsJson = confirmedTempo?.let(::companion),
            draft = initialDraftField?.let { SheetDraft("""{"score":$original$it}""", 1, now) })
        val actualScores = object : SheetScores by scores {
            override fun unfilledCellCount(scoreJson: String) = 0
            override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore {
                val prepared = json.readTree(scoreJson)
                (prepared.path("score") as tools.jackson.databind.node.ObjectNode).put("scoreVersion", scoreVersion)
                return PreparedScore.Ready(prepared.toString(), 1, 4, "", "QUARTER")
            }
        }
        val edits = EditUserSheet(repository, actualScores, Clock.fixed(now, ZoneOffset.UTC), jangdanValidation = null)
        assertIs<SaveSheetDraftResult.Saved>(edits.saveDraft(draft(score = original).copy(bpm = 120, expressionsJson = companion(80).takeIf { initialDraftField == null })))
        assertIs<SaveSheetDraftResult.Saved>(edits.saveDraft(draft(revision = 1, score = original).copy(bpm = 120)))
        val resumed = json.readTree(repository.draftUpdates.last().draftJson).path("expressions")
        assertTrue(resumed.isObject, "Omitted companion must retain the draft plan")
        assertEquals(80.0, resumed.path("performancePlan").path("tempos")[0].path("bpm").asDouble())
        assertIs<SaveSheetScoreResult.Saved>(edits.save(save(score = original, jangdanId = null).copy(bpm = 120)))
        val saved = repository.scoreUpdates.single()
        val result = json.readTree(saved.expressionsJson)
        assertEquals(96.0, result.path("performancePlan").path("tempos")[0].path("bpm").asDouble())
        assertEquals(expressions.hash(saved.scoreJson), result.path("sourceSha256").asString())
        assertEquals(expressions.hash(saved.scoreJson), result.path("performancePlan").path("input_sha256").asString())
    }

    private fun save(
        sheetId: Long = 701,
        base: Int = 1,
        score: String = "full",
        jangdanId: Long? = 3,
        title: String? = null,
        resolved: List<String> = emptyList(),
    ) = SaveSheetScoreCommand(OWNER, sheetId, base, title, jangdanId, 60, score, resolved)

    private fun draft(sheetId: Long = 701, base: Int = 1, revision: Int = 0, score: String = "partial") =
        SaveSheetDraftCommand(OWNER, sheetId, base, revision, "고치는 중", 3, 66, score)

    @Test
    fun `saving the whole score bumps the version, makes the sheet evaluable and clears the draft`() {
        repository.sheets[701] = repository.sheets.getValue(701).copy(draft = SheetDraft("{}", 1, now), draftRevision = 4)

        val saved = assertIs<SaveSheetScoreResult.Saved>(service.save(save(title = "  아리랑 (내 편곡)  ")))

        val update = repository.scoreUpdates.single()
        assertEquals(1, update.baseScoreVersion)
        assertEquals("full@v2", update.scoreJson)
        assertEquals(Triple(16, 84, "JANGDAN_BEAT"), Triple(update.columnCount, update.noteCount, update.bpmUnit))
        assertEquals("아리랑 (내 편곡)", update.title)
        assertEquals(Instant.parse("2026-11-03T03:10:00.123456Z"), update.updatedAt)
        with(saved.sheet) {
            assertEquals(2, scoreVersion)
            assertEquals(SheetStatus.EVALUABLE, status)
            assertNull(draft)
            assertEquals(0, draftRevision)
        }
        assertEquals(listOf("full@v2"), saved.events.map { it.eventId })
    }

    @Test
    fun `omitting the title keeps the stored one`() {
        service.save(save())

        assertEquals("새 악보 1", repository.scoreUpdates.single().title)
    }

    @Test
    fun `an empty cell is reported with its id and nothing is saved`() {
        assertEquals(SaveSheetScoreResult.UnfilledCells(listOf("c5-j4")), service.save(save(score = "partial")))
        assertEquals(emptyList(), repository.scoreUpdates)
    }

    @Test
    fun `a stale base version conflicts without saving`() {
        assertEquals(SaveSheetScoreResult.StateConflict, service.save(save(base = 2)))
        assertEquals(emptyList(), repository.scoreUpdates)
    }

    @Test
    fun `a version changed between read and write still conflicts`() {
        repository.raceOnUpdate = true

        assertEquals(SaveSheetScoreResult.StateConflict, service.save(save()))
    }

    @Test
    fun `a sheet deleted right after the save is reported as not found, not as a server error`() {
        repository.deleteAfterUpdate = true

        assertEquals(SaveSheetScoreResult.NotFound, service.save(save()))
    }

    @Test
    fun `another member's or a missing sheet is not found`() {
        assertEquals(SaveSheetScoreResult.NotFound, service.save(save(sheetId = 702)))
        assertEquals(SaveSheetScoreResult.NotFound, service.save(save(sheetId = 999)))
    }

    @Test
    fun `the requested jangdan must match the score's jangdan and unit`() {
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(jangdanId = 1)))
        assertEquals(SaveSheetScoreResult.NotFound, service.save(save(jangdanId = 9)))
    }

    @Test
    fun `a sheet without jangdan takes its unit from the score`() {
        assertIs<SaveSheetScoreResult.Saved>(service.save(save(jangdanId = null, score = "nojangdan")))

        assertEquals("QUARTER", repository.scoreUpdates.single().bpmUnit)
    }

    @Test
    fun `a score the CAT-04 decoder rejects or that is not common score v1 is invalid`() {
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(score = "badticks")))
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(score = "nonsense")))
    }

    @Test
    fun `issues cannot be resolved while sheets have none`() {
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(resolved = listOf("i1"))))
    }

    @Test
    fun `a title outside 1 to 50 characters or a nonpositive bpm is invalid`() {
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(title = "   ")))
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save(title = "가".repeat(51))))
        assertEquals(SaveSheetScoreResult.InvalidRequest, service.save(save().copy(bpm = 0)))
    }

    @Test
    fun `a score over 1 MiB is too large`() {
        assertEquals(SaveSheetScoreResult.TooLarge, service.save(save(score = "x".repeat(EditUserSheet.MAX_SCORE_BYTES + 1))))
        assertIs<SaveSheetScoreResult.InvalidRequest>(service.save(save(score = "x".repeat(EditUserSheet.MAX_SCORE_BYTES))))
    }

    @Test
    fun `autosave stores the draft with its settings and counts empty cells without touching the version`() {
        val saved = assertIs<SaveSheetDraftResult.Saved>(service.saveDraft(draft()))

        assertEquals(SaveSheetDraftResult.Saved(1, 1, Instant.parse("2026-11-03T03:10:00.123456Z")), saved)
        val update = repository.draftUpdates.single()
        assertEquals(Triple(1, 0, Instant.parse("2026-11-03T03:10:00.123456Z")), Triple(update.baseScoreVersion, update.draftRevision, update.savedAt))
        assertEquals("""{"title":"고치는 중","jangdanId":"3","bpm":66,"score":partial,"expressions":null}""", update.draftJson)
        assertEquals(1, repository.sheets.getValue(701).scoreVersion)
    }

    @Test
    fun `autosave from a stale version or revision conflicts`() {
        assertEquals(SaveSheetDraftResult.StateConflict, service.saveDraft(draft(base = 2)))
        assertEquals(SaveSheetDraftResult.StateConflict, service.saveDraft(draft(revision = 3)))
        assertEquals(emptyList(), repository.draftUpdates)
    }

    @Test
    fun `autosave rejects a document that is not shaped like common score v1 and other members' sheets`() {
        assertEquals(SaveSheetDraftResult.InvalidRequest, service.saveDraft(draft(score = "nonsense")))
        assertEquals(SaveSheetDraftResult.InvalidRequest, service.saveDraft(draft(revision = -1)))
        assertEquals(SaveSheetDraftResult.NotFound, service.saveDraft(draft(sheetId = 702)))
        assertEquals(SaveSheetDraftResult.TooLarge, service.saveDraft(draft(score = "x".repeat(EditUserSheet.MAX_SCORE_BYTES + 1))))
    }

    @Test
    fun `deleting removes only the owner's sheet`() {
        assertEquals(false, service.delete(OWNER, 702))
        assertTrue(service.delete(OWNER, 701))
        assertEquals(false, service.delete(OWNER, 701))
        assertEquals(setOf(702L), repository.sheets.keys)
    }

    private class Scores : SheetScores {
        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) =
            if (scoreJson.startsWith("badticks")) null
            else listOf(ScoreTimelineEvent(scoreJson, "NOTE", 0, "1", 0, 0, 0, 0, 480, 0.0, 1.0))

        override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore = when (scoreJson) {
            "full" -> PreparedScore.Ready("full@v$scoreVersion", 16, 84, "세마치", "JANGDAN_BEAT")
            "nojangdan" -> PreparedScore.Ready("nojangdan@v$scoreVersion", 4, 10, "", "QUARTER")
            "badticks" -> PreparedScore.Ready("badticks@v$scoreVersion", 4, 10, "세마치", "JANGDAN_BEAT")
            "partial" -> PreparedScore.Unfilled(listOf("c5-j4"))
            else -> PreparedScore.Malformed
        }

        override fun unfilledCellCount(scoreJson: String): Int? = when (scoreJson) {
            "partial" -> 1
            "full" -> 0
            else -> null
        }
    }

    private inner class Sheets : UserSheetRepository {
        val sheets = mutableMapOf(701L to sheet(701, OWNER), 702L to sheet(702, OTHER))
        val scoreUpdates = mutableListOf<SheetScoreUpdate>()
        val draftUpdates = mutableListOf<SheetDraftUpdate>()
        var raceOnUpdate = false
        var deleteAfterUpdate = false

        override fun findJangdan(jangdanId: Long) = when (jangdanId) {
            1L -> SheetJangdan(1, "DOTTED_QUARTER", "굿거리")
            3L -> SheetJangdan(3, "JANGDAN_BEAT", "세마치")
            else -> null
        }
        override fun find(sheetId: Long, userId: Long) = sheets[sheetId]?.takeIf { it.userId == userId }
        override fun page(userId: Long, kind: String?, page: Int, size: Int) = SheetPage(emptyList(), 0)

        override fun updateScore(update: SheetScoreUpdate): Boolean {
            val current = find(update.sheetId, update.userId)
            if (raceOnUpdate || current == null || current.scoreVersion != update.baseScoreVersion) return false
            scoreUpdates += update
            sheets[update.sheetId] = current.copy(
                title = update.title, status = SheetStatus.EVALUABLE, jangdanId = update.jangdanId, bpm = update.bpm, bpmUnit = update.bpmUnit,
                columnCount = update.columnCount, scoreVersion = current.scoreVersion + 1, scoreJson = update.scoreJson,
                noteCount = update.noteCount, updatedAt = update.updatedAt, draft = null, draftRevision = 0,
            )
            if (deleteAfterUpdate) sheets.remove(update.sheetId)
            return true
        }

        override fun updateDraft(update: SheetDraftUpdate): Boolean {
            val current = find(update.sheetId, update.userId)
            if (current == null || current.scoreVersion != update.baseScoreVersion || current.draftRevision != update.draftRevision) return false
            draftUpdates += update
            sheets[update.sheetId] = current.copy(
                draft = SheetDraft(update.draftJson, update.baseScoreVersion, update.savedAt), draftRevision = update.draftRevision + 1,
            )
            return true
        }

        override fun delete(sheetId: Long, userId: Long) = find(sheetId, userId) != null && sheets.remove(sheetId) != null
    }

    private fun sheet(id: Long, userId: Long) = UserSheet(
        id, userId, "새 악보 1", "JEONGGANBO", "MANUAL", SheetStatus.NEEDS_REVIEW, 3, "세마치", 60, "JANGDAN_BEAT", 8, 1, null, 0, 0,
        Instant.parse("2026-11-02T05:00:00Z"),
    )
}
