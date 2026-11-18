package com.ssafy.eolssu.feature.result.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 태블릿 가로(Expanded) 결과 화면의 왼쪽 요약 칸 너비. 점수 상자 항목 막대가 휴대폰과 비슷한 길이로 보인다. */
internal val ResultSummaryPaneWidth: Dp = 380.dp

/** 태블릿 가로(Expanded) 결과 화면 하단 버튼 줄의 최대 너비. 버튼 하나가 300dp 남짓으로 넘지 않게 한다. */
internal val ResultBottomBarMaxWidth: Dp = 640.dp

/** 두 칸 사이 간격. */
private val ResultPaneGap: Dp = 24.dp

/**
 * 연주 결과·구간 재평가 결과의 태블릿 가로(Expanded) 두 칸 배치(S15P31E102-974).
 * 왼쪽 [ResultSummaryPaneWidth] 칸에 점수 상자·필터 같은 요약을 세로로 쌓아 따로 스크롤하고,
 * 오른쪽 칸은 남은 너비·높이를 받아 악보를 그린다. 오른쪽 칸의 스크롤은 [score]가 정한다.
 */
@Composable
internal fun ResultExpandedPanes(
    summary: @Composable ColumnScope.() -> Unit,
    score: @Composable BoxWithConstraintsScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ResultPaneGap),
    ) {
        Column(
            Modifier.width(ResultSummaryPaneWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 2.dp),
            content = summary,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(top = 2.dp), content = score)
    }
}

/**
 * 결과 화면 하단 버튼 한 줄. 버튼은 [content]에서 `weight`로 나눈다. 글꼴을 키워도 두 버튼 높이가 같도록 높이를 가장 큰 버튼에 맞춘다.
 * 태블릿 가로([expanded])는 줄 너비를 [ResultBottomBarMaxWidth]로 묶어 오른쪽(악보 칸 쪽)에 붙이고, 그 밖에는 줄 전체를 쓴다.
 */
@Composable
internal fun ColumnScope.ResultButtonRow(expanded: Boolean, content: @Composable RowScope.() -> Unit) {
    val width = if (expanded) Modifier.align(Alignment.End).widthIn(max = ResultBottomBarMaxWidth).fillMaxWidth() else Modifier.fillMaxWidth()
    Row(width.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
