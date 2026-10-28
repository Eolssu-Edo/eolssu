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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar

/** 태블릿 마이페이지 내용 너비(`.tabapp.auth .body`, 좌우 여백 포함 640dp). 가로·세로 모두 가운데 한 단이다. */
private val TabletContentMaxWidth: Dp = 640.dp

/**
 * 마이페이지 화면 틀(MY-01~05). 휴대폰은 최대 560dp, 태블릿은 가운데 640dp 한 단에 본문과 하단 버튼을 둔다.
 * 본문은 좌우 20dp·세로 스크롤이고 최소 화면 높이만큼 늘어난다.
 */
@Composable
internal fun ProfileScaffold(
    widthClass: WindowWidthClass,
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    gap: Dp = 20.dp,
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
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = if (tablet) TabletContentMaxWidth else CompactContentMaxWidth,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(start = 20.dp, end = 20.dp, top = if (tablet) 24.dp else 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
    }
}
