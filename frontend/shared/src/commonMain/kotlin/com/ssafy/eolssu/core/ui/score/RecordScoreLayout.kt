package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.Dp
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
