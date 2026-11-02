package com.ssafy.eolssu.backend.feature.record.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.record.UnusedPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.DeletePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.GetPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PracticeRecordControllerTests {
    private val session = RecordSession(
        sessionId = 501, songId = 101, songTitle = "아리랑", noteCount = 6, scoreVersion = 2, startNoteIdx = 2, endNoteIdx = 4,
        bpm = 96, bpmUnit = "DOTTED_QUARTER", status = "DONE", startedAt = LocalDateTime.parse("2026-11-02T03:58:00"),
        recordingDurationMs = 48000, totalScore = 84, savedAt = null, memo = null,
    )
    private var sessionStatus = "DONE"
    private val memos = mutableListOf<MemoChange>()
    private val records = object : UnusedPracticeRecords() {
        override fun lockStatus(sessionId: Long, userId: Long) = sessionStatus.takeIf { sessionId == 501L && userId == 1L }

        override fun findSession(sessionId: Long, userId: Long) =
            session.copy(savedAt = LocalDateTime.parse("2026-11-02T04:00:00"), memo = (memos.lastOrNull() as? MemoChange.Replace)?.memo)

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) {
            memos += memo
        }
    }
    private val columns = ScoreColumns { _, _ -> listOf(0, 0, 1, 1, 2, 2) }
    private val mvc = MockMvcBuilders.standaloneSetup(
        PracticeRecordController(
            SavePracticeRecord(records, columns, Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneOffset.UTC)),
            GetPracticeRecords(records, columns),
            DeletePracticeRecord(records, { error("not used") }, Clock.systemUTC()),
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `saves without a body and answers the saved record`() {
        mvc.perform(put("/api/practice-sessions/501/record")).andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.result.sessionId").value("501"))
            .andExpect(jsonPath("$.result.savedAt").value("2026-11-02T04:00:00Z"))
            .andExpect(jsonPath("$.result.memo").isEmpty)
            .andExpect(jsonPath("$.result.songId").value("101"))
            .andExpect(jsonPath("$.result.songTitle").value("아리랑"))
            .andExpect(jsonPath("$.result.startColumnIdx").value(1))
            .andExpect(jsonPath("$.result.endColumnIdx").value(2))
            .andExpect(jsonPath("$.result.isFullSong").value(false))
            .andExpect(jsonPath("$.result.startedAt").value("2026-11-02T03:58:00Z"))
            .andExpect(jsonPath("$.result.recordingDurationMs").value(48000))
            .andExpect(jsonPath("$.result.bpm").value(96))
            .andExpect(jsonPath("$.result.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.totalScore").value(84))
        assertEquals(listOf<MemoChange>(MemoChange.Keep), memos)
    }

    @Test
    fun `reads the memo field as keep, clear or trimmed text`() {
        for ((body, expected) in listOf(
            "{}" to MemoChange.Keep,
            """{"memo":null}""" to MemoChange.Clear,
            """{"memo":"  박자가 밀림 "}""" to MemoChange.Replace("박자가 밀림"),
            """{"memo":"   "}""" to MemoChange.Clear,
        )) {
            memos.clear()
            mvc.perform(put("/api/practice-sessions/501/record").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk)
            assertEquals(listOf(expected), memos, body)
        }
        mvc.perform(put("/api/practice-sessions/501/record").contentType(MediaType.APPLICATION_JSON).content("""{"memo":"박자"}"""))
            .andExpect(jsonPath("$.result.memo").value("박자"))
    }

    @Test
    fun `rejects bodies that are not a memo object without saving`() {
        for (body in listOf("""{"memo":3}""", """{"memo":["a"]}""", "[]", "\"메모\"", "{", """{"memo":"${"가".repeat(501)}"}""")) {
            mvc.perform(put("/api/practice-sessions/501/record").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(emptyList<MemoChange>(), memos)
    }

    @Test
    fun `hides missing sessions and refuses unfinished ones`() {
        mvc.perform(put("/api/practice-sessions/999/record")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        sessionStatus = "PENDING"
        mvc.perform(put("/api/practice-sessions/501/record")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.result.errorCode").value("STATE_CONFLICT"))
        assertEquals(emptyList<MemoChange>(), memos)
    }
}
