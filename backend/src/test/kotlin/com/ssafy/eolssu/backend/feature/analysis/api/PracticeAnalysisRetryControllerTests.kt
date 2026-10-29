package com.ssafy.eolssu.backend.feature.analysis.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.analysis.application.RetryPracticeAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** PRA-03 분석 다시 시도 API(노션 명세 `POST /practice-sessions/{sessionId}/retry`, S15P31E102-222). */
class PracticeAnalysisRetryControllerTests {
    /** 회원 1의 세션 501. [startable]이 false면 다른 시도가 계속 끼어든 것처럼 새 시도를 시작하지 못한다. */
    private class Sessions(var state: AnalysisState, var startable: Boolean = true) : AnalysisRetryRepository {
        val failedStarts = mutableListOf<Int>()
        override fun findState(sessionId: Long, userId: Long) = state.takeIf { sessionId == 501L && userId == 1L }
        override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime): Boolean {
            if (!startable) return false
            state = AnalysisState("PENDING", null, attempt + 1)
            return true
        }
        override fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime): Boolean {
            failedStarts += attempt
            state = AnalysisState("FAILED", "SERVER_ERROR", attempt)
            return true
        }
    }

    private val started = mutableListOf<Long>()

    private fun retry(sessions: Sessions, startAnalysis: (Long) -> Unit = { started += it }, path: String = "/api/practice-sessions/501/retry"): ResultActions {
        val useCase = RetryPracticeAnalysis(sessions, { false }, startAnalysis, Clock.fixed(Instant.parse("2026-10-29T01:00:00Z"), ZoneOffset.UTC))
        val mockMvc = MockMvcBuilders.standaloneSetup(PracticeAnalysisRetryController(useCase))
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()
        return mockMvc.perform(post(path).header("Idempotency-Key", "retry-1"))
    }

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    // 단독 MockMvc에는 보안 필터가 없어 검증을 마친 토큰을 직접 넣는다.
    private fun signIn(userId: String) {
        val token = Jwt.withTokenValue("test").header("alg", "HS256").subject(userId).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(token)
    }

    private fun ResultActions.acceptedPending() = andExpect(status().isAccepted)
        .andExpect(jsonPath("$.httpStatus").value("ACCEPTED"))
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(202))
        .andExpect(jsonPath("$.result.sessionId").value("501"))
        .andExpect(jsonPath("$.result.status").value("PENDING"))
        .andExpect(jsonPath("$.result.retryable").value(false))

    private fun ResultActions.error(httpStatus: Int, errorCode: String) = andExpect(status().`is`(httpStatus))
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(httpStatus))
        .andExpect(jsonPath("$.result.errorCode").value(errorCode))

    @Test
    fun `starts a new attempt of a timed out analysis and answers 202 pending`() {
        signIn("1")
        retry(Sessions(AnalysisState("FAILED", "TIMEOUT", 1))).acceptedPending()
        assertEquals(listOf(501L), started)
    }

    @Test
    fun `answers an already pending session the same way without a new attempt`() {
        signIn("1")
        retry(Sessions(AnalysisState("PENDING", null, 2))).acceptedPending()
        assertEquals(emptyList(), started)
    }

    @Test
    fun `rejects sessions that need a new recording, are done or not uploaded with 409`() {
        signIn("1")
        for (state in listOf(
            AnalysisState("FAILED", "NO_SOUND", 1), AnalysisState("FAILED", "TOO_SHORT", 1), AnalysisState("FAILED", "DECODE_ERROR", 1),
            AnalysisState("FAILED", "UPLOAD_TIMEOUT", 0), AnalysisState("DONE", null, 1), AnalysisState("RECORDING", null, 0),
        )) {
            retry(Sessions(state)).error(409, "STATE_CONFLICT")
        }
        assertEquals(emptyList(), started)
    }

    @Test
    fun `answers 404 for another member's or a missing session`() {
        signIn("2")
        retry(Sessions(AnalysisState("FAILED", "TIMEOUT", 1))).error(404, "RESOURCE_NOT_FOUND")
        signIn("1")
        retry(Sessions(AnalysisState("FAILED", "TIMEOUT", 1)), path = "/api/practice-sessions/999/retry").error(404, "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `tells the app to try again later with 503 when other attempts keep getting in`() {
        signIn("1")
        retry(Sessions(AnalysisState("FAILED", "SERVER_ERROR", 1), startable = false)).error(503, "SERVICE_UNAVAILABLE")
    }

    @Test
    fun `ends the attempt and answers 503 when the analysis cannot be handed over`() {
        signIn("1")
        val sessions = Sessions(AnalysisState("FAILED", "TIMEOUT", 1))
        retry(sessions, startAnalysis = { throw RejectedExecutionException("full") }).error(503, "SERVICE_UNAVAILABLE")
        assertEquals(listOf(2), sessions.failedStarts)
    }

    @Test
    fun `an unexpected failure stays a server error instead of try again later`() {
        signIn("1")
        retry(Sessions(AnalysisState("FAILED", "TIMEOUT", 1)), startAnalysis = { throw IllegalStateException("bug") })
            .error(500, "INTERNAL_SERVER_ERROR")
    }

    @Test
    fun `requires a printable Idempotency-Key of at most 100 characters`() {
        signIn("1")
        val useCase = RetryPracticeAnalysis(Sessions(AnalysisState("FAILED", "TIMEOUT", 1)), { false }, { started += it }, Clock.systemUTC())
        val mockMvc = MockMvcBuilders.standaloneSetup(PracticeAnalysisRetryController(useCase))
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()
        mockMvc.perform(post("/api/practice-sessions/501/retry")).error(400, "INVALID_REQUEST")
        for (key in listOf("", "has space", "k".repeat(101))) {
            mockMvc.perform(post("/api/practice-sessions/501/retry").header("Idempotency-Key", key)).error(400, "INVALID_REQUEST")
        }
        assertEquals(emptyList(), started)
    }

    @Test
    fun `rejects a session id that is not a positive number with 400`() {
        signIn("1")
        for (id in listOf("0", "-1", "abc")) {
            retry(Sessions(AnalysisState("FAILED", "TIMEOUT", 1)), path = "/api/practice-sessions/$id/retry").error(400, "INVALID_REQUEST")
        }
        assertEquals(emptyList(), started)
    }
}
