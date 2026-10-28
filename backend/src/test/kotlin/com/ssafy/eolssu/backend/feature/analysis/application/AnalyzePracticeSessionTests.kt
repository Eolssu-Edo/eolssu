package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AnalyzePracticeSessionTests {
    private val pending = PendingAnalysis(7, 2, 3, 1, 4, 5, 60, "JEONGGAN", "recordings/7/a.m4a")
    private val notes = listOf(ScoreNote(4, 0, 1.0, false, 11), ScoreNote(5, 2, 2.0, true, 12))
    private val done = AnalysisResult.Done(
        "pyin-1", 87, 90, 85, 86,
        listOf(NoteJudgement(4, NoteVerdict.CORRECT, 3.1, 20, -15), NoteJudgement(5, NoteVerdict.MISSED, null, null, null)),
    )

    private class FakeRepository(var pending: PendingAnalysis?, private val accepts: Boolean = true) : PracticeAnalysisRepository {
        val saved = mutableListOf<Any>()

        override fun findPending(sessionId: Long) = pending?.takeIf { it.sessionId == sessionId }

        override fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>): Boolean {
            if (accepts) saved += Triple(analysis, result, notes)
            return accepts
        }

        override fun saveFailed(analysis: PendingAnalysis, reason: AnalysisFailReason): Boolean {
            if (accepts) saved += analysis to reason
            return accepts
        }
    }

    private fun service(repository: FakeRepository, result: AnalysisResult, requests: MutableList<AnalysisRequest> = mutableListOf()) =
        AnalyzePracticeSession(
            repository,
            { requests += it; result },
            { notes },
            { "https://storage.example/${it.recordingUrl}?sig=x" },
        )

    @Test
    fun `sends the session attempt range notes and read url to the AI and stores a DONE result`() {
        val repository = FakeRepository(pending)
        val requests = mutableListOf<AnalysisRequest>()

        val outcome = service(repository, done, requests).analyze(7)

        assertEquals(AnalysisOutcome.Saved(done), outcome)
        assertEquals(
            listOf(AnalysisRequest(7, 2, "https://storage.example/recordings/7/a.m4a?sig=x", 60, "JEONGGAN", notes)),
            requests,
        )
        assertEquals(listOf<Any>(Triple(pending, done, notes)), repository.saved)
    }

    @Test
    fun `stores the failure reason the AI returns`() {
        val repository = FakeRepository(pending)
        val failed = AnalysisResult.Failed(AnalysisFailReason.NO_SOUND)

        assertEquals(AnalysisOutcome.Saved(failed), service(repository, failed).analyze(7))
        assertEquals(listOf<Any>(pending to AnalysisFailReason.NO_SOUND), repository.saved)
    }

    @Test
    fun `does not call the AI when the session is not waiting for analysis`() {
        val requests = mutableListOf<AnalysisRequest>()

        assertEquals(AnalysisOutcome.NotPending, service(FakeRepository(null), done, requests).analyze(7))
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `discards a result when the session moved on to another attempt or state`() {
        val repository = FakeRepository(pending, accepts = false)

        assertEquals(AnalysisOutcome.Discarded, service(repository, done).analyze(7))
        assertEquals(emptyList(), repository.saved)
    }

    @Test
    fun `hides the read url when the request is logged`() {
        val request = AnalysisRequest(7, 2, "https://storage.example/a.m4a?sig=secret", 60, "JEONGGAN", notes)
        assertEquals(false, request.toString().contains("secret"))
    }
}
