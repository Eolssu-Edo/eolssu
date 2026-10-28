package com.ssafy.eolssu.feature.auth.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_brand
import eolssu.shared.generated.resources.auth_brand_body
import eolssu.shared.generated.resources.auth_brand_headline
import org.jetbrains.compose.resources.stringResource

/**
 * 로그인·가입 화면 틀의 배치. 와이어프레임 '첫 실행 · 로그인·회원가입 — 세로는 가운데 한 단, 가로는 브랜드 칸 + 입력 칸'.
 */
enum class AuthFrame {
    /** 휴대폰. */
    Compact,

    /** 태블릿 세로: 가운데 한 단(본문 560dp). */
    Portrait,

    /** 태블릿 가로·넓은 웹 창의 한 단 화면(약관 보기 · 마이크 안내 · 소셜 로그인, 본문 640dp). */
    Landscape,

    /** 태블릿 가로·넓은 웹 창: 왼쪽 브랜드 칸 + 오른쪽 입력 칸(약관 동의 · 로그인 · 가입 · 프로필). */
    Split,
    ;

    val isTablet: Boolean get() = this != Compact

    /** 화면 틀의 내용 최대 너비. 본문 좌우 여백 20dp를 더한 값이다. */
    internal val contentMaxWidth: Dp
        get() = when (this) {
            Compact -> CompactContentMaxWidth
            Portrait, Split -> AuthTabletColumnWidth + 40.dp
            Landscape -> 680.dp
        }
}

/** 창 너비와 화면 종류로 배치를 정한다. [split]은 가로에서 브랜드 칸을 두는 화면인지. */
fun authFrameOf(widthClass: WindowWidthClass, split: Boolean): AuthFrame = when (widthClass) {
    WindowWidthClass.Compact -> AuthFrame.Compact
    WindowWidthClass.Medium -> AuthFrame.Portrait
    WindowWidthClass.Expanded -> if (split) AuthFrame.Split else AuthFrame.Landscape
}

/** 태블릿 입력 칸·단계 막대·하단 버튼 너비(`.tabapp.auth .au-progress`, `.bottombar > *`). */
internal val AuthTabletColumnWidth: Dp = 560.dp

/** 지금 화면의 배치. [AuthScaffold] 안에서 제목 크기·단계 막대·상단 바가 읽는다. */
internal val LocalAuthFrame = staticCompositionLocalOf { AuthFrame.Compact }

/** 1280dp 화면 기준 브랜드 칸 너비(`.ta-brand`, 560px)의 비율. */
private const val BrandPaneRatio = 560f / 1280f

/**
 * 로그인·가입 화면 틀. 휴대폰은 [EolssuScaffold] 그대로, 태블릿은 본문과 하단 버튼을 560dp(가로 한 단 본문은 640dp)로 가운데에 두고,
 * [AuthFrame.Split]이면 왼쪽에 브랜드 칸을 두고 오른쪽 칸에 같은 틀을 그린다.
 */
@Composable
fun AuthScaffold(
    frame: AuthFrame,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAuthFrame provides frame) {
        val scaffold: @Composable (Modifier) -> Unit = { m ->
            EolssuScaffold(
                modifier = m,
                topBar = topBar,
                bottomBar = if (bottomBar != null && frame.isTablet) tabletBottomBar(bottomBar) else bottomBar,
                snackbarHostState = snackbarHostState,
                contentMaxWidth = frame.contentMaxWidth,
                content = content,
            )
        }
        if (frame == AuthFrame.Split) {
            BoxWithConstraints(modifier.fillMaxSize().background(EolssuTheme.colors.background)) {
                val brandWidth = (maxWidth * BrandPaneRatio).coerceAtMost(560.dp)
                Row(Modifier.fillMaxSize()) {
                    AuthBrandPane(Modifier.width(brandWidth).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) { scaffold(Modifier) }
                }
            }
        } else {
            scaffold(modifier)
        }
    }
}

/** 태블릿 하단 고정 영역: 버튼 묶음을 [AuthTabletColumnWidth]로 가운데에 둔다. */
private fun tabletBottomBar(bar: @Composable ColumnScope.() -> Unit): @Composable ColumnScope.() -> Unit = {
    Column(
        Modifier.align(Alignment.CenterHorizontally).widthIn(max = AuthTabletColumnWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = bar,
    )
}

/**
 * 배치에 맞춘 상단 바. 태블릿 한 단은 뒤로 가기를 화면 왼쪽 40dp(가로는 80dp)에, 브랜드 칸 옆에서는 입력 칸 왼쪽 끝에 맞춘다.
 */
@Composable
fun AuthTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    right: @Composable RowScope.() -> Unit = {},
) {
    when (LocalAuthFrame.current) {
        AuthFrame.Compact -> EolssuTopBar(title, onBack = onBack, subtitle = subtitle, right = right)
        AuthFrame.Split -> EolssuTopBar(title, onBack = onBack, subtitle = subtitle, maxWidth = AuthFrame.Split.contentMaxWidth, right = right)
        AuthFrame.Portrait, AuthFrame.Landscape -> EolssuTopBar(
            title,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp),
            subtitle = subtitle,
            maxWidth = WindowBreakpoints.ContentMaxWidth + 40.dp,
            right = right,
        )
    }
}

/** 브랜드 칸의 한지 바탕·낙관 색. 홈 족자와 같은 값이다(와이어프레임 `.ta-brand`, `.ta-seal`). */
private val BrandPaper = Color(0xFFF7F1E4)
private val BrandEdge = Color(0x1F3C301E)
private val BrandSeal = Color(0xFFB8322A)

/**
 * 가로 배치 왼쪽 브랜드 칸(`.ta-brand`): 한지 바탕에 앱 이름 · 대표 문구 · 한 줄 소개, 오른쪽 위에 낙관.
 * 와이어프레임의 붓글씨 글꼴은 앱에 없어 홈 족자처럼 세리프 제목으로 쓴다.
 */
@Composable
private fun AuthBrandPane(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    BoxWithConstraints(
        modifier
            .background(BrandPaper)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(BrandEdge, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            },
    ) {
        // 560dp 칸에서 왼쪽 80 · 오른쪽 64. 좁은 창에서는 같은 비율로 줄인다.
        val start = (maxWidth * (80f / 560f)).coerceIn(32.dp, 80.dp)
        val end = (maxWidth * (64f / 560f)).coerceIn(24.dp, 64.dp)
        val brand = stringResource(Res.string.auth_brand)
        BrandSealMark(brand, Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 28.dp))
        val viewportHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(start = start, end = end, top = 72.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(brand, style = EolssuTheme.serif.brand, color = colors.text)
            Text(
                stringResource(Res.string.auth_brand_headline),
                Modifier.padding(top = 28.dp),
                style = EolssuTheme.serif.headline.copy(fontSize = 40.sp, lineHeight = 52.sp),
                color = colors.text,
            )
            Text(
                stringResource(Res.string.auth_brand_body),
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = colors.textMuted,
            )
        }
    }
}

/** 붉은 낙관. 세로쓰기가 없어 글자를 한 자씩 세로로 쌓는다. 장식이라 화면 읽기에서 제외한다. */
@Composable
private fun BrandSealMark(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clearAndSetSemantics {}
            .alpha(0.85f)
            .rotate(4f)
            .border(1.5.dp, BrandSeal, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        text.forEach { ch ->
            Text(
                ch.toString(),
                style = TextStyle(
                    fontFamily = EolssuTheme.serif.brand.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = BrandSeal,
                ),
            )
        }
    }
}
