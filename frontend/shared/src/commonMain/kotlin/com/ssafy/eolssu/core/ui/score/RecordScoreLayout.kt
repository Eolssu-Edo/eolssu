package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Song

/** 축소 미리보기 대신 연습·평가의 전체 악보와 같은 크기를 사용한다. */
internal fun recordScoreMetrics(song: Song): JeongganboMetrics = JeongganboMetrics.practiceBoard(
    big = false,
    longestStrip = scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1,
)

/**
 * 한 정간의 세로 분할이 셋 이상이면 평가 화면처럼 행당 최소 반 정간을 확보한다.
 * 가로 분할의 음 수를 세로 높이로 오인하지 않으며 원본 좌표·음표·시간을 바꾸지 않는다.
 * 글꼴 배율은 JeongganboView에서 다른 정간과 함께 한 번 적용한다.
 */
internal fun recordScoreCellHeights(song: Song, metrics: JeongganboMetrics): Map<Pair<Int, Int>, Dp> =
    song.displayUnits.groupBy { it.column to it.row }.mapNotNull { (cell, units) ->
        val rows = units.map { it.unitRow }.distinct().size
        if (rows > 2) cell to metrics.cellHeight * (rows / 2f) else null
    }.toMap()

/** 오류 위치 악보에서 위아래로 나뉜 소박 한 줄의 최소 높이. 두 음 율명(보드 14sp)이 잘리지 않는다. */
private val ErrorScoreSplitRowHeight = 22.dp

/**
 * 오류 위치 악보의 정간 높이. 위아래로 나뉜 소박은 한 줄마다 [ErrorScoreSplitRowHeight] 이상을 주어 율명이 잘리지 않게 한다.
 * 정간 수가 같은 줄(세로 줄 하나)끼리는 가장 긴 줄에 맞춰 정간을 고르게 늘려 위아래 끝을 맞춘다.
 * 원본 좌표·음표·시간은 바꾸지 않으며, 글꼴 배율은 JeongganboView에서 한 번 적용한다.
 */
internal fun errorScoreCellHeights(
    song: Song,
    metrics: JeongganboMetrics,
    minSplitRowHeight: Dp = ErrorScoreSplitRowHeight,
): Map<Pair<Int, Int>, Dp> {
    val splitRows = song.displayUnits.groupBy { it.column to it.row }.mapValues { (_, units) -> units.map { it.unitRow }.distinct().size }
    fun natural(cell: Pair<Int, Int>): Dp = maxOf(metrics.cellHeight, minSplitRowHeight * (splitRows[cell] ?: 1))
    val strips = scoreStrips(song.score, song.jangdan)
    val totals = strips.associateWith { strip -> strip.rows.fold(0.dp) { sum, row -> sum + natural(strip.column to row) } }
    val longest = strips.groupBy { it.rows.count() }.mapValues { (_, same) -> same.maxOf { totals.getValue(it) } }
    return buildMap {
        strips.forEach { strip ->
            val scale = longest.getValue(strip.rows.count()) / totals.getValue(strip)
            strip.rows.forEach { row -> put(strip.column to row, natural(strip.column to row) * scale) }
        }
    }
}
