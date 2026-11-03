package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.sheet.application.CopiedScore
import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.EditUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.NewUserSheet
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

/** SHT-04·11·02와 SHT-08 `events`·`draft`, SHT-01 `hasDraft`의 HTTP 모양. */
class UserSheetEditControllerTests {
    private val now = Instant.parse("2026-11-03T03:10:00Z")
    private val sheets = mutableMapOf<Long, UserSheet>()
    private val repository = object : UserSheetRepository {
        override fun findByIdempotencyKey(userId: Long, idempotencyKey: String): UserSheet? = null
        override fun findJangdan(jangdanId: Long) = SheetJangdan(3, "JANGDAN_BEAT", "세마치").takeIf { jangdanId == 3L }
        override fun findSongSource(songId: Long) = null
        override fun find(sheetId: Long, userId: Long) = sheets[sheetId]?.takeIf { it.userId == userId }
        override fun insert(sheet: NewUserSheet): Long? = error("not used")
        override fun page(userId: Long, kind: String?, page: Int, size: Int) = SheetPage(sheets.values.filter { it.userId == userId }, 1)

        override fun updateScore(update: SheetScoreUpdate): Boolean {
            val current = find(update.sheetId, update.userId)?.takeIf { it.scoreVersion == update.baseScoreVersion } ?: return false
            sheets[update.sheetId] = current.copy(
                status = SheetStatus.EVALUABLE, scoreVersion = current.scoreVersion + 1, scoreJson = update.scoreJson,
                noteCount = update.noteCount, columnCount = update.columnCount, updatedAt = update.updatedAt, draft = null, draftRevision = 0,
            )
            return true
        }

        override fun updateDraft(update: SheetDraftUpdate): Boolean {
            val current = find(update.sheetId, update.userId)
                ?.takeIf { it.scoreVersion == update.baseScoreVersion && it.draftRevision == update.draftRevision } ?: return false
            sheets[update.sheetId] = current.copy(draft = SheetDraft(update.draftJson, update.baseScoreVersion, update.savedAt), draftRevision = update.draftRevision + 1)
            return true
        }

        override fun delete(sheetId: Long, userId: Long) = find(sheetId, userId) != null && sheets.remove(sheetId) != null
    }

    // 악보 문서의 내용은 응용 테스트가 다룬다. 여기서는 표식 문자열로 결과만 고른다.
    private val scores = object : SheetScores {
        override fun copy(scoreJson: String, scoreVersion: Int): CopiedScore? = null
        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) =
            if ("broken" in scoreJson) null else listOf(ScoreTimelineEvent("c0-j0-r0-u0", "NOTE", 0, "1", 0, 0, 0, 0, 480, 0.0, 1.0))
        override fun prepare(scoreJson: String, scoreVersion: Int) = when {
            "\"units\":[]" in scoreJson -> PreparedScore.Unfilled(listOf("c5-j4"))
            "\"schemaVersion\":1" in scoreJson -> PreparedScore.Ready("""{"schemaVersion":1,"v":$scoreVersion}""", 16, 84, "세마치", "JANGDAN_BEAT")
            else -> PreparedScore.Malformed
        }
        override fun unfilledCellCount(scoreJson: String) = if ("\"schemaVersion\":1" in scoreJson) 1 else null
    }
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val mvc = MockMvcBuilders.standaloneSetup(
        UserSheetController(
            CreateUserSheet(repository, scores, clock),
            GetUserSheets(repository, scores),
            EditUserSheet(repository, scores, clock),
            JsonMapper.builder().build(),
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    private fun sheet(id: Long, userId: Long = 1, json: String? = null) = UserSheet(
        id, userId, "새 악보 1", "JEONGGANBO", "MANUAL", SheetStatus.NEEDS_REVIEW, 3, "세마치", 60, "JANGDAN_BEAT", 8, 1, json, 0, 0, "h",
        Instant.parse("2026-11-02T05:00:00Z"),
    )

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
        sheets[701] = sheet(701)
        sheets[702] = sheet(702, userId = 2)
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private val score = """{"schemaVersion":1,"score":{},"music":{},"columns":[],"events":[],"provenance":{}}"""

    private fun saveBody(base: String = "\"1\"", score: String = this.score, extra: String = "") =
        """{"baseScoreVersion":$base,"jangdanId":"3","bpm":60,"score":$score$extra}"""

    @Test
    fun `saving the score answers the new version, status and timeline`() {
        mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON).content(saveBody(extra = ""","resolvedIssueIds":[]""")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("정간보 편집 저장 성공"))
            .andExpect(jsonPath("$.result.sheetId").value("701"))
            .andExpect(jsonPath("$.result.scoreVersion").value("2"))
            .andExpect(jsonPath("$.result.reviewStatus").value("EVALUABLE"))
            .andExpect(jsonPath("$.result.noteCount").value(84))
            .andExpect(jsonPath("$.result.columnCount").value(16))
            .andExpect(jsonPath("$.result.pendingIssueCount").value(0))
            .andExpect(jsonPath("$.result.events[0].eventId").value("c0-j0-r0-u0"))
            .andExpect(jsonPath("$.result.updatedAt").value("2026-11-03T03:10:00Z"))
    }

    @Test
    fun `an explicit null for the optional resolvedIssueIds is the same as leaving it out`() {
        mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON).content(saveBody(extra = ""","resolvedIssueIds":null""")))
            .andExpect(status().isOk)
    }

    @Test
    fun `save failures map to the spec error codes`() {
        mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON)
            .content(saveBody(score = """{"schemaVersion":1,"columns":[{"cells":[{"id":"c5-j4","units":[]}]}]}""")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result.errorCode").value("UNFILLED_CELLS"))
            .andExpect(jsonPath("$.result.details.cellIds[0]").value("c5-j4"))
        mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON).content(saveBody(base = "\"2\"")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result.errorCode").value("STATE_CONFLICT"))
        mvc.perform(put("/api/users/me/sheets/702/score").contentType(MediaType.APPLICATION_JSON).content(saveBody()))
            .andExpect(status().isNotFound)
        val tooLarge = """{"schemaVersion":1,"pad":"${"x".repeat(EditUserSheet.MAX_SCORE_BYTES)}"}"""
        mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON).content(saveBody(score = tooLarge)))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.result.errorCode").value("PAYLOAD_TOO_LARGE"))
        assertEquals(1, sheets.getValue(701).scoreVersion)
    }

    @Test
    fun `save rejects wrong JSON types and shapes with 400`() {
        val invalid = listOf(
            saveBody(base = "1"),
            saveBody(base = "\"0\""),
            saveBody(score = "[]"),
            saveBody(score = "\"text\""),
            saveBody(extra = ""","resolvedIssueIds":"i1""""),
            saveBody(extra = ""","resolvedIssueIds":[1]"""),
            saveBody(extra = ""","resolvedIssueIds":["i1"]"""),
            saveBody().replace("\"bpm\":60", "\"bpm\":\"60\""),
            saveBody().replace("\"jangdanId\":\"3\"", "\"jangdanId\":3"),
            """{"baseScoreVersion":"1","jangdanId":"3","bpm":60}""",
        )
        invalid.forEach { body ->
            mvc.perform(put("/api/users/me/sheets/701/score").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(1, sheets.getValue(701).scoreVersion)
    }

    @Test
    fun `autosave answers the next revision and the sheet then returns the draft`() {
        val body = """{"baseScoreVersion":"1","draftRevision":0,"title":"고치는 중","jangdanId":"3","bpm":66,"score":$score}"""
        mvc.perform(put("/api/users/me/sheets/701/score/draft").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("악보 초안 저장 성공"))
            .andExpect(jsonPath("$.result.sheetId").value("701"))
            .andExpect(jsonPath("$.result.draftRevision").value(1))
            .andExpect(jsonPath("$.result.unfilledCellCount").value(1))
            .andExpect(jsonPath("$.result.savedAt").value("2026-11-03T03:10:00Z"))
        mvc.perform(put("/api/users/me/sheets/701/score/draft").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)

        mvc.perform(get("/api/users/me/sheets/701/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.scoreVersion").value("1"))
            .andExpect(jsonPath("$.result.events").isEmpty)
            .andExpect(jsonPath("$.result.draft.title").value("고치는 중"))
            .andExpect(jsonPath("$.result.draft.jangdanId").value("3"))
            .andExpect(jsonPath("$.result.draft.bpm").value(66))
            .andExpect(jsonPath("$.result.draft.score.schemaVersion").value(1))
            .andExpect(jsonPath("$.result.draft.baseScoreVersion").value("1"))
            .andExpect(jsonPath("$.result.draft.draftRevision").value(1))
            .andExpect(jsonPath("$.result.draft.savedAt").value("2026-11-03T03:10:00Z"))
        mvc.perform(get("/api/users/me/sheets")).andExpect(jsonPath("$.result.items[0].hasDraft").value(true))
    }

    @Test
    fun `the sheet answers its timeline, no draft, and 409 when the stored score is broken`() {
        sheets[703] = sheet(703, json = """{"schemaVersion":1}""")
        sheets[704] = sheet(704, json = """{"schemaVersion":1,"broken":true}""")

        mvc.perform(get("/api/users/me/sheets/703/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.events[0].noteIdx").value(0))
            .andExpect(jsonPath("$.result.draft").doesNotExist())
        mvc.perform(get("/api/users/me/sheets/704/score")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.result.errorCode").value("STATE_CONFLICT"))
        mvc.perform(get("/api/users/me/sheets")).andExpect(jsonPath("$.result.items[0].hasDraft").value(false))
    }

    @Test
    fun `delete answers 204 for the owner and 404 otherwise`() {
        mvc.perform(delete("/api/users/me/sheets/701")).andExpect(status().isNoContent).andExpect(content().string(""))
        mvc.perform(delete("/api/users/me/sheets/701")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        mvc.perform(delete("/api/users/me/sheets/702")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/users/me/sheets/07")).andExpect(status().isBadRequest)
        assertEquals(setOf(702L), sheets.keys)
    }
}
