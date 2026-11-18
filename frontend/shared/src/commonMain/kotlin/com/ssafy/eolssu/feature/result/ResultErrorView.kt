package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key

/**
 * 정간보의 칸 하나(각·정간 번호, 0부터). [unit]은 한 정간을 나눈 소박 가운데 누른 자리다. 정간 전체를 고르면 null.
 */
data class ScoreCellPosition(val column: Int, val row: Int, val unit: ScoreUnitKey? = null)

/** [cell]의 오류 종류 전부. 필터와 관계없이 찾는다. 틀리지 않은 칸이면 빈 목록. */
fun EvaluationResult.errorKindsAt(cell: ScoreCellPosition): List<ScoreErrorKind> =
    errors.filter { it.column == cell.column && it.row == cell.row }.map { it.kind }.distinct().sorted()

/**
 * 소박 하나([unit])의 오류 종류. 서버 결과는 음별 판정([EvaluationResult.eventErrors])을 쓰고,
 * 음별 판정이 없는 예시 결과는 그 정간의 오류 종류로 대신한다.
 */
fun EvaluationResult.errorKindsAt(unit: ScoreDisplayUnit): List<ScoreErrorKind> =
    if (eventErrors.isEmpty()) errorKindsAt(ScoreCellPosition(unit.column, unit.row)) else eventErrors[unit.event.index].orEmpty()

/**
 * 카드에 보여줄 소박. 누른 소박이 있으면 그것, 없으면 그 정간에서 처음 틀린 소박(없으면 첫 소박)이다.
 * 소박으로 나뉘지 않은 정간이면 그 정간의 유일한 자리다.
 */
fun EvaluationResult.shownUnit(cell: ScoreCellPosition, units: List<ScoreDisplayUnit>): ScoreDisplayUnit? =
    units.firstOrNull { it.key == cell.unit } ?: units.firstOrNull { errorKindsAt(it).isNotEmpty() } ?: units.firstOrNull()

/** [cell]의 대표 오류 종류. 필터와 관계없이 찾는다. 틀리지 않은 칸이면 null. */
fun EvaluationResult.errorAt(cell: ScoreCellPosition): ScoreErrorKind? = errorKindsAt(cell).firstOrNull()

/**
 * 처음 고른 칸. 틀린 칸이 가장 많이 몰린 두 각에서 연주 순서로 첫 오류이고, 틀린 칸이 없으면 첫 칸이다.
 */
fun EvaluationResult.defaultSelectedCell(): ScoreCellPosition {
    val cluster = densestColumnPair() ?: return ScoreCellPosition(0, 0)
    val columns = cluster.first..cluster.first + 1
    val first = errors.filter { it.column in columns }.minWith(compareBy({ it.column }, { it.row }))
    return ScoreCellPosition(first.column, first.row)
}
