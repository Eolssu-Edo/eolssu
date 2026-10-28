package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult

/** 태블릿 결과 화면에서 정간보에 표시할 오류 종류. [metric]이 null이면 모두 보인다. */
enum class ErrorFilter(val metric: EvaluationMetric?) {
    All(null),
    Pitch(EvaluationMetric.Pitch),
    Rhythm(EvaluationMetric.Rhythm),
    Length(EvaluationMetric.Length),
}

/** 정간보의 칸 하나(각·정간 번호, 0부터). */
data class ScoreCellPosition(val column: Int, val row: Int)

/** [filter]에 해당하는 틀린 칸 수. */
fun EvaluationResult.errorCount(filter: ErrorFilter): Int =
    if (filter.metric == null) errors.size else errors.count { it.metric == filter.metric }

/** 정간보에 색칠할 틀린 칸(각, 정간) → 틀린 항목. [filter]에 해당하지 않는 오류는 뺀다. */
fun EvaluationResult.visibleErrors(filter: ErrorFilter): Map<Pair<Int, Int>, EvaluationMetric> =
    errors.filter { filter.metric == null || it.metric == filter.metric }.associate { (it.column to it.row) to it.metric }

/** [cell]의 오류 항목. 필터와 관계없이 찾는다. 틀리지 않은 칸이면 null. */
fun EvaluationResult.errorAt(cell: ScoreCellPosition): EvaluationMetric? =
    errors.firstOrNull { it.column == cell.column && it.row == cell.row }?.metric

/**
 * 처음 고른 칸. 틀린 칸이 가장 많이 몰린 두 각에서 연주 순서로 첫 오류이고, 틀린 칸이 없으면 첫 칸이다.
 */
fun EvaluationResult.defaultSelectedCell(): ScoreCellPosition {
    val cluster = densestColumnPair() ?: return ScoreCellPosition(0, 0)
    val columns = cluster.first..cluster.first + 1
    val first = errors.filter { it.column in columns }.minWith(compareBy({ it.column }, { it.row }))
    return ScoreCellPosition(first.column, first.row)
}
