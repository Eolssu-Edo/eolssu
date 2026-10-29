package com.ssafy.eolssu.backend.feature.evaluation.application

import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluation
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import org.springframework.transaction.annotation.Transactional

/** 세션과 음표를 같은 읽기 전용 트랜잭션에서 조회한다. */
open class GetPracticeEvaluation(private val evaluations: PracticeEvaluations) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, sessionId: Long): EvaluationLookup {
        val row = evaluations.findOwned(sessionId, userId) ?: return EvaluationLookup.NotFound
        if (row.status != "DONE") return EvaluationLookup.NotReady
        val id = requireNotNull(row.evaluationId)
        return EvaluationLookup.Found(PracticeEvaluation(
            row.sessionId, id, row.songId, row.scoreVersion, row.bpm, row.bpmUnit,
            row.startedAt, row.endedAt, requireNotNull(row.evaluatedAt),
            requireNotNull(row.totalScore), requireNotNull(row.pitchScore),
            requireNotNull(row.timingScore), requireNotNull(row.durationScore),
            evaluations.notes(id), row.recordingDurationMs,
        ))
    }
}

sealed interface EvaluationLookup {
    data object NotFound : EvaluationLookup
    data object NotReady : EvaluationLookup
    data class Found(val evaluation: PracticeEvaluation) : EvaluationLookup
}
