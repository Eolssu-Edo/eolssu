package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar

/**
 * 태블릿 마이페이지 본문 너비. 와이어프레임 `.tabapp.auth .body`(세로 640px · 가로 800px)에서 좌우 여백(40 · 80px)을 뺀 값이다.
 * 가로·세로 모두 가운데 한 단이다.
 */
private val TabletPortraitBodyWidth: Dp = 560.dp
private val TabletLandscapeBodyWidth: Dp = 640.dp

/** 태블릿 하단 버튼 너비(`.tabapp.auth .bottombar > *`). */
private val TabletBottomButtonWidth: Dp = 560.dp

/**
 * 마이페이지 화면 틀(MY-01~05). 휴대폰은 최대 560dp, 태블릿은 가운데 한 단에 본문(세로 560dp · 가로 640dp)과
 * 하단 버튼(560dp)을 둔다. 본문은 좌우 20dp·세로 스크롤이고 최소 화면 높이만큼 늘어난다.
 * 휴대폰 본문 위 여백은 [topPadding](양식 화면 `.au-body` 12dp, 마이페이지 `.my-body` 8dp)이다.
 * [containerColor]를 주면 화면 바탕색을 바꾼다(마이페이지의 회색 바탕).
 */
@Composable
internal fun ProfileScaffold(
    widthClass: WindowWidthClass,
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    gap: Dp = 20.dp,
    topPadding: Dp = 12.dp,
    containerColor: Color? = null,
    bottomDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tablet = widthClass != WindowWidthClass.Compact
    EolssuScaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (tablet) {
                EolssuTopBar(title, onBack = onBack, modifier = Modifier.padding(horizontal = 20.dp), maxWidth = WindowBreakpoints.ContentMaxWidth + 40.dp)
            } else {
                EolssuTopBar(title, onBack = onBack)
            }
        },
        bottomBar = if (bottomBar != null && tablet) tabletBottomBar(bottomBar) else bottomBar,
        snackbarHostState = snackbarHostState,
        containerColor = containerColor,
        bottomDivider = bottomDivider,
        contentMaxWidth = when (widthClass) {
            WindowWidthClass.Compact -> CompactContentMaxWidth
            WindowWidthClass.Medium -> TabletPortraitBodyWidth + 40.dp
            WindowWidthClass.Expanded -> TabletLandscapeBodyWidth + 40.dp
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(start = 20.dp, end = 20.dp, top = if (tablet) 24.dp else topPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
    }
}

/** 태블릿 하단 고정 영역: 버튼 묶음을 [TabletBottomButtonWidth]로 가운데에 둔다. */
private fun tabletBottomBar(bar: @Composable ColumnScope.() -> Unit): @Composable ColumnScope.() -> Unit = {
    Column(
        Modifier.align(Alignment.CenterHorizontally).widthIn(max = TabletBottomButtonWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = bar,
    )
}
