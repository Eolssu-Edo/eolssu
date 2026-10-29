package com.ssafy.eolssu.feature.history.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.GrowthStats
import com.ssafy.eolssu.feature.history.component.BarChartCard
import com.ssafy.eolssu.feature.history.component.GrowthLinks
import com.ssafy.eolssu.feature.history.component.GrowthSpacing
import com.ssafy.eolssu.feature.history.component.GrowthTiles
import com.ssafy.eolssu.feature.history.component.HistoryFrame
import com.ssafy.eolssu.feature.history.component.MetricGrowthCard
import com.ssafy.eolssu.feature.history.component.RangeTabs

/** 두 칸이 세로 스크롤 없이 들어가는 최소 높이. 창이 이보다 낮으면 전체를 세로로 스크롤한다. */
private val GrowthTwoColumnMinHeight = 560.dp

/**
 * 태블릿 가로 연습 기록지(`Tab-Growth` land): 같은 너비의 두 칸(사이 24dp).
 * 왼쪽 연습 시간 카드가 남은 높이를 채우고, 오른쪽에 항목별 성장과 바로 가기를 둔다.
 */
@Composable
internal fun GrowthExpandedLayout(
    stats: GrowthStats,
    completedSteps: Int,
    frame: HistoryFrame,
    spacing: GrowthSpacing,
    onRange: (GrowthRange) -> Unit,
    onOpenSteps: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = if (maxHeight > GrowthTwoColumnMinHeight) maxHeight else GrowthTwoColumnMinHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().height(height).padding(start = frame.gutter, end = frame.gutter, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing.gap)) {
                    RangeTabs(stats.range, onRange)
                    GrowthTiles(stats, spacing)
                    BarChartCard(stats, spacing, Modifier.weight(1f), fill = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.gap)) {
                    stats.metricGrowth?.let { MetricGrowthCard(it, spacing) }
                    GrowthLinks(stats, completedSteps, spacing, onOpenSteps, onOpenRecords)
                }
            }
        }
    }
}
