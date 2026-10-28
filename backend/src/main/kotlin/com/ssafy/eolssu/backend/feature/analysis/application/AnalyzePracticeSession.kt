package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisClient
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls

/**
 * 업로드가 끝나 `PENDING`이 된 세션의 현재 시도를 AI로 분석하고 결과를 저장한다(PRA-03).
 * 업로드 커밋 뒤 백그라운드에서 부른다. 시간 초과·연결 실패 처리는 S15P31E102-174에서 더한다.
 */
class AnalyzePracticeSession(
    private val repository: PracticeAnalysisRepository,
    private val client: AnalysisClient,
    private val scoreNotes: PracticeScoreNotes,
    private val readUrls: RecordingReadUrls,
) {
    fun analyze(sessionId: Long): AnalysisOutcome {
        val pending = repository.findPending(sessionId) ?: return AnalysisOutcome.NotPending
        val notes = scoreNotes.notesFor(pending)
        val request = AnalysisRequest(pending.sessionId, pending.attempt, readUrls.readUrlFor(pending), pending.bpm, pending.bpmUnit, notes)

        val result = client.analyze(request)
        val saved = when (result) {
            is AnalysisResult.Done -> repository.saveDone(pending, result, notes)
            is AnalysisResult.Failed -> repository.saveFailed(pending, result.reason)
        }
        return if (saved) AnalysisOutcome.Saved(result) else AnalysisOutcome.Discarded
    }
}

sealed interface AnalysisOutcome {
    data class Saved(val result: AnalysisResult) : AnalysisOutcome

    /** 세션이 분석 대기가 아니라 AI를 부르지 않았다. */
    data object NotPending : AnalysisOutcome

    /** 결과가 오는 사이 세션이 다른 시도나 상태로 바뀌어 버렸다. */
    data object Discarded : AnalysisOutcome
}
