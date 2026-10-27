package com.ssafy.eolssu.feature.history

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
import com.ssafy.eolssu.feature.history.component.GrowthTiles
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.feature.history.component.MetricGrowthCard
import com.ssafy.eolssu.feature.history.component.RangeTabs
import com.ssafy.eolssu.feature.history.component.TrendCard
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.layout.GrowthExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_subtitle
import eolssu.shared.generated.resources.growth_title
import org.jetbrains.compose.resources.stringResource

/**
 * GROWTH-01 연습 기록지·성취도. 휴대폰·태블릿 세로는 한 칸으로 쌓고(태블릿은 여백·글자·그래프를 키운다),
 * 태블릿 가로·넓은 웹 창은 왼쪽(기간 · 요약 · 연습 시간)과 오른쪽(점수 변화 · 항목 성장 · 바로 가기) 두 칸이다.
 */
@Composable
fun GrowthScreen(
    state: GrowthUiState,
    widthClass: WindowWidthClass,
    todayEpochDay: Long,
    onBack: () -> Unit,
    onRange: (GrowthRange) -> Unit,
    onOpenMissions: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val frame = historyFrame(widthClass)
    val spacing = if (frame.isTablet) GrowthSpacing.Tablet else GrowthSpacing.Compact
    EolssuScaffold(
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, stringResource(Res.string.growth_title), stringResource(Res.string.growth_subtitle, state.stats.period), onBack) },
    ) {
        if (widthClass == WindowWidthClass.Expanded) {
            GrowthExpandedLayout(state, frame, spacing, todayEpochDay, onRange, onOpenMissions, onOpenRecords)
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
            GrowthTiles(state.stats, spacing)
            BarChartCard(state, spacing)
            if (state.trend.isNotEmpty()) TrendCard(state, todayEpochDay, spacing)
            if (state.metricGrowth.isNotEmpty()) MetricGrowthCard(state, spacing)
            GrowthLinks(state, spacing, onOpenMissions, onOpenRecords)
        }
    }
}
