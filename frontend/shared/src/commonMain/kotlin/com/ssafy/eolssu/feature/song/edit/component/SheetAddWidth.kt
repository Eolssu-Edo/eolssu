package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth

/** 태블릿 가로에서 악보 추가·가져오기 흐름 본문 폭(좌우 여백 20dp 포함). 두 가져오기 카드와 인식 결과 정간보가 넓게 들어간다. */
private val ExpandedSheetAddWidth: Dp = 720.dp

/** 태블릿 가로에서 하단 버튼 묶음의 최대 폭. 본문이 넓어져도 버튼은 휴대폰 열 폭을 넘지 않는다. */
private val ExpandedSheetAddButtonWidth: Dp = 560.dp

/**
 * 악보 추가·가져오기 화면이 `EolssuScaffold`·`EolssuTopBar`에 넘길 본문 최대 폭.
 * 태블릿 가로(Expanded)만 넓히고 휴대폰·태블릿 세로는 휴대폰 열([CompactContentMaxWidth]) 그대로 둔다(S15P31E102-978).
 */
@Composable
internal fun sheetAddContentMaxWidth(): Dp =
    if (currentWindowLayout().widthClass == WindowWidthClass.Expanded) ExpandedSheetAddWidth else CompactContentMaxWidth

/** 본문이 휴대폰 열보다 넓을 때만 하단 버튼 묶음을 [ExpandedSheetAddButtonWidth]로 가운데에 둔다. */
internal fun sheetAddBottomBar(contentMaxWidth: Dp, bar: @Composable ColumnScope.() -> Unit): @Composable ColumnScope.() -> Unit =
    if (contentMaxWidth <= CompactContentMaxWidth) {
        bar
    } else {
        {
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = ExpandedSheetAddButtonWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = bar,
            )
        }
    }
