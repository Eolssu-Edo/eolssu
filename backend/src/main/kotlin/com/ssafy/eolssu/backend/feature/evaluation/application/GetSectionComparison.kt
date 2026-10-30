package com.ssafy.eolssu.backend.feature.evaluation.application

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.domain.SectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.domain.SectionComparisonResult
import org.springframework.transaction.annotation.Transactional

/**
 * 이번 세션과 이전 세션의 저장 결과를 같은 읽기 전용 트랜잭션에서 비교한다(EVA-04).
 * 이전 세션 규칙은 PRA-01의 parentSessionId와 같다: 본인·미삭제가 아니면 404, 다른 곡이면 400, 미완료면 409.
 */
open class GetSectionComparison(private val evaluations: PracticeEvaluations, private val scores: EvaluationScoreCells) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, sessionId: Long, baselineSessionId: Long?): SectionComparisonLookup {
        val current = evaluations.findOwned(sessionId, userId) ?: return SectionComparisonLookup.NotFound
        if (current.status != "DONE") return SectionComparisonLookup.NotReady
        val baselineId = baselineSessionId ?: current.parentSessionId ?: return SectionComparisonLookup.InvalidBaseline
        if (baselineId == sessionId) return SectionComparisonLookup.InvalidBaseline
        val baseline = evaluations.findOwned(baselineId, userId) ?: return SectionComparisonLookup.NotFound
        if (baseline.songId != current.songId) return SectionComparisonLookup.InvalidBaseline
        if (baseline.status != "DONE") return SectionComparisonLookup.NotReady
        // 이전 결과가 이번 구간을 모두 담아야 같은 음표끼리 견줄 수 있다.
        if (baseline.startNoteIdx > current.startNoteIdx || current.endNoteIdx > baseline.endNoteIdx) {
            return SectionComparisonLookup.InvalidBaseline
        }
        val cells = scores.noteCells(current.songId, current.scoreVersion)?.takeIf { it.size > current.endNoteIdx }
            ?: return SectionComparisonLookup.ScoreChanged
        val result = SectionComparison.compare(
            current, evaluations.notes(requireNotNull(current.evaluationId)),
            baseline, evaluations.notes(requireNotNull(baseline.evaluationId)),
        )
        return SectionComparisonLookup.Found(
            baselineId, result, cells[current.startNoteIdx].column, cells[current.endNoteIdx].column, current.bpm, current.bpmUnit,
        )
    }
}

sealed interface SectionComparisonLookup {
    data object NotFound : SectionComparisonLookup
    data object NotReady : SectionComparisonLookup
    data object InvalidBaseline : SectionComparisonLookup
    data object ScoreChanged : SectionComparisonLookup
    data class Found(
        val baselineSessionId: Long, val comparison: SectionComparisonResult, val startColumn: Int, val endColumn: Int,
        val bpm: Int, val bpmUnit: String,
    ) : SectionComparisonLookup
}
