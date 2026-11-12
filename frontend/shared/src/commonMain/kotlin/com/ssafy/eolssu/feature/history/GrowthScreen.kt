package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.history.component.AnchorPickerSheet
import com.ssafy.eolssu.feature.history.component.GrowthSpacing
import com.ssafy.eolssu.feature.history.component.GrowthStatus
import com.ssafy.eolssu.feature.history.component.HeatCalendarCard
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.feature.history.component.PeriodChartCard
import com.ssafy.eolssu.feature.history.component.RangeTabs
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.layout.GrowthExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_title
import org.jetbrains.compose.resources.stringResource

/** 연습 기록지 화면의 동작. */
data class GrowthActions(
    val onBack: () -> Unit = {},
    val onRange: (GrowthRange) -> Unit = {},
    val onMetric: (GrowthMetric) -> Unit = {},
    val onToggleDense: () -> Unit = {},
    val onPickAnchor: (Long) -> Unit = {},
    val onMoveHeatMonth: (Int) -> Unit = {},
    val onPickHeatDay: (Long) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * GROWTH-01 연습 기록지(A안 2차, S15P31E102-795): 주별·월별 탭, 기간 그래프 카드, 연습량 달력 카드.
 * 휴대폰·태블릿 세로는 한 칸으로 쌓고(태블릿은 여백·그래프를 키운다), 태블릿 가로·넓은 웹 창은 왼쪽 기간 그래프 · 오른쪽 연습량 달력 두 칸이다.
 * 받는 중이거나 받지 못했으면 탭 아래에 그 상태만 보인다. 기간 표시를 누르면 기준 날짜 고르기 시트가 열린다.
 */
@Composable
fun GrowthScreen(state: GrowthUiState, widthClass: WindowWidthClass, actions: GrowthActions) {
    val frame = historyFrame(widthClass)
    val spacing = if (frame.isTablet) GrowthSpacing.Tablet else GrowthSpacing.Compact
    var picking by rememberSaveable { mutableStateOf(false) }
    val chart = state.chart
    EolssuScaffold(
        // 연주 결과 D안 톤: 흰 바탕 위 옅은 테두리 카드.
        containerColor = EolssuTheme.colors.surface,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, stringResource(Res.string.growth_title), null, actions.onBack, bottom = 10.dp) },
    ) {
        if (chart != null && widthClass == WindowWidthClass.Expanded) {
            GrowthExpandedLayout(state, chart, frame, spacing, actions, onOpenPicker = { picking = true })
            return@EolssuScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = frame.gutter, end = frame.gutter, top = 4.dp, bottom = if (frame.isTablet) 28.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.gap),
        ) {
            RangeTabs(state.range, actions.onRange)
            if (chart == null) {
                GrowthStatus(state.load, actions.onRetry)
                return@Column
            }
            PeriodChartCard(
                chart, state.metric, state.dense, empty = state.history?.firstDay == null, spacing = spacing,
                onOpenPicker = { picking = true }, onMetric = actions.onMetric, onToggleDense = actions.onToggleDense,
            )
            HeatCalendarCard(state, spacing, actions.onMoveHeatMonth, actions.onPickHeatDay)
        }
    }
    if (picking && state.history != null) {
        AnchorPickerSheet(
            state,
            onPick = {
                actions.onPickAnchor(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
