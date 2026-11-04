package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics

internal val PracticeStackGap = 12.dp

internal fun Score.hasShortPracticeColumns(): Boolean =
    columns.isNotEmpty() && columns.all { it.size in 1..4 }

internal data class PracticeScorePacking(
    val columnsPerStack: Int,
    val metrics: JeongganboMetrics,
    val cellHeights: Map<Pair<Int, Int>, Dp>,
    /** 연주 순서. 화면에서는 이 묶음 목록만 오른쪽부터 배치한다. */
    val columnGroups: List<List<Int>>,
    val height: Dp,
)

/** 원본 좌표를 유지하며, 남은 높이에 맞춰 짧은 각을 최대 세 개씩 쌓는다. */
internal fun practiceScorePacking(
    score: Score,
    metrics: JeongganboMetrics,
    availableHeight: Dp,
    fontScale: Float = 1f,
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
    fun height(groups: List<List<Int>>, cellHeight: Dp): Dp =
        (groups.maxOfOrNull { group ->
            (metrics.headHeight * group.size + cellHeight * group.sumOf { weights[it].toDouble() }.toFloat()) * scale +
                PracticeStackGap * (group.size - 1)
        } ?: 0.dp) + 12.dp

    var count = 1
    var fitted = metrics.cellHeight
    if (score.hasShortPracticeColumns() && availableHeight.value.isFinite()) {
        // 크게 보기와 글꼴 확대에서 정간 최소 크기를 깎지 않는다.
        val minimum = if (metrics.cellHeight > 44.dp || scale > 1f) metrics.cellHeight else 32.dp
        for (candidate in minOf(3, score.columns.size) downTo 2) {
            val groups = score.columns.indices.toList().chunked(candidate)
            val allowed = groups.minOf { group ->
                // 정간별 픽셀 반올림으로 마지막 줄이 살짝 넘치지 않도록 2dp를 남긴다.
                val remaining = availableHeight - 14.dp - PracticeStackGap * (group.size - 1) - metrics.headHeight * group.size * scale
                remaining / (group.sumOf { weights[it].toDouble() }.toFloat() * scale)
            }
            if (allowed >= minimum) {
                count = candidate
                fitted = minOf(metrics.cellHeight, allowed)
                break
            }
        }
    }
    val groups = score.columns.indices.toList().chunked(count)
    return PracticeScorePacking(
        columnsPerStack = count,
        metrics = metrics.copy(cellHeight = fitted),
        cellHeights = expanded.associateWith { fitted * 1.5f },
        columnGroups = groups,
        height = height(groups, fitted),
    )
}
