package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.scoreStrips

internal val PracticeStackGap = 6.dp
private val PracticeStackRowPadding = 2.dp

/** 한 각만 놓을 때 줄인 글자도 읽을 수 있는 정간 하한. 이보다 낮은 화면에서만 세로 스크롤이 생긴다. */
private val PracticeMinFallbackCell = 20.dp

/** 두 각을 위아래로 쌓기 위해 허용하는 정간 하한(사용자 결정: 글자를 줄여서라도 쌓는다). */
private val PracticeMinStackCell = 14.dp

/** 글자를 줄여서라도 두 각을 쌓는 짧은 각(아리랑·도라지타령의 세마치 3정간 등)의 최대 정간 수. */
private const val PracticeShortColumnCells = 4

/** 이미 여러 표시 줄로 나눈 각은 기존 렌더링을 보존한다. 정간 수로 짧은 각을 단정하지 않는다. */
internal fun Score.supportsPracticeColumnPacking(jangdan: Jangdan? = null): Boolean =
    columns.isNotEmpty() && columns.all { it.isNotEmpty() } && scoreStrips(this, jangdan).all { it.strip == 0 }

internal data class PracticeScorePacking(
    val columnsPerStack: Int,
    val metrics: JeongganboMetrics,
    val cellHeights: Map<Pair<Int, Int>, Dp>,
    /** 연주 순서. 화면에서는 이 묶음 목록만 오른쪽부터 배치한다. */
    val columnGroups: List<List<Int>>,
    val height: Dp,
    /** 같은 가로줄에 놓이는 각들이 공유하는 세로 공간. */
    val stackRowHeights: List<Dp>,
)

/** 각의 실제 높이에 맞춰 위→아래로 쌓고, 들어가지 않는 다음 각은 왼쪽 세로줄로 넘긴다. */
internal fun practiceScorePacking(
    score: Score,
    metrics: JeongganboMetrics,
    availableHeight: Dp,
    fontScale: Float = 1f,
    jangdan: Jangdan? = null,
): PracticeScorePacking {
    val scale = fontScale.coerceAtLeast(1f)
    val expanded = score.columns.flatMapIndexed { column, cells ->
        cells.mapIndexedNotNull { row, cell ->
            val parts = if (cell.units.isEmpty()) cell.notes.size else cell.units.map { it.row }.distinct().size
            (column to row).takeIf { parts >= 3 }
        }
    }.toSet()
    val weights = score.columns.mapIndexed { column, cells ->
        cells.indices.sumOf { row -> if ((column to row) in expanded) 1.5 else 1.0 }.toFloat()
    }
    // 같은 가로줄의 최대 높이를 공유해야 1·4·7각 / 2·5·8각 / 3·6·9각의 시작점이 일치한다.
    fun rowWeights(count: Int): List<Float> = List(count) { row ->
        weights.filterIndexed { column, _ -> column % count == row }.maxOrNull() ?: 0f
    }

    var count = 1
    var fitted = metrics.cellHeight
    var textRatio = 1f
    val packable = score.supportsPracticeColumnPacking(jangdan)
    if (packable && availableHeight.value.isFinite()) {
        // 글꼴 확대에서도 묶음을 유지한다. 글자도 같은 배율로 커지므로 정간 최소 크기는 배율 전 값으로 본다.
        val minimum = minOf(metrics.cellHeight, if (metrics.cellHeight > 44.dp) 44.dp else 32.dp)
        fun allowedFor(candidate: Int): Dp {
            val sharedWeight = rowWeights(candidate).sum()
            // 가로줄별 픽셀 반올림 여유와 악보 바깥 여백까지 포함해 맞춘다.
            val remaining = availableHeight - 14.dp - PracticeStackGap * (candidate - 1) -
                (metrics.headHeight + PracticeStackRowPadding) * candidate * scale
            return remaining / (sharedWeight * scale)
        }
        // 가장 짧은 각만 들어간다고 가정한 상한에서 시작해 전체 각·공유 가로줄 높이로 실제 배치를 결정한다.
        val minimumColumnHeight = (metrics.headHeight + PracticeStackRowPadding + minimum * weights.min()) * scale + PracticeStackGap
        val limit = minOf(score.columns.size, ((availableHeight - 14.dp + PracticeStackGap) / minimumColumnHeight).toInt())
        val found = (limit downTo 1).firstOrNull { allowedFor(it) >= minimum }
        val shortColumns = score.columns.size > 1 && score.columns.maxOf { it.size } <= PracticeShortColumnCells
        if ((found == null || found == 1) && shortColumns && allowedFor(2) >= PracticeMinStackCell) {
            // 짧은 각은 한 줄로 늘어놓지 않고 1각 아래 2각을 둔다. 낮은 화면에서는 정간과 글자를 같은 비율로 줄인다.
            count = 2
            fitted = minOf(minimum, allowedFor(2))
            textRatio = fitted / minimum
        } else if (found != null) {
            count = found
            fitted = minOf(metrics.cellHeight, allowedFor(found))
        } else {
            // 한 각도 최소 크기로 들어가지 않는 낮은 화면: 세로 스크롤 대신 정간과 글자를 같은 비율로 줄인다.
            fitted = allowedFor(1).coerceIn(PracticeMinFallbackCell, minimum)
            textRatio = fitted / minimum
        }
    }
    val groups = score.columns.indices.toList().chunked(count)
    val sharedWeights = if (groups.isEmpty()) emptyList() else rowWeights(count)
    val rowHeights = sharedWeights.map { weight ->
        metrics.headHeight + fitted * weight + PracticeStackRowPadding
    }
    // 같은 가로줄의 가장 긴 각에 맞춰 정간을 비례 확대하고 3소박 높이 비율을 유지한다.
    val cellHeights = if (packable) buildMap {
        score.columns.forEachIndexed { column, cells ->
            val stretched = fitted * (sharedWeights[column % count] / weights[column])
            cells.indices.forEach { row ->
                put(column to row, stretched * if ((column to row) in expanded) 1.5f else 1f)
            }
        }
    } else expanded.associateWith { fitted * 1.5f }
    return PracticeScorePacking(
        columnsPerStack = count,
        metrics = metrics.copy(cellHeight = fitted, yulSize = metrics.yulSize * textRatio, auxSize = metrics.auxSize * textRatio),
        cellHeights = cellHeights,
        columnGroups = groups,
        height = rowHeights.fold(0.dp) { total, height -> total + height } * scale +
            PracticeStackGap * (rowHeights.size - 1).coerceAtLeast(0) + 12.dp,
        stackRowHeights = rowHeights,
    )
}
