package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 화면에 그리는 세로 줄 하나. 한 각이 [Jangdan.strips]에 따라 여러 줄로 나뉠 수 있다.
 *
 * @property strip 같은 각 안에서의 줄 순서. 0이면 각 번호를 적는 머리 칸이 있다.
 * @property rows 이 줄에 그리는 정간 번호 범위.
 */
internal data class ScoreStrip(val column: Int, val strip: Int, val rows: IntRange)

/** 오른쪽 시작점에서 센 스크롤 위치에 필요한 세로 묶음. 양쪽 한 묶음을 미리 구성한다. */
internal fun visibleScoreStacks(stackCount: Int, scrollPx: Int, viewportPx: Int, stridePx: Float): IntRange {
    if (stackCount == 0) return IntRange.EMPTY
    require(stridePx > 0 && stridePx.isFinite())
    val first = floor(scrollPx.coerceAtLeast(0) / stridePx).toInt() - 1
    val last = ceil((scrollPx.toDouble().coerceAtLeast(0.0) + viewportPx.coerceAtLeast(0)) / stridePx).toInt()
    return first.coerceIn(0, stackCount - 1)..last.coerceIn(0, stackCount - 1)
}

/** 칸 너비와 간격을 각각 반올림한 실제 배치와 화면 밖 빈 공간의 폭을 일치시킨다. */
internal fun scoreStackSpanPx(count: Int, columnPx: Int, gapPx: Int): Int =
    if (count <= 0) 0 else count * columnPx + (count - 1) * gapPx

/**
 * 연주 순서(각 0의 첫 줄부터)로 나열한 줄 목록. 화면에는 이 목록을 오른쪽에서 왼쪽으로 놓는다.
 *
 * @param maxColumns 앞에서부터 이 수의 각만 그린다. 미리보기용.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다. 표기 설정 예시 칸용.
 * @param columnRange 이 범위의 각만 그린다. 악보를 여러 단으로 나눠 그릴 때 쓴다.
 * @param splitColumns 한 줄로 그릴 각을 [columnSplitAt]에서 두 줄로 나눈다. 휴대폰 연습 악보에서 한 각이 화면 높이에 들어가지 않을 때 쓴다.
 */
internal fun scoreStrips(
    score: Score,
    jangdan: Jangdan?,
    maxColumns: Int? = null,
    maxRows: Int? = null,
    columnRange: IntRange? = null,
    splitColumns: Boolean = false,
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
            // 공통 악보의 각 길이는 원본 columns가 정한다. 장단 이름의 샘플 정간 수로 자르지 않는다.
            val maxStrip = Jangdan.MAX_STRIP_CELLS
            val counts = if (score.timing != null || jangdan == null) List((size + maxStrip - 1) / maxStrip) { maxStrip } else jangdan.strips
            for (count in counts) {
                if (start >= size) break
                strips += ScoreStrip(column, strips.size, start until minOf(start + count, size))
                start += count
            }
            // 기존 악보도 알려진 장단보다 긴 각의 나머지를 버리지 않는다.
            while (start < size) {
                strips += ScoreStrip(column, strips.size, start until minOf(start + maxStrip, size))
                start += maxStrip
            }
            if (splitColumns && strips.size == 1 && size >= 2) {
                val at = columnSplitAt(size, jangdan)
                listOf(ScoreStrip(column, 0, 0 until at), ScoreStrip(column, 1, at until size))
            } else {
                strips
            }
        }
    }
}

/**
 * 한 각을 두 줄로 나눌 때 첫 줄의 정간 수. 각 길이가 장단 정간 수와 같으면 가운데에 가장 가까운 대강 경계에서 나누고,
 * 대강을 모르면(공통 악보처럼 장단 정보가 없거나 각 길이가 다르면) 가운데에서 나눈다. 예: 세마치 9(3·3·3) → 6, 12정간 → 6.
 */
internal fun columnSplitAt(size: Int, jangdan: Jangdan?): Int {
    val half = (size + 1) / 2
    val starts = jangdan?.takeIf { it.gak == size }?.daegangStarts.orEmpty().filter { it in 1 until size }
    return starts.minByOrNull { kotlin.math.abs(it - half) } ?: half
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

/** 이음 이벤트가 다음 정간까지 이어져도 현재 위치와 각 정간의 시간 구간을 비교한다. */
internal fun cellProgressAt(cellStart: Double, position: Double?): CellProgress = when {
    position == null || position < cellStart -> CellProgress.Upcoming
    position >= cellStart + 1 -> CellProgress.Past
    else -> CellProgress.Current
}

/**
 * 진행 위치([position], 곡 처음부터의 정간 수)가 표시 자리 [unit]의 끝까지 지났는지. 위치가 없으면 지나지 않았다.
 * 나눈 정간의 소박 경계는 분수 합이라 끝 값이 아주 조금 어긋날 수 있어 [PassedEpsilon]만큼 봐준다.
 */
internal fun unitPassed(unit: ScoreDisplayUnit, position: Double?): Boolean =
    position != null && position >= unit.unitStart + unit.unitDuration - PassedEpsilon

private const val PassedEpsilon = 1e-6
