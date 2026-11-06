package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key

/** 태블릿 결과 화면에서 정간보에 표시할 오류 종류. [kind]가 null이면 모두 보인다. */
enum class ErrorFilter(val kind: ScoreErrorKind?) {
    All(null),
    Pitch(ScoreErrorKind.Pitch),
    Rhythm(ScoreErrorKind.Rhythm),
    Length(ScoreErrorKind.Length),
    Missed(ScoreErrorKind.Missed),
}

/**
 * 정간보의 칸 하나(각·정간 번호, 0부터). [unit]은 한 정간을 나눈 소박 가운데 누른 자리다. 정간 전체를 고르면 null.
 */
data class ScoreCellPosition(val column: Int, val row: Int, val unit: ScoreUnitKey? = null)

/**
 * [filter]에 해당하는 틀린 칸 수. 전체는 오류 종류가 여럿인 칸도 한 칸으로 센다('틀린 정간 N개').
 * 종류별은 그 종류가 있는 칸 수다.
 */
fun EvaluationResult.errorCount(filter: ErrorFilter): Int =
    if (filter.kind == null) errors.distinctBy { it.column to it.row }.size else errors.count { it.kind == filter.kind }

/**
 * 칸(각, 정간)마다의 오류 종류 전부. 한 칸에 음이 여럿이면 종류도 여럿일 수 있다.
 * 종류는 [ScoreErrorKind] 순서(음정·박자·음 길이·미검출)로 겹치지 않게 놓고, [filter]에 해당하는 종류만 남긴다.
 */
fun EvaluationResult.visibleErrorKinds(filter: ErrorFilter): Map<Pair<Int, Int>, List<ScoreErrorKind>> =
    errors.filter { filter.kind == null || it.kind == filter.kind }
        .groupBy({ it.column to it.row }, { it.kind })
        .mapValues { (_, kinds) -> kinds.distinct().sorted() }

/**
 * 소박(표시 자리)마다 칠할 틀린 종류. 음별 판정([EvaluationResult.eventErrors])이 없으면 빈 지도라 정간 단위로 칠한다.
 * [filter]에 해당하는 종류만 남긴다.
 */
fun EvaluationResult.visibleUnitErrors(song: Song, filter: ErrorFilter): Map<ScoreUnitKey, List<ScoreErrorKind>> {
    if (eventErrors.isEmpty()) return emptyMap()
    return song.displayUnits.mapNotNull { unit ->
        val kinds = eventErrors[unit.event.index].orEmpty().filter { filter.kind == null || it == filter.kind }
        if (kinds.isEmpty()) null else unit.key to kinds
    }.toMap()
}

/** 정간보에 칠할 틀린 칸(각, 정간) → 대표 종류(바탕·테두리 색). 대표는 [visibleErrorKinds]의 첫 종류다. */
fun EvaluationResult.visibleErrors(filter: ErrorFilter): Map<Pair<Int, Int>, ScoreErrorKind> =
    visibleErrorKinds(filter).mapValues { it.value.first() }

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
