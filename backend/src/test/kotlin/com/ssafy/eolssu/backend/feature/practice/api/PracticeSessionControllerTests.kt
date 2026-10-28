package com.ssafy.eolssu.backend.feature.practice.api

import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSession
import com.ssafy.eolssu.backend.feature.practice.application.GetPracticeSession
import com.ssafy.eolssu.backend.feature.practice.application.MemoryPracticeSessionRepository
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetails
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PracticeSessionControllerTests {
    private val detail = PracticeSessionDetail(
        sessionId = 501, userId = 1, songId = 101, bpm = 96, bpmUnit = "DOTTED_QUARTER", startNoteIdx = 0, endNoteIdx = 71,
        status = "DONE", evaluationId = 701, failReason = null, recordingAvailable = true,
        startedAt = LocalDateTime.parse("2026-10-22T03:00:00"), endedAt = LocalDateTime.parse("2026-10-22T03:00:48"),
        analysisStartedAt = LocalDateTime.parse("2026-10-22T03:00:49"), savedAt = null, memo = null,
    )
    private val details = PracticeSessionDetails { sessionId, userId -> detail.takeIf { it.sessionId == sessionId && it.userId == userId } }
    private val mockMvc = MockMvcBuilders.standaloneSetup(
        PracticeSessionController(
            CreatePracticeSession(MemoryPracticeSessionRepository(), Clock.systemUTC()),
            GetPracticeSession(details, { _, _ -> false }, Clock.fixed(Instant.parse("2026-10-22T03:01:00Z"), ZoneOffset.UTC)),
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    // 단독 MockMvc에는 보안 필터가 없어 검증을 마친 토큰을 직접 넣는다. 서명·쿠키 검증은 AccessTokenSecurityTests가 맡는다.
    private fun signIn(userId: String) {
        val token = Jwt.withTokenValue("test").header("alg", "HS256").subject(userId).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(token)
    }

    @Test
    fun `answers the owner's session in the API spec shape`() {
        signIn("1")
        mockMvc.perform(get("/api/practice-sessions/501"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.result.sessionId").value("501"))
            .andExpect(jsonPath("$.result.songId").value("101"))
            .andExpect(jsonPath("$.result.bpm").value(96))
            .andExpect(jsonPath("$.result.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.startNoteIdx").value(0))
            .andExpect(jsonPath("$.result.endNoteIdx").value(71))
            .andExpect(jsonPath("$.result.status").value("DONE"))
            .andExpect(jsonPath("$.result.evaluationId").value("701"))
            .andExpect(jsonPath("$.result.failReason").isEmpty)
            .andExpect(jsonPath("$.result.recordingAvailable").value(true))
            .andExpect(jsonPath("$.result.startedAt").value("2026-10-22T03:00:00Z"))
            .andExpect(jsonPath("$.result.endedAt").value("2026-10-22T03:00:48Z"))
            .andExpect(jsonPath("$.result.retryable").value(false))
            .andExpect(jsonPath("$.result.savedAt").isEmpty)
            .andExpect(jsonPath("$.result.memo").isEmpty)
    }

    @Test
    fun `answers 404 in the common error shape for another member's or a missing session`() {
        for (path in listOf("/api/practice-sessions/501" to "2", "/api/practice-sessions/999" to "1")) {
            signIn(path.second)
            mockMvc.perform(get(path.first))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        }
    }
}
