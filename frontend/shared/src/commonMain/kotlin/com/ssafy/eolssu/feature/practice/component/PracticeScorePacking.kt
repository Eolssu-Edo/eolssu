package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.scoreStrips

internal val PracticeStackGap = 6.dp
private val PracticeStackRowPadding = 2.dp

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
    val packable = score.supportsPracticeColumnPacking(jangdan)
    if (packable && availableHeight.value.isFinite()) {
        // 크게 보기와 글꼴 확대에서 정간 최소 크기를 깎지 않는다.
        val minimum = if (metrics.cellHeight > 44.dp || scale > 1f) metrics.cellHeight else 32.dp
        // 가장 짧은 각만 들어간다고 가정한 상한에서 시작해 전체 각·공유 가로줄 높이로 실제 배치를 결정한다.
        val minimumColumnHeight = (metrics.headHeight + PracticeStackRowPadding + minimum * weights.min()) * scale + PracticeStackGap
        val limit = minOf(score.columns.size, ((availableHeight - 14.dp + PracticeStackGap) / minimumColumnHeight).toInt())
        for (candidate in limit downTo 1) {
            val sharedWeight = rowWeights(candidate).sum()
            // 가로줄별 픽셀 반올림 여유와 악보 바깥 여백까지 포함해 맞춘다.
            val remaining = availableHeight - 14.dp - PracticeStackGap * (candidate - 1) -
                (metrics.headHeight + PracticeStackRowPadding) * candidate * scale
            val allowed = remaining / (sharedWeight * scale)
            if (allowed >= minimum) {
                count = candidate
                fitted = minOf(metrics.cellHeight, allowed)
                break
            }
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
        metrics = metrics.copy(cellHeight = fitted),
        cellHeights = cellHeights,
        columnGroups = groups,
        height = rowHeights.fold(0.dp) { total, height -> total + height } * scale +
            PracticeStackGap * (rowHeights.size - 1).coerceAtLeast(0) + 12.dp,
        stackRowHeights = rowHeights,
    )
}
