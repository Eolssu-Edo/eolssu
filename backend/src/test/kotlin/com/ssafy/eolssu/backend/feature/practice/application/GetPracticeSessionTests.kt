package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetails
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetPracticeSessionTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val base = PracticeSessionDetail(
        sessionId = 501, userId = 1, songId = 101, bpm = 96, bpmUnit = "DOTTED_QUARTER", startNoteIdx = 0, endNoteIdx = 71,
        status = "PENDING", evaluationId = null, failReason = null, recordingAvailable = true,
        startedAt = LocalDateTime.parse("2026-10-28T02:58:00"), endedAt = LocalDateTime.parse("2026-10-28T02:58:48"),
        analysisStartedAt = LocalDateTime.parse("2026-10-28T02:59:30"), savedAt = null, memo = null,
    )

    /** DB와 같은 규칙: 마감 기준(startedAfter)보다 먼저 시작한 `PENDING`만 `TIMEOUT`이 된다. */
    private class Store(var detail: PracticeSessionDetail) : PracticeSessionDetails {
        val expired = mutableListOf<Long>()

        override fun findOwned(sessionId: Long, userId: Long) = detail.takeIf { it.sessionId == sessionId && it.userId == userId }

        fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime): Boolean {
            expired += sessionId
            val started = detail.analysisStartedAt ?: return false
            if (detail.status != "PENDING" || !started.isBefore(startedAfter)) return false
            detail = detail.copy(status = "FAILED", failReason = "TIMEOUT")
            return true
        }
    }

    private fun service(store: Store) = GetPracticeSession(store, store::expireOverdue, clock, Duration.ofSeconds(60))

    @Test
    fun `returns the owner's pending session within the time limit unchanged`() {
        val store = Store(base)
        val detail = service(store).execute(userId = 1, sessionId = 501)
        assertEquals("PENDING", detail?.status)
        assertFalse(detail!!.retryable)
        assertEquals(listOf(501L), store.expired)
    }

    @Test
    fun `turns an overdue pending session into TIMEOUT before answering`() {
        val store = Store(base.copy(analysisStartedAt = LocalDateTime.parse("2026-10-28T02:58:59.999999")))
        val detail = service(store).execute(userId = 1, sessionId = 501)!!
        assertEquals("FAILED" to "TIMEOUT", detail.status to detail.failReason)
        assertTrue(detail.retryable)
    }

    @Test
    fun `hides another member's session and never expires it`() {
        val store = Store(base.copy(analysisStartedAt = LocalDateTime.parse("2026-10-28T02:00:00")))
        assertNull(service(store).execute(userId = 2, sessionId = 501))
        assertNull(service(store).execute(userId = 1, sessionId = 999))
        assertEquals(emptyList(), store.expired)
        assertEquals("PENDING", store.detail.status)
    }

    @Test
    fun `does not touch sessions that are not pending`() {
        val store = Store(base.copy(status = "DONE", evaluationId = 701))
        assertEquals("DONE", service(store).execute(userId = 1, sessionId = 501)?.status)
        assertEquals(emptyList(), store.expired)
    }

    @Test
    fun `only timeout and server error failures can be analyzed again`() {
        val retryable = listOf("TIMEOUT", "SERVER_ERROR", "NO_SOUND", "TOO_SHORT", "DECODE_ERROR", "UPLOAD_TIMEOUT")
            .associateWith { base.copy(status = "FAILED", failReason = it).retryable }
        assertEquals(
            mapOf("TIMEOUT" to true, "SERVER_ERROR" to true, "NO_SOUND" to false, "TOO_SHORT" to false, "DECODE_ERROR" to false, "UPLOAD_TIMEOUT" to false),
            retryable,
        )
        assertFalse(base.copy(status = "DONE").retryable)
    }
}
