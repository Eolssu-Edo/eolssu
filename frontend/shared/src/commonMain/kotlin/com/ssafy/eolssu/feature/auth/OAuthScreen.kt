package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.feature.auth.component.AuthFaint
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_close
import eolssu.shared.generated.resources.auth_confirm
import eolssu.shared.generated.resources.auth_no_account_message
import eolssu.shared.generated.resources.auth_no_account_signup
import eolssu.shared.generated.resources.auth_no_account_title
import eolssu.shared.generated.resources.auth_oauth_title
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_message
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_title
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.social_auth_progress
import eolssu.shared.generated.resources.social_auth_reopen
import eolssu.shared.generated.resources.social_auth_waiting_body
import eolssu.shared.generated.resources.social_auth_waiting_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-05 간편 로그인(로그인 전용). 제공자 화면은 외부 브라우저에 뜨고, 이 화면은 그동안 와이어프레임의 점선 자리에 진행 상태를 보여 준다.
 * 연결된 계정이면 홈, 같은 이메일 계정이면 계정 연결 확인(Route가 이동), 둘 다 없으면 '가입된 계정이 없어요' 창([onSignUp]으로 이메일 가입)이다.
 * 탈퇴 대기 계정이면 이메일 로그인처럼 '탈퇴 신청이 취소됐어요' 창의 확인 뒤 [onLoggedIn]으로 간다.
 */
@Composable
fun OAuthScreen(
    provider: SocialProvider,
    step: OAuthStep,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReopen: () -> Unit,
    onLoggedIn: () -> Unit,
    onSignUp: () -> Unit,
) {
    val name = stringResource(provider.displayName)
    val frame = authFrameOf(widthClass, split = false)
    AuthScaffold(
        frame,
        topBar = { AuthTopBar(stringResource(Res.string.auth_oauth_title, name), onBack = onBack) },
        bottomBar = { ProviderStepButton(step, name, onBack = onBack, onRetry = onRetry, onReopen = onReopen) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 자리 표시는 본문 높이를 채우되 최소 360dp(`.au-placeholder`), 태블릿 세로 560dp · 가로 440dp.
            val minHeight = when (frame) {
                AuthFrame.Compact -> 360.dp
                AuthFrame.Portrait -> 560.dp
                AuthFrame.Landscape, AuthFrame.Split -> 440.dp
            }
            val boxHeight = maxOf(minHeight, maxHeight - AuthBodyTopWithBar - 24.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = AuthBodyTopWithBar, bottom = 24.dp),
            ) {
                ProviderPlaceholder(boxHeight, step, name)
            }
        }
    }
    if (step == OAuthStep.NoAccount) {
        EolssuDialog(
            title = stringResource(Res.string.auth_no_account_title),
            message = stringResource(Res.string.auth_no_account_message, name),
            icon = EolssuIcons.Info,
        ) {
            EolssuButton(stringResource(Res.string.auth_no_account_signup), onClick = onSignUp)
            EolssuButton(stringResource(Res.string.auth_close), onClick = onBack, style = EolssuButtonStyle.Text)
        }
    }
    if (step == OAuthStep.WithdrawalCancelled) {
        EolssuDialog(
            title = stringResource(Res.string.auth_withdrawal_cancelled_title),
            message = stringResource(Res.string.auth_withdrawal_cancelled_message),
            icon = EolssuIcons.Check,
        ) {
            EolssuButton(stringResource(Res.string.auth_confirm), onClick = onLoggedIn)
        }
    }
}

/** 단계별 아래 버튼: 브라우저 진행 중 '다시 열기', 멈춤 '다시 시도'(미지원은 '확인'), 요청 중 진행 표시. */
@Composable
private fun ProviderStepButton(step: OAuthStep, name: String, onBack: () -> Unit, onRetry: () -> Unit, onReopen: () -> Unit) {
    when (step) {
        OAuthStep.InBrowser -> EolssuButton(stringResource(Res.string.social_auth_reopen, name), onClick = onReopen)
        is OAuthStep.Stopped -> if (step.problem.retryable) {
            EolssuButton(stringResource(Res.string.member_api_retry), onClick = onRetry)
        } else {
            EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
        }
        OAuthStep.Starting, OAuthStep.Completing -> EolssuButton(stringResource(Res.string.social_auth_progress), onClick = {}, busy = true)
        else -> Unit
    }
}

/** 제공자 화면 자리(`.au-placeholder`): 점선 둥근 상자 안에 단계 안내를 가운데에 둔다. */
@Composable
private fun ProviderPlaceholder(minHeight: Dp, step: OAuthStep, name: String) {
    val colors = EolssuTheme.colors
    val (title, body) = when (step) {
        is OAuthStep.Stopped -> null to stringResource(step.problem.message, name)
        OAuthStep.Starting, OAuthStep.Completing -> null to stringResource(Res.string.social_auth_progress)
        else -> stringResource(Res.string.social_auth_waiting_title, name) to stringResource(Res.string.social_auth_waiting_body)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = AuthFaint,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            body,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.8.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
