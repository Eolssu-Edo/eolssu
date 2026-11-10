package com.ssafy.eolssu.feature.result.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * 위 내용([top]) · 남은 높이를 채우는 칸([fill]) · 아래 내용([bottom])을 세로로 놓는다.
 * [fill]은 화면 높이와 관계없이 [fillHeight]로 고정된다. 전체 높이가 화면보다 크면 바깥 스크롤이 이어받는다.
 */
@Composable
internal fun ResultFillColumn(
    fillHeight: Dp,
    gap: Dp,
    top: @Composable () -> Unit,
    fill: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(top, fill, bottom), modifier = modifier) { (topItems, fillItems, bottomItems), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(minWidth = width, maxWidth = width)
        val topPlaced = topItems.map { it.measure(loose) }
        val bottomPlaced = bottomItems.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val topHeight = topPlaced.sumOf { it.height }
        val bottomHeight = bottomPlaced.sumOf { it.height }
        val fixed = topHeight + bottomHeight + gapPx * 2
        val fillPx = fillHeight.roundToPx()
        val fillPlaced = fillItems.map { it.measure(Constraints.fixed(width, fillPx)) }
        val total = fixed + fillPx
        layout(width, total) {
            var y = 0
            topPlaced.forEach { it.placeRelative(0, y); y += it.height }
            y += gapPx
            fillPlaced.forEach { it.placeRelative(0, y) }
            y += fillPx + gapPx
            bottomPlaced.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}
