package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class RetryPracticeAnalysisTests {
    private val now = Instant.parse("2026-10-28T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC)

    /** DB와 같은 규칙: 본인 세션이 읽은 시도 번호 그대로 `FAILED`·`TIMEOUT`/`SERVER_ERROR`일 때만 새 시도로 바뀐다. */
    private class FakeRepository : AnalysisRetryRepository {
        var owner = 1L
        var state = AnalysisState("FAILED", "TIMEOUT", 2)
        var startedAt: LocalDateTime? = null
        var startRetryCalls = 0
        var beforeStart: () -> Unit = {}
        var failStartError: RuntimeException? = null

        override fun findState(sessionId: Long, userId: Long) = state.takeIf { sessionId == 7L && userId == owner }

        override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime, timingId: EvaluationTimingId?): Boolean {
            startRetryCalls++
            beforeStart()
            if (findState(sessionId, userId) == null || state.attempt != attempt) return false
            if (state.status != "FAILED" || state.failReason !in setOf("TIMEOUT", "SERVER_ERROR")) return false
            state = AnalysisState("PENDING", null, attempt + 1)
            this.startedAt = startedAt
            return true
        }

        override fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime): Boolean {
            failStartError?.let { throw it }
            if (state.status != "PENDING" || state.attempt != attempt || this.startedAt != startedAt) return false
            state = AnalysisState("FAILED", "SERVER_ERROR", attempt)
            return true
        }
    }

    private val repository = FakeRepository()
    private val launched = mutableListOf<Long>()
    private val expired = mutableListOf<Long>()

    /** 상태 조회처럼 마감을 넘긴 `PENDING`을 `TIMEOUT`으로 바꾼다. 테스트마다 다른 요청이 먼저 바꾼 경우 등을 흉내 낸다. */
    private var expire: () -> Boolean = { false }

    private fun service(startAnalysis: (Long) -> Unit = { launched += it }, retryClock: Clock = clock) = RetryPracticeAnalysis(
        repository,
        expireIfOverdue = { sessionId -> expired += sessionId; expire() },
        startAnalysis = { sessionId, _, _ -> startAnalysis(sessionId) },
        clock = retryClock,
    )

    private fun timeOut(): Boolean {
        if (repository.state.status != "PENDING") return false
        repository.state = repository.state.copy(status = "FAILED", failReason = "TIMEOUT")
        return true
    }

    @Test
    fun `starts a new attempt for a TIMEOUT session and runs the analysis once`() {
        assertEquals(RetryOutcome.Started(3), service().retry(userId = 1, sessionId = 7))
        assertEquals(AnalysisState("PENDING", null, 3), repository.state)
        assertEquals(nowUtc, repository.startedAt)
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `starts a new attempt for a SERVER_ERROR session`() {
        repository.state = AnalysisState("FAILED", "SERVER_ERROR", 1)

        assertEquals(RetryOutcome.Started(2), service().retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `returns the current attempt without starting another while still pending`() {
        repository.state = AnalysisState("PENDING", null, 2)

        assertEquals(RetryOutcome.AlreadyPending(2), service().retry(userId = 1, sessionId = 7))
        assertEquals(AnalysisState("PENDING", null, 2), repository.state)
        assertEquals(0, repository.startRetryCalls)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `expires an overdue pending attempt and then retries it`() {
        repository.state = AnalysisState("PENDING", null, 2)
        expire = ::timeOut

        assertEquals(RetryOutcome.Started(3), service().retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), expired)
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `retries when another request already expired the pending attempt`() {
        repository.state = AnalysisState("PENDING", null, 2)
        expire = { timeOut(); false } // 상태 조회가 먼저 TIMEOUT으로 바꿔 이 요청의 전환은 0행이다

        assertEquals(RetryOutcome.Started(3), service().retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `refuses sessions whose failure needs a new recording or that already finished without trying to start`() {
        val refused = listOf(
            AnalysisState("FAILED", "NO_SOUND", 1),
            AnalysisState("FAILED", "TOO_SHORT", 1),
            AnalysisState("FAILED", "DECODE_ERROR", 1),
            AnalysisState("FAILED", "UPLOAD_TIMEOUT", 0),
            AnalysisState("DONE", null, 1),
            AnalysisState("RECORDING", null, 0),
        )
        refused.forEach {
            repository.state = it

            assertEquals(RetryOutcome.NotRetryable(it), service().retry(userId = 1, sessionId = 7), it.toString())
            assertEquals(it, repository.state)
        }
        assertEquals(0, repository.startRetryCalls)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `does not reveal another member's session`() {
        assertEquals(RetryOutcome.NotFound, service().retry(userId = 2, sessionId = 7))
        assertEquals(RetryOutcome.NotFound, service().retry(userId = 1, sessionId = 8))
        assertEquals(AnalysisState("FAILED", "TIMEOUT", 2), repository.state)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `reports the attempt another request started first instead of starting a second one`() {
        repository.beforeStart = {
            repository.beforeStart = {}
            repository.state = AnalysisState("PENDING", null, 3)
        }

        assertEquals(RetryOutcome.AlreadyPending(3), service().retry(userId = 1, sessionId = 7))
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `reports the attempt it actually started when another attempt began and failed in between`() {
        repository.beforeStart = {
            repository.beforeStart = {}
            repository.state = AnalysisState("FAILED", "SERVER_ERROR", 3) // 다른 요청의 3번째 시도가 곧바로 실패했다
        }

        assertEquals(RetryOutcome.Started(4), service().retry(userId = 1, sessionId = 7))
        assertEquals(AnalysisState("PENDING", null, 4), repository.state)
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `records SERVER_ERROR for the new attempt when the analysis cannot be started`() {
        val failure = assertFailsWith<RejectedExecutionException> {
            service(startAnalysis = { throw RejectedExecutionException("queue full") }).retry(userId = 1, sessionId = 7)
        }

        assertEquals("queue full", failure.message)
        assertEquals(AnalysisState("FAILED", "SERVER_ERROR", 3), repository.state)
    }

    @Test
    fun `asks to try again later when other attempts keep starting and failing in between`() {
        // 분석을 넘기지 못해 매번 곧바로 SERVER_ERROR로 끝나는 다른 시도가 끼어든다. 다시 시도할 수 있는 상태지만 시작하지 못했다.
        repository.beforeStart = { repository.state = AnalysisState("FAILED", "SERVER_ERROR", repository.state.attempt + 1) }

        assertEquals(RetryOutcome.Busy, service().retry(userId = 1, sessionId = 7))
        assertEquals(3, repository.startRetryCalls)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `records SERVER_ERROR for checked exceptions from starting the analysis too`() {
        assertFailsWith<IOException> { service(startAnalysis = { throw IOException("executor closed") }).retry(userId = 1, sessionId = 7) }

        assertEquals(AnalysisState("FAILED", "SERVER_ERROR", 3), repository.state)
    }

    @Test
    fun `keeps the start failure when recording SERVER_ERROR fails as well`() {
        repository.failStartError = IllegalStateException("database down")

        val failure = assertFailsWith<RejectedExecutionException> {
            service(startAnalysis = { throw RejectedExecutionException("queue full") }).retry(userId = 1, sessionId = 7)
        }

        assertEquals("queue full", failure.message)
        assertEquals(listOf("database down"), failure.suppressed.map { it.message })
    }

    @Test
    fun `uses the deadline clock for the new start time`() {
        service(retryClock = Clock.offset(clock, Duration.ofMinutes(3))).retry(userId = 1, sessionId = 7)

        assertEquals(nowUtc.plusMinutes(3), repository.startedAt)
    }
}
