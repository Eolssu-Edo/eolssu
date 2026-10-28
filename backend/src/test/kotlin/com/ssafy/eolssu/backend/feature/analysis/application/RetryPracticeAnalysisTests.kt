package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RetryPracticeAnalysisTests {
    private val now = Instant.parse("2026-10-28T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC)

    /** DB와 같은 규칙: 본인 세션이고 `FAILED`·`TIMEOUT`/`SERVER_ERROR`일 때만 새 시도로 바뀐다. */
    private class FakeRepository : AnalysisRetryRepository {
        var owner = 1L
        var state = AnalysisState("FAILED", "TIMEOUT", 2)
        var startedAt: LocalDateTime? = null
        var beforeStart: () -> Unit = {}

        override fun findState(sessionId: Long, userId: Long) = state.takeIf { sessionId == 7L && userId == owner }

        override fun startRetry(sessionId: Long, userId: Long, startedAt: LocalDateTime): Boolean {
            beforeStart()
            if (findState(sessionId, userId) == null || state.status != "FAILED" || state.failReason !in setOf("TIMEOUT", "SERVER_ERROR")) return false
            state = AnalysisState("PENDING", null, state.attempt + 1)
            this.startedAt = startedAt
            return true
        }
    }

    private val repository = FakeRepository()
    private val launched = mutableListOf<Long>()
    private val expired = mutableListOf<Long>()
    private var overdue = false

    private val service = RetryPracticeAnalysis(
        repository,
        expireIfOverdue = { sessionId ->
            expired += sessionId
            if (overdue && repository.state.status == "PENDING") {
                repository.state = repository.state.copy(status = "FAILED", failReason = "TIMEOUT")
                true
            } else {
                false
            }
        },
        startAnalysis = { launched += it },
        clock = clock,
    )

    @Test
    fun `starts a new attempt for a TIMEOUT session and runs the analysis once`() {
        assertEquals(RetryOutcome.Started(3), service.retry(userId = 1, sessionId = 7))
        assertEquals(AnalysisState("PENDING", null, 3), repository.state)
        assertEquals(nowUtc, repository.startedAt)
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `starts a new attempt for a SERVER_ERROR session`() {
        repository.state = AnalysisState("FAILED", "SERVER_ERROR", 1)

        assertEquals(RetryOutcome.Started(2), service.retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `returns the current attempt without starting another while still pending`() {
        repository.state = AnalysisState("PENDING", null, 2)

        assertEquals(RetryOutcome.AlreadyPending(2), service.retry(userId = 1, sessionId = 7))
        assertEquals(AnalysisState("PENDING", null, 2), repository.state)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `expires an overdue pending attempt and then retries it`() {
        repository.state = AnalysisState("PENDING", null, 2)
        overdue = true

        assertEquals(RetryOutcome.Started(3), service.retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), expired)
        assertEquals(listOf(7L), launched)
    }

    @Test
    fun `refuses sessions whose failure needs a new recording or that already finished`() {
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

            assertEquals(RetryOutcome.NotRetryable(it), service.retry(userId = 1, sessionId = 7), it.toString())
            assertEquals(it, repository.state)
        }
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `does not reveal another member's session`() {
        assertEquals(RetryOutcome.NotFound, service.retry(userId = 2, sessionId = 7))
        assertEquals(RetryOutcome.NotFound, service.retry(userId = 1, sessionId = 8))
        assertEquals(AnalysisState("FAILED", "TIMEOUT", 2), repository.state)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `reports the attempt another request started first instead of starting a second one`() {
        repository.beforeStart = {
            repository.beforeStart = {}
            repository.state = AnalysisState("PENDING", null, 3)
        }

        assertEquals(RetryOutcome.AlreadyPending(3), service.retry(userId = 1, sessionId = 7))
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `uses the deadline clock for the new start time`() {
        val later = RetryPracticeAnalysis(repository, { false }, { launched += it }, Clock.offset(clock, Duration.ofMinutes(3)))

        later.retry(userId = 1, sessionId = 7)

        assertEquals(nowUtc.plusMinutes(3), repository.startedAt)
    }
}
