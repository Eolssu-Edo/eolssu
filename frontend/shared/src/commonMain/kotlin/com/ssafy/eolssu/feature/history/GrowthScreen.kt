package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.feature.history.component.ScoreTrendCard
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.history.component.BarChartCard
import com.ssafy.eolssu.feature.history.component.GrowthLinks
import com.ssafy.eolssu.feature.history.component.GrowthSpacing
import com.ssafy.eolssu.feature.history.component.GrowthStatus
import com.ssafy.eolssu.feature.history.component.GrowthTiles
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.feature.history.component.MetricGrowthCard
import com.ssafy.eolssu.feature.history.component.RangeTabs
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.layout.GrowthExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_period_month
import eolssu.shared.generated.resources.growth_period_week
import eolssu.shared.generated.resources.growth_title
import org.jetbrains.compose.resources.stringResource

/**
 * GROWTH-01 연습 기록지·성취도. 휴대폰·태블릿 세로는 한 칸으로 쌓고(태블릿은 여백·글자·그래프를 키운다),
 * 태블릿 가로·넓은 웹 창은 왼쪽(기간 · 요약 · 연습 시간)과 오른쪽(항목 성장 · 바로 가기) 두 칸이다.
 * 고른 기간의 통계를 받는 중이거나 받지 못했으면 기간 탭 아래에 그 상태만 보인다.
 */
@Composable
fun GrowthScreen(
    state: GrowthUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onRange: (GrowthRange) -> Unit,
    onRetry: () -> Unit,
    onOpenSteps: () -> Unit,
    onOpenRecords: () -> Unit,
    trendActions: ScoreTrendActions = ScoreTrendActions.None,
) {
    val frame = historyFrame(widthClass)
    val spacing = if (frame.isTablet) GrowthSpacing.Tablet else GrowthSpacing.Compact
    val period = state.period
    val periodLabel = when (period.range) {
        GrowthRange.Week -> stringResource(Res.string.growth_period_week, period.month, period.weekOfMonth)
        GrowthRange.Month -> stringResource(Res.string.growth_period_month, period.month)
    }
    val stats = (state.load as? GrowthLoad.Loaded)?.stats
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, stringResource(Res.string.growth_title), periodLabel, onBack, bottom = 10.dp) },
    ) {
        if (stats != null && widthClass == WindowWidthClass.Expanded) {
            GrowthExpandedLayout(stats, state.completedSteps, frame, spacing, onRange, onOpenSteps, onOpenRecords, state.scoreTrend, state.today, trendActions)
            return@EolssuScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = frame.gutter, end = frame.gutter, top = if (frame.isTablet) 4.dp else 0.dp, bottom = if (frame.isTablet) 28.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.gap),
        ) {
            RangeTabs(state.range, onRange)
            if (stats == null) {
                GrowthStatus(state.load, onRetry)
                return@Column
            }
            GrowthTiles(stats, spacing)
            BarChartCard(stats, spacing)
            ScoreTrendCard(state.scoreTrend, state.today, spacing, trendActions)
            stats.metricGrowth?.let { MetricGrowthCard(it, spacing) }
            GrowthLinks(stats, state.completedSteps, spacing, onOpenSteps, onOpenRecords)
        }
    }
}
