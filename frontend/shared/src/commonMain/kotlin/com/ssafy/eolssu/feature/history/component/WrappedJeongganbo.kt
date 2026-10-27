package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.scoreStrips

/**
 * 각을 연주 순서대로 여러 줄로 접는다. 각 하나는 [stripsPerColumn]만큼의 세로 줄을 차지하고, 한 줄에는 [slots]개까지 들어간다.
 * 각 하나가 [slots]보다 넓어도 그 각만으로 한 줄을 만든다. 결과는 줄마다 담을 각 번호 범위다.
 */
internal fun wrapColumns(stripsPerColumn: List<Int>, slots: Int): List<IntRange> {
    val capacity = slots.coerceAtLeast(1)
    val rows = mutableListOf<IntRange>()
    var start = 0
    var used = 0
    stripsPerColumn.forEachIndexed { column, count ->
        if (used > 0 && used + count > capacity) {
            rows += start until column
            start = column
            used = 0
        }
        used += count
    }
    if (stripsPerColumn.isNotEmpty()) rows += start until stripsPerColumn.size
    return rows
}

/** [width] 너비에 [metrics] 크기의 세로 줄이 몇 개 들어가는지. 정간보 좌우 안쪽 여백 2dp씩을 뺀다. */
internal fun stripSlots(width: Dp, metrics: JeongganboMetrics): Int =
    ((width - 4.dp + metrics.columnGap) / (metrics.columnWidth + metrics.columnGap)).toInt().coerceAtLeast(1)

/**
 * 너비에 맞춰 여러 줄로 접은 정간보(`Tab-RecordDetail-Port`). 줄마다 오른쪽 각부터 읽고, 다음 줄은 아래에서 다시 오른쪽부터 잇는다.
 * [follow]이면 현재 음이 든 줄이 다 보이지 않을 때 그 줄로 세로 스크롤한다.
 */
@Composable
internal fun WrappedJeongganbo(
    song: Song,
    metrics: JeongganboMetrics,
    currentIndex: Int,
    follow: Boolean,
    errors: Map<Pair<Int, Int>, EvaluationMetric>,
    onEventClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val slots = stripSlots(maxWidth, metrics)
        val rows = remember(song, slots) {
            val strips = scoreStrips(song.score, song.jangdan).groupingBy { it.column }.eachCount()
            wrapColumns(song.score.columns.indices.map { strips[it] ?: 1 }, slots)
        }
        val listState = rememberLazyListState()
        val column = song.timeline.getOrNull(currentIndex)?.column
        val currentRow = column?.let { c -> rows.indexOfFirst { c in it } } ?: -1
        LaunchedEffect(currentRow, follow, rows) {
            if (!follow || currentRow < 0) return@LaunchedEffect
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == currentRow }
            val fullyVisible = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) listState.animateScrollToItem(currentRow)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows) { range ->
                JeongganboView(
                    song = song,
                    metrics = metrics,
                    notation = ScoreNotation(showHangul = true),
                    scrollState = null,
                    currentIndex = currentIndex,
                    errors = errors,
                    onEventClick = { onEventClick(it.index) },
                    columns = range,
                )
            }
        }
    }
}
