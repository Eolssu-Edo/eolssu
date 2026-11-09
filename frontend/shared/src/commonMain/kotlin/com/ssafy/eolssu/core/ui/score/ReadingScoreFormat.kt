package com.ssafy.eolssu.core.ui.score

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Song

/**
 * 자동으로 넘어가는 연습 화면 악보를 뺀 모든 화면이 함께 쓰는 정간보 형식.
 * 평가 결과 카드·틀린 부분·이전 연주 결과·이전 연주와 비교·구간 고르기·구간 반복·악보 편집이 같은 크기와 칸 규칙으로 그린다.
 *
 * - 각·정간·율명은 연습 악보 크기(각 78dp · 정간 44dp · 율명 21sp, 11정간 이상인 각은 정간 32dp · 율명 18sp)다.
 * - 위아래로 나뉜 소박은 한 줄마다 [ReadingSplitRowHeight] 이상을 주고, 율명을 두 음 16sp · 셋 이상 15sp로 그린다.
 *   줄어든 한자가 잘리지 않고 칸 위아래 선에 붙지 않는다.
 * - 정간 수가 같은 세로 줄끼리는 가장 긴 줄에 맞춰 정간을 고르게 늘려 각의 위아래 끝을 맞춘다.
 * - 곁줄(가사 칸)은 곡에 가사가 있을 때만 둔다.
 *
 * 원본 각·정간·음표·시간은 바꾸지 않는다. 글꼴 배율은 [JeongganboView]가 한 번 적용한다.
 */
@Immutable
data class ReadingScoreFormat(
    val metrics: JeongganboMetrics,
    val look: JeongganboLook,
    val cellHeights: Map<Pair<Int, Int>, Dp>,
)

/** 위아래로 나뉜 소박 한 줄의 최소 높이. 두 음 16sp · 셋 이상 15sp 율명이 위아래 선과 떨어져 보인다. */
internal val ReadingSplitRowHeight = 26.dp

/** 나뉜 두 음 · 셋 이상 율명 크기(sp). */
private const val ReadingSplitTwoSp = 16f
private const val ReadingSplitManySp = 15f

/** 곁줄(가사 칸) 너비. 연습 화면 악보와 같다. */
private val ReadingLyricLane = 25.dp

/**
 * [song]을 그릴 공통 형식. [lyrics]가 false이면 가사가 있어도 곁줄을 두지 않는다(이전 연주 결과의 좁은 기록 화면).
 */
fun readingScoreFormat(song: Song, lyrics: Boolean = true): ReadingScoreFormat {
    val metrics = readingScoreMetrics(song)
    val lane = ReadingLyricLane.takeIf { lyrics && song.hasLyrics() }
    return ReadingScoreFormat(metrics, JeongganboLook.Reading.copy(lyricLane = lane), readingCellHeights(song, metrics))
}

/** [readingScoreFormat]을 곡이 바뀔 때만 다시 계산한다. */
@Composable
fun rememberReadingScoreFormat(song: Song, lyrics: Boolean = true): ReadingScoreFormat =
    remember(song, lyrics) { readingScoreFormat(song, lyrics) }

/** 공통 형식의 각·정간·율명 크기. 나뉜 소박 율명만 [ReadingSplitTwoSp]·[ReadingSplitManySp]로 키운다. */
internal fun readingScoreMetrics(song: Song): JeongganboMetrics {
    val longestStrip = scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1
    val base = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip)
    return base.copy(
        splitScaleTwo = ReadingSplitTwoSp / base.yulSize.value,
        splitScaleMany = ReadingSplitManySp / base.yulSize.value,
    )
}

/**
 * 공통 형식의 정간 높이. 위아래로 나뉜 소박은 한 줄마다 [minSplitRowHeight] 이상을 준다.
 * 가로로 나뉜 음 수는 세로 높이로 세지 않는다. 정간 수가 같은 줄(세로 줄 하나)끼리는 가장 긴 줄에 맞춰
 * 정간을 고르게 늘려 위아래 끝을 맞춘다. 원본 좌표·음표·시간은 바꾸지 않는다.
 */
internal fun readingCellHeights(
    song: Song,
    metrics: JeongganboMetrics,
    minSplitRowHeight: Dp = ReadingSplitRowHeight,
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

/** 악보에 가사가 한 칸이라도 있는지. */
private fun Song.hasLyrics(): Boolean = score.columns.any { cells -> cells.any { !it.lyric.isNullOrBlank() } }
