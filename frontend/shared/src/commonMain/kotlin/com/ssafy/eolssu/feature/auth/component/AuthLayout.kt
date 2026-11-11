package com.ssafy.eolssu.feature.auth.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_step
import org.jetbrains.compose.resources.stringResource

/** 상단 바가 없는 화면의 본문 위 여백. 와이어프레임 64px에서 상태 표시줄 자리 44px를 뺀 값. */
val AuthBodyTopWithoutBar: Dp = 20.dp

/** 상단 바 아래 본문 위 여백(`.au-body`). */
val AuthBodyTopWithBar: Dp = 12.dp

/**
 * 로그인·가입 화면 본문(`.body.au-body`): 좌우 20dp, 세로 스크롤, 항목 사이 [gap], 끝 여백 24dp.
 * 본문은 최소한 화면 높이만큼 늘어나므로 `Modifier.weight`로 남는 높이를 나눌 수 있다(`margin-top: auto`, `flex: 1`).
 * [verticalAlignment]를 가운데로 주면 내용이 남은 높이의 가운데에 온다(태블릿 상단 바 없는 화면).
 */
@Composable
fun AuthBody(
    topPadding: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 20.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap, verticalAlignment),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** 세리프 큰 제목(`.au-title`, 24sp · 줄 높이 1.4). 태블릿은 28sp(`.tabapp.auth .au-title`). */
@Composable
fun AuthHeading(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    val tablet = LocalAuthFrame.current.isTablet
    Text(
        text,
        modifier = modifier,
        style = EolssuTheme.serif.headline.copy(fontSize = if (tablet) 28.sp else 24.sp, lineHeight = if (tablet) 39.2.sp else 33.6.sp),
        color = EolssuTheme.colors.text,
        textAlign = textAlign,
    )
}

/** 제목 아래 회색 설명(`.au-lead`, 14sp · 줄 높이 1.55, #5C564F). */
@Composable
fun AuthLead(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.7.sp),
        color = EolssuTheme.colors.textSub,
        textAlign = textAlign,
    )
}

/** 작은 회색 도움말(`.au-help`, 12.5sp). */
@Composable
fun AuthHelp(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = EolssuTheme.colors.textMuted,
        textAlign = textAlign,
    )
}

/** 보드의 옅은 회색 선(`--faint`, #B9B3A6): 체크 상자·빈 자리 점선. 테마에 없어 로그인·가입 화면에서만 쓴다. */
internal val AuthFaint = Color(0xFFB9B3A6)

/** 상단 바 오른쪽 단계 표시(`.au-step`, 예: 1 / 2). */
@Composable
fun AuthStepLabel(step: Int, total: Int) {
    Text(
        stringResource(Res.string.auth_step, step, total),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = EolssuTheme.colors.textMuted,
    )
}

/** 22dp 체크 상자(`.au-box`). 누르기는 감싼 행이 맡는다. */
@Composable
fun AuthCheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(22.dp)
            .background(if (checked) colors.primary else colors.surface, shape)
            .border(1.5.dp, if (checked) colors.primary else AuthFaint, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            // 와이어프레임 체크 아이콘(24 격자, 선 굵기 2.6)을 14dp로 그린다.
            Canvas(Modifier.size(14.dp)) {
                val unit = size.width / 24f
                val path = Path().apply {
                    moveTo(5f * unit, 12f * unit)
                    lineTo(10f * unit, 17f * unit)
                    lineTo(19f * unit, 6f * unit)
                }
                drawPath(path, colors.onPrimary, style = Stroke(width = 2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** 단계 막대 아래 본문 위 여백. 태블릿은 24dp(`.tabapp.auth .au-progress + .body`). */
@Composable
internal fun authBodyTopBelowProgress(): Dp = if (LocalAuthFrame.current.isTablet) 24.dp else AuthBodyTopWithBar

/** 상단 바 없는 화면의 본문 위 여백. 태블릿 세로 40dp, 브랜드 칸 옆 28dp(`.tabapp.auth .screen > .au-body:first-child`). */
@Composable
internal fun authBodyTopWithoutBar(): Dp = when (LocalAuthFrame.current) {
    AuthFrame.Compact -> AuthBodyTopWithoutBar
    AuthFrame.Split -> 28.dp
    AuthFrame.Portrait, AuthFrame.Landscape -> 40.dp
}
