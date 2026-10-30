package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.component.EolssuBottomBar
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuTopBar

/** 태블릿 세로 배치의 내용 너비(와이어프레임 `max-width: 720px`). */
internal val TabletPortraitContentWidth: Dp = 720.dp

/** 태블릿 내용 양옆의 최소 여백(와이어프레임 본문 `padding: 0 40px`). */
private val TabletMinGutter: Dp = 40.dp

/** 태블릿 세로 하단 버튼 너비(와이어프레임 `.bottombar` 800dp 너비에 `padding: 0 80px`). */
private val TabletBottomButtonWidth: Dp = 640.dp

/** 태블릿 세로 하단 고정 버튼 높이(와이어프레임 56px). 가로 배치의 칸 안 버튼도 같은 높이를 쓴다. */
internal val TabletButtonHeight: Dp = 56.dp

/**
 * 내용을 [expanded]이면 최대 [WindowBreakpoints.ContentMaxWidth], 아니면 [TabletPortraitContentWidth]로 가운데에 둘 때 양옆 여백.
 * 와이어프레임 1280dp 가로 창에서는 80dp, 800dp 세로 창에서는 40dp가 된다.
 */
internal fun tabletGutter(windowWidth: Dp, expanded: Boolean): Dp {
    val content = if (expanded) WindowBreakpoints.ContentMaxWidth else TabletPortraitContentWidth
    return maxOf(TabletMinGutter, (windowWidth - content) / 2)
}

/**
 * 곡·내 악보·PDF 올리기 태블릿 화면 틀. 상단 바는 세로에서 창 양옆 32dp, 가로에서 내용 가장자리에 맞추고,
 * 하단 고정 버튼(세로만)은 640dp로 가운데에 둔다. [content]는 내용 양옆 여백(gutter)을 받아 스크롤 영역 안쪽에 쓴다.
 * 휴대폰 화면 틀([com.ssafy.eolssu.core.ui.component.EolssuScaffold])과 같은 색·여백 처리를 따른다.
 * [pageTopBar]이면 가운데 제목 대신 페이지형 상단 바(`.page-head`, SONG-01 · SONG-02)를 쓰고, 뒤로 가기 원을 내용 가장자리에 맞추며 구분선은 창 너비로 긋는다.
 */
@Composable
internal fun SongTabletScaffold(
    expanded: Boolean,
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    pageTopBar: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable (gutter: Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = tabletGutter(maxWidth, expanded)
        val windowWidth = maxWidth
        Scaffold(
            containerColor = EolssuTheme.colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    if (pageTopBar) {
                        // 휴대폰처럼 원이 내용 가장자리(gutter)에서 시작하도록, 줄 너비를 내용 너비 + 양옆 20dp로 둔다.
                        EolssuPageTopBar(title = title, onBack = onBack, maxWidth = windowWidth - (gutter - 20.dp) * 2)
                        return@Box
                    }
                    // 상단 바 안쪽 여백 20dp에 더해, 세로는 창 가장자리에서 32dp, 가로는 내용 가장자리에 맞춘다.
                    EolssuTopBar(
                        title = title,
                        onBack = onBack,
                        modifier = Modifier.padding(horizontal = if (expanded) gutter - 20.dp else 12.dp),
                        subtitle = subtitle,
                        maxWidth = Dp.Infinity,
                    )
                }
            },
            bottomBar = {
                if (bottomBar != null) {
                    EolssuBottomBar(maxWidth = TabletBottomButtonWidth + 40.dp, content = bottomBar)
                } else {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content(gutter) }
        }
    }
}
