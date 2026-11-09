package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

/** 휴대폰 배치 내용의 최대 너비. 넓은 창에서도 휴대폰 배치를 가운데에 둔다. */
val CompactContentMaxWidth: Dp = 560.dp

/**
 * 와이어프레임 화면 틀(`.screen`): 상단 바 · 본문 · 하단 고정 영역. 본문은 [contentMaxWidth]로 가운데에 둔다.
 * 태블릿 배치는 [com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints.ContentMaxWidth]를 넘긴다.
 * 상태 표시줄·내비게이션 바 영역은 상단 바·하단 영역이 비켜 간다.
 */
@Composable
fun EolssuScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    contentMaxWidth: Dp = CompactContentMaxWidth,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.imePadding().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        // 기본은 `background`(#F9FAF9). 기록·성장 보드처럼 흰 바탕 화면은 [containerColor]를 넘긴다.
        containerColor = containerColor ?: EolssuTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { topBar() } },
        bottomBar = {
            if (bottomBar != null) EolssuBottomBar(maxWidth = contentMaxWidth, content = bottomBar)
            else Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()) { content() }
        }
    }
}

/**
 * 태블릿 와이어프레임에서 화면 맨 위부터 상단 바까지의 거리(세로 40px · 가로 28px). 상태 표시줄 높이를 포함한다.
 */
private val TabletPortraitTopBarTop: Dp = 40.dp
private val TabletLandscapeTopBarTop: Dp = 28.dp

/**
 * 상단 바(`.topbar`): 왼쪽 뒤로 가기 원형 버튼 · 가운데 제목(보조 문구, 그 아래 [below]) · 오른쪽 자리. [titleFontSize]를 주면 제목만 그 크기로 바꾼다.
 * 휴대폰은 상태 표시줄 아래 12dp, 태블릿은 와이어프레임처럼 화면 맨 위에서 세로 40dp · 가로 28dp에 두고 제목을 17sp로 키운다.
 * 상태 표시줄 영역은 감싸는 쪽(`EolssuScaffold` 등)이 비켜 간다.
 */
@Composable
fun EolssuTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    below: (@Composable () -> Unit)? = null,
    maxWidth: Dp = CompactContentMaxWidth,
    titleFontSize: TextUnit = TextUnit.Unspecified,
    right: @Composable RowScope.() -> Unit = {},
) {
    val widthClass = currentWindowLayout().widthClass
    val statusBarTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val topPadding = when (widthClass) {
        WindowWidthClass.Compact -> 12.dp
        WindowWidthClass.Medium -> (TabletPortraitTopBarTop - statusBarTop).coerceAtLeast(0.dp)
        WindowWidthClass.Expanded -> (TabletLandscapeTopBarTop - statusBarTop).coerceAtLeast(0.dp)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(48.dp)) {
                if (onBack != null) {
                    EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.common_back), onBack)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (titleFontSize.isSpecified) titleFontSize else if (widthClass == WindowWidthClass.Compact) 16.sp else 17.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = EolssuTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                if (below != null) Box(Modifier.padding(top = 6.dp)) { below() }
            }
            Row(Modifier.widthIn(min = 48.dp), horizontalArrangement = Arrangement.End, content = right)
        }
    }
}

/** 최소 48dp 터치 영역을 가진 아이콘 버튼. */
@Composable
fun EolssuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        // 테두리 없는 아이콘 버튼(`.ibtn`)은 바탕도 투명하다(시트·대화상자 위에서 회색 원이 보이지 않게).
        color = if (bordered) EolssuTheme.colors.background else androidx.compose.ui.graphics.Color.Transparent,
        border = if (bordered) androidx.compose.foundation.BorderStroke(1.dp, EolssuTheme.colors.line) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) EolssuTheme.colors.text else EolssuTheme.colors.text.copy(alpha = 0.45f),
            )
        }
    }
}

/** 하단 고정 영역(`.bottombar`): 위 구분선(`#EEEBE3`), 버튼 사이 10dp. */
@Composable
fun EolssuBottomBar(
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = EolssuTheme.colors.surfaceStrong)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** 본문 위쪽의 회색 안내 문구(`.section-title`). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 28.dp, bottom = 12.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        color = EolssuTheme.colors.textMuted,
    )
}

/** 아이콘 + 회색 안내 문구 한 줄(`.preview-hint`). */
@Composable
fun InfoHint(text: String, modifier: Modifier = Modifier, icon: ImageVector = EolssuIcons.Info) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = EolssuTheme.colors.textMuted)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}
