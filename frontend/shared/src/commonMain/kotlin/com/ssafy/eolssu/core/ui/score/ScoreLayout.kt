package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreEvent

/**
 * 화면에 그리는 세로 줄 하나. 한 각이 [Jangdan.strips]에 따라 여러 줄로 나뉠 수 있다.
 *
 * @property strip 같은 각 안에서의 줄 순서. 0이면 각 번호를 적는 머리 칸이 있다.
 * @property rows 이 줄에 그리는 정간 번호 범위.
 */
internal data class ScoreStrip(val column: Int, val strip: Int, val rows: IntRange)

/**
 * 연주 순서(각 0의 첫 줄부터)로 나열한 줄 목록. 화면에는 이 목록을 오른쪽에서 왼쪽으로 놓는다.
 *
 * @param maxColumns 앞에서부터 이 수의 각만 그린다. 미리보기용.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다. 표기 설정 예시 칸용.
 * @param columnRange 이 범위의 각만 그린다. 악보를 여러 단으로 나눠 그릴 때 쓴다.
 */
internal fun scoreStrips(
    score: Score,
    jangdan: Jangdan,
    maxColumns: Int? = null,
    maxRows: Int? = null,
    columnRange: IntRange? = null,
): List<ScoreStrip> {
    val columnCount = minOf(maxColumns ?: score.columns.size, score.columns.size)
    val columns = (0 until columnCount).let { all -> if (columnRange == null) all.toList() else all.filter { it in columnRange } }
    return columns.flatMap { column ->
        val size = score.columns[column].size
        if (maxRows != null) {
            listOf(ScoreStrip(column, 0, 0 until minOf(maxRows, size)))
        } else {
            val strips = mutableListOf<ScoreStrip>()
            var start = 0
            for (count in jangdan.strips) {
                if (start >= size) break
                strips += ScoreStrip(column, strips.size, start until minOf(start + count, size))
                start += count
            }
            strips
        }
    }
}

/**
 * 태블릿 세로 연습 화면에서 악보를 위·아래 두 단으로 나눌 때 한 단에 넣을 각 수. 두 단이 필요 없거나 들어가지 않으면 null(한 단, 가로 스크롤).
 * 가장 긴 줄이 두 단 모두에 정간 [minCellHeight] 이상으로 들어가야 두 단으로 나눈다(와이어프레임 `twoBands`·`gakPerBand`).
 *
 * @param stripsPerColumn 한 각을 나눈 줄 수([Jangdan.strips]의 크기).
 * @param longestStrip 가장 긴 줄의 정간 수.
 */
fun twoBandColumnCount(
    columnCount: Int,
    stripsPerColumn: Int,
    longestStrip: Int,
    availableWidth: Dp,
    availableHeight: Dp,
    metrics: JeongganboMetrics,
    minCellHeight: Dp,
): Int? {
    val bandHeight = metrics.headHeight + ScoreVerticalPadding + minCellHeight * longestStrip
    if (bandHeight * 2 + BandGap > availableHeight) return null
    val stripsFit = ((availableWidth - ScoreHorizontalPadding + metrics.columnGap) / (metrics.columnWidth + metrics.columnGap)).toInt()
    val perBand = maxOf(1, stripsFit / stripsPerColumn)
    return if (perBand >= columnCount) null else perBand
}

/** 단과 단 사이 간격. */
val BandGap: Dp = 16.dp

/** [JeongganboView]가 줄 위·아래에 두는 여백 합(위 2dp, 아래 10dp). */
internal val ScoreVerticalPadding: Dp = 12.dp

/** [JeongganboView]가 줄 양옆에 두는 여백 합(2dp씩). */
internal val ScoreHorizontalPadding: Dp = 4.dp

/** 정간이 현재 연주 위치와 어떤 관계인지. */
internal enum class CellProgress { Upcoming, Current, Past }

/** [events]는 한 정간에 든 음들. 현재 위치가 없으면 모두 [CellProgress.Upcoming]이다. */
internal fun cellProgress(events: List<ScoreEvent>, currentIndex: Int?): CellProgress = when {
    currentIndex == null -> CellProgress.Upcoming
    events.any { it.index == currentIndex } -> CellProgress.Current
    events.all { it.index < currentIndex } -> CellProgress.Past
    else -> CellProgress.Upcoming
}
