package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisClient
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResponseException
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.client.ResourceAccessException

class AnalyzePracticeSessionTests {
    private val startedAt = Instant.parse("2026-10-28T01:00:00Z")
    private val pending = PendingAnalysis(7, 2, 3, 1, 4, 5, 60, "JEONGGAN", "recordings/7/a.m4a")
    private val notes = listOf(ScoreNote(4, 0, 1.0, false, 11), ScoreNote(5, 2, 2.0, true, 12))
    private val done = AnalysisResult.Done(
        "pyin-1", 87, 90, 85, 86,
        listOf(NoteJudgement(4, NoteVerdict.CORRECT, 3.1, 20, -15), NoteJudgement(5, NoteVerdict.MISSED, null, null, null)),
    )
    private val clock = MutableClock(startedAt.plusSeconds(5))

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    /** DB와 같은 규칙: 같은 시도의 `PENDING`이고 시작 시각이 마감 기준 이상일 때만 바뀐다. */
    private inner class FakeRepository : PracticeAnalysisRepository {
        var status = "PENDING"
        var attempt = 2
        var failReason: String? = null
        var saveDoneFails = false
        val stored = mutableListOf<Any>()
        private val started = LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC)

        private fun open(analysis: PendingAnalysis, startedAfter: LocalDateTime) =
            status == "PENDING" && attempt == analysis.attempt && !started.isBefore(startedAfter)

        override fun findPending(sessionId: Long, startedAfter: LocalDateTime) =
            pending.copy(attempt = attempt).takeIf { sessionId == 7L && status == "PENDING" && !started.isBefore(startedAfter) }

        override fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>, startedAfter: LocalDateTime): Boolean {
            if (saveDoneFails) throw DataIntegrityViolationException("pitch_error_cent out of range")
            if (!open(analysis, startedAfter)) return false
            status = "DONE"
            stored += result
            return true
        }

        override fun saveFailed(analysis: PendingAnalysis, failReason: String, startedAfter: LocalDateTime): Boolean {
            if (!open(analysis, startedAfter)) return false
            status = "FAILED"
            this.failReason = failReason
            return true
        }

        override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime): Boolean {
            if (status != "PENDING" || !started.isBefore(startedAfter)) return false
            status = "FAILED"
            failReason = "TIMEOUT"
            return true
        }
    }

    private val repository = FakeRepository()
    private val requests = mutableListOf<AnalysisRequest>()

    private fun service(client: AnalysisClient, scoreNotes: (PendingAnalysis) -> List<ScoreNote> = { notes }) =
        AnalyzePracticeSession(
            repository,
            { requests += it; client.analyze(it) },
            scoreNotes,
            { "https://storage.example/${it.recordingUrl}?sig=x" },
            clock,
            Duration.ofSeconds(60),
        )

    @Test
    fun `sends the session attempt range notes and read url to the AI and stores a DONE result`() {
        assertEquals(AnalysisOutcome.Saved(done), service({ done }).analyze(7))
        assertEquals(
            listOf(AnalysisRequest(7, 2, "https://storage.example/recordings/7/a.m4a?sig=x", 60, "JEONGGAN", notes)),
            requests,
        )
        assertEquals("DONE", repository.status)
        assertEquals(listOf<Any>(done), repository.stored)
    }

    @Test
    fun `stores the failure reason the AI returns`() {
        val failed = AnalysisResult.Failed(AnalysisFailReason.NO_SOUND)

        assertEquals(AnalysisOutcome.Saved(failed), service({ failed }).analyze(7))
        assertEquals("FAILED" to "NO_SOUND", repository.status to repository.failReason)
    }

    @Test
    fun `does not call the AI when the session is not waiting for analysis`() {
        repository.status = "DONE"

        assertEquals(AnalysisOutcome.NotPending, service({ done }).analyze(7))
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `discards a result when the session moved on to another attempt`() {
        val outcome = service({ repository.attempt = 3; done }).analyze(7)

        assertEquals(AnalysisOutcome.Discarded, outcome)
        assertEquals("PENDING", repository.status)
        assertEquals(emptyList(), repository.stored)
    }

    @Test
    fun `records SERVER_ERROR when the AI cannot be reached`() {
        val outcome = service({ throw ResourceAccessException("connection refused") }).analyze(7)

        assertEquals(AnalysisOutcome.ServerError, outcome)
        assertEquals("FAILED" to "SERVER_ERROR", repository.status to repository.failReason)
    }

    @Test
    fun `records SERVER_ERROR when the AI response breaks the contract`() {
        service({ throw AnalysisResponseException("unknown status RUNNING") }).analyze(7)

        assertEquals("FAILED" to "SERVER_ERROR", repository.status to repository.failReason)
    }

    @Test
    fun `records SERVER_ERROR without calling the AI when the score cannot be read`() {
        val outcome = service({ done }, scoreNotes = { throw IllegalStateException("broken score_json") }).analyze(7)

        assertEquals(AnalysisOutcome.ServerError, outcome)
        assertEquals(emptyList(), requests)
        assertEquals("FAILED" to "SERVER_ERROR", repository.status to repository.failReason)
    }

    @Test
    fun `records SERVER_ERROR when storing the result fails`() {
        repository.saveDoneFails = true

        assertEquals(AnalysisOutcome.ServerError, service({ done }).analyze(7))
        assertEquals("FAILED" to "SERVER_ERROR", repository.status to repository.failReason)
    }

    @Test
    fun `turns a result that arrives after the time limit into TIMEOUT`() {
        val outcome = service({ clock.now = startedAt.plusSeconds(61); done }).analyze(7)

        assertEquals(AnalysisOutcome.TimedOut, outcome)
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
        assertEquals(emptyList(), repository.stored)
    }

    @Test
    fun `turns a failure that happens after the time limit into TIMEOUT instead of SERVER_ERROR`() {
        val outcome = service({ clock.now = startedAt.plusSeconds(61); throw ResourceAccessException("read timed out") }).analyze(7)

        assertEquals(AnalysisOutcome.TimedOut, outcome)
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
    }

    @Test
    fun `turns a storage failure after the time limit into TIMEOUT`() {
        repository.saveDoneFails = true

        assertEquals(AnalysisOutcome.TimedOut, service({ clock.now = startedAt.plusSeconds(61); done }).analyze(7))
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
    }

    @Test
    fun `discards a late result after the status lookup already expired the attempt`() {
        lateinit var service: AnalyzePracticeSession
        service = service({
            clock.now = startedAt.plusSeconds(61)
            assertTrue(service.expireIfOverdue(7))
            done
        })

        assertEquals(AnalysisOutcome.Discarded, service.analyze(7))
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
        assertEquals(emptyList(), repository.stored)
    }

    @Test
    fun `keeps a result that arrives exactly at the time limit`() {
        assertEquals(AnalysisOutcome.Saved(done), service({ clock.now = startedAt.plusSeconds(60); done }).analyze(7))
    }

    @Test
    fun `expires an attempt that is already past the limit without calling the AI`() {
        clock.now = startedAt.plusSeconds(61)

        assertEquals(AnalysisOutcome.TimedOut, service({ done }).analyze(7))
        assertEquals(emptyList(), requests)
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
    }

    @Test
    fun `expires an overdue pending attempt on status lookup only after the limit`() {
        val service = service({ done })

        clock.now = startedAt.plusSeconds(60)
        assertFalse(service.expireIfOverdue(7))
        assertEquals("PENDING", repository.status)

        clock.now = startedAt.plusSeconds(61)
        assertTrue(service.expireIfOverdue(7))
        assertEquals("FAILED" to "TIMEOUT", repository.status to repository.failReason)
    }

    @Test
    fun `hides the read url when the request is logged`() {
        val request = AnalysisRequest(7, 2, "https://storage.example/a.m4a?sig=secret", 60, "JEONGGAN", notes)
        assertEquals(false, request.toString().contains("secret"))
    }
}
