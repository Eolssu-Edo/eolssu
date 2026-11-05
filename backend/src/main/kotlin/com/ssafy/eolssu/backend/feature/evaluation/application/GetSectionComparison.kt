package com.ssafy.eolssu.backend.feature.evaluation.application

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.domain.SectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.domain.SectionComparisonResult
import java.time.Instant
import org.springframework.transaction.annotation.Transactional

/**
 * 이번 세션과 이전 세션의 저장 결과를 같은 읽기 전용 트랜잭션에서 비교한다(EVA-04).
 * 이전 세션은 baselineSessionId → parentSessionId → 같은 곡 직전 세션 순서로 고른다. 직전 세션도 없으면 첫 연주로 답한다.
 * 고른 세션의 규칙은 PRA-01의 parentSessionId와 같다: 본인·미삭제가 아니면 404, 다른 곡이면 400, 미완료면 409.
 * 이번 세션보다 나중에 시작한 세션은 이전 결과가 아니므로 400이다.
 */
open class GetSectionComparison(private val evaluations: PracticeEvaluations, private val scores: EvaluationScoreCells) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, sessionId: Long, baselineSessionId: Long?): SectionComparisonLookup {
        val current = evaluations.findOwned(sessionId, userId) ?: return SectionComparisonLookup.NotFound
        if (current.status != "DONE") return SectionComparisonLookup.NotReady
        val source = when {
            baselineSessionId != null -> BaselineSource.QUERY
            current.parentSessionId != null -> BaselineSource.PARENT
            else -> BaselineSource.PREVIOUS
        }
        val baselineId = baselineSessionId ?: current.parentSessionId ?: evaluations.findPreviousSessionId(userId, current)
        val baseline = baselineId?.let { id ->
            if (id == sessionId) return SectionComparisonLookup.InvalidBaseline
            val found = evaluations.findOwned(id, userId) ?: return SectionComparisonLookup.NotFound
            if (found.songId != current.songId) return SectionComparisonLookup.InvalidBaseline
            if (found.status != "DONE") return SectionComparisonLookup.NotReady
            // 나중 세션을 이전 결과로 넣으면 이전·이번과 차이의 부호가 뒤바뀐다.
            if (found.startedAt.isAfter(current.startedAt)) return SectionComparisonLookup.InvalidBaseline
            // 같은 악보에서는 이전 결과가 이번 구간을 모두 담아야 같은 음표끼리 견줄 수 있다. 악보가 다르면 음표를 대지 않는다.
            val coversRange = found.startNoteIdx <= current.startNoteIdx && current.endNoteIdx <= found.endNoteIdx
            if (found.scoreVersion == current.scoreVersion && !coversRange) return SectionComparisonLookup.InvalidBaseline
            found
        }
        val cells = scores.noteCells(current.songId, current.scoreVersion)?.takeIf { it.size > current.endNoteIdx }
            ?: return SectionComparisonLookup.ScoreChanged
        val result = SectionComparison.compare(
            current, evaluations.notes(requireNotNull(current.evaluationId)),
            baseline, baseline?.let { evaluations.notes(requireNotNull(it.evaluationId)) }.orEmpty(),
        )
        return SectionComparisonLookup.Found(
            baseline?.let { ComparedBaseline(it.sessionId, it.startedAt, source, it.bpm, it.bpmUnit) },
            result, cells[current.startNoteIdx].column, cells[current.endNoteIdx].column,
            current.startNoteIdx == 0 && current.endNoteIdx == cells.lastIndex, current.bpm, current.bpmUnit,
        )
    }
}

enum class BaselineSource { QUERY, PARENT, PREVIOUS }

data class ComparedBaseline(val sessionId: Long, val startedAt: Instant, val source: BaselineSource, val bpm: Int, val bpmUnit: String)

sealed interface SectionComparisonLookup {
    data object NotFound : SectionComparisonLookup
    data object NotReady : SectionComparisonLookup
    data object InvalidBaseline : SectionComparisonLookup
    data object ScoreChanged : SectionComparisonLookup
    data class Found(
        val baseline: ComparedBaseline?, val comparison: SectionComparisonResult, val startColumn: Int, val endColumn: Int,
        val isFullSong: Boolean, val bpm: Int, val bpmUnit: String,
    ) : SectionComparisonLookup
}
