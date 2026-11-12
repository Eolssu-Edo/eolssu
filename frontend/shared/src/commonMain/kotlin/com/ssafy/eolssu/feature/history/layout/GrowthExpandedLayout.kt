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
import com.ssafy.eolssu.feature.history.GrowthActions
import com.ssafy.eolssu.feature.history.GrowthUiState
import com.ssafy.eolssu.feature.history.PeriodChart
import com.ssafy.eolssu.feature.history.component.GrowthSpacing
import com.ssafy.eolssu.feature.history.component.HeatCalendarCard
import com.ssafy.eolssu.feature.history.component.HistoryFrame
import com.ssafy.eolssu.feature.history.component.PeriodChartCard
import com.ssafy.eolssu.feature.history.component.RangeTabs

/** 두 칸이 세로 스크롤 없이 들어가는 최소 높이(오른쪽 연습량 달력 6줄 기준). 창이 이보다 낮으면 전체를 세로로 스크롤한다. */
private val GrowthTwoColumnMinHeight = 680.dp

/**
 * 태블릿 가로 연습 기록지: 같은 너비의 두 칸(사이 24dp).
 * 왼쪽은 주별·월별 탭과 기간 그래프 카드(그래프가 남은 높이를 채운다), 오른쪽은 연습량 달력 카드다.
 */
@Composable
internal fun GrowthExpandedLayout(
    state: GrowthUiState,
    chart: PeriodChart,
    frame: HistoryFrame,
    spacing: GrowthSpacing,
    actions: GrowthActions,
    onOpenPicker: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = if (maxHeight > GrowthTwoColumnMinHeight) maxHeight else GrowthTwoColumnMinHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().height(height).padding(start = frame.gutter, end = frame.gutter, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing.gap)) {
                    RangeTabs(state.range, actions.onRange)
                    PeriodChartCard(
                        chart, state.metric, state.dense, empty = state.history?.firstDay == null, spacing = spacing,
                        onOpenPicker = onOpenPicker, onMetric = actions.onMetric, onToggleDense = actions.onToggleDense,
                        modifier = Modifier.weight(1f), fill = true,
                    )
                }
                Column(Modifier.weight(1f)) {
                    HeatCalendarCard(state, spacing, actions.onMoveHeatMonth, actions.onPickHeatDay)
                }
            }
        }
    }
}
