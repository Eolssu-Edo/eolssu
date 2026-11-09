package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuTopBar

/*
 * 학습 화면(운지법 보기 · 정간보 쉽게 읽기 · 기초부터 배우기)의 창 너비별 틀 값.
 * 태블릿 와이어프레임(`.tabapp`)은 본문 좌우 40px, 가로 화면(1280px)에서는 내용 1120px을 가운데에 둔다.
 */

/** 태블릿 배치의 본문 좌우 여백(`.tabapp .body`). */
internal val GuideTabletSidePadding: Dp = 40.dp

/** 태블릿 하단 고정 영역 버튼의 최대 너비(`.tabapp .bottombar > *`). */
internal val GuideTabletBottomMaxWidth: Dp = 640.dp

/** 본문 좌우 여백. */
internal val WindowWidthClass.guideSidePadding: Dp
    get() = if (this == WindowWidthClass.Compact) 20.dp else GuideTabletSidePadding

/** 화면 틀([com.ssafy.eolssu.core.ui.component.EolssuScaffold])의 내용 최대 너비. 태블릿은 내용 1120dp에 좌우 여백을 더한다. */
internal val WindowWidthClass.guidePageMaxWidth: Dp
    get() = if (this == WindowWidthClass.Compact) {
        CompactContentMaxWidth
    } else {
        WindowBreakpoints.ContentMaxWidth + GuideTabletSidePadding * 2
    }

/** 창 너비에 맞춘 상단 바. 태블릿은 뒤로 가기 버튼을 본문 왼쪽 끝(40dp)에 맞춘다. */
@Composable
internal fun GuideTopBar(
    title: String,
    onBack: (() -> Unit)?,
    widthClass: WindowWidthClass,
    subtitle: String? = null,
    titleFontSize: TextUnit = TextUnit.Unspecified,
) {
    if (widthClass == WindowWidthClass.Compact) {
        EolssuTopBar(title, onBack = onBack, subtitle = subtitle, titleFontSize = titleFontSize)
    } else {
        // EolssuTopBar 안쪽 여백 20dp에 바깥 20dp를 더해 40dp, 내용 최대 1120dp가 되게 한다.
        EolssuTopBar(
            title,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = GuideTabletSidePadding - 20.dp),
            subtitle = subtitle,
            maxWidth = WindowBreakpoints.ContentMaxWidth + 40.dp,
            titleFontSize = titleFontSize,
        )
    }
}

/** 태블릿 하단 고정 영역: 버튼 묶음을 [GuideTabletBottomMaxWidth]로 가운데에 둔다. */
@Composable
internal fun ColumnScope.GuideTabletBottomContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.align(Alignment.CenterHorizontally).widthIn(max = GuideTabletBottomMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * [items]를 [columns]칸 격자로 놓는다(와이어프레임의 태블릿 `grid`). 한 칸이면 휴대폰처럼 세로로 쌓기만 한다.
 * [stretch]이면 한 줄의 칸을 가장 높은 칸에 맞춰 늘린다(CSS grid 기본값). 칸에는 너비·높이를 맞추는 Modifier를 넘긴다.
 */
@Composable
internal fun <T> GuideGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    stretch: Boolean = true,
    cell: @Composable (index: Int, item: T, cellModifier: Modifier) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(verticalGap)) {
        if (columns <= 1) {
            items.forEachIndexed { i, item -> cell(i, item, Modifier.fillMaxWidth()) }
        } else {
            items.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth().then(if (stretch) Modifier.height(IntrinsicSize.Max) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                ) {
                    row.forEachIndexed { k, item ->
                        cell(rowIndex * columns + k, item, Modifier.weight(1f).then(if (stretch) Modifier.fillMaxHeight() else Modifier))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
