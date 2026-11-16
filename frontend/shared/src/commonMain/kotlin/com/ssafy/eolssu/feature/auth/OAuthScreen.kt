package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.ProviderWaiting
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
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
 * AUTH-05 간편 로그인. 제공자 화면은 외부 브라우저에 뜨고, 이 화면은 그동안 제공자 표시와 진행 상태([ProviderWaiting])를 보여 준다.
 * 연결된 계정이면 홈, 같은 이메일 계정이면 계정 연결 확인, 처음 온 계정이면 소셜 가입(둘 다 Route가 이동)이다.
 * 소셜 가입 전 서버가 '가입된 계정 없음'을 주면 '가입된 계정이 없어요' 창([onSignUp]으로 이메일 가입)을 띄운다.
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
            // 본문 높이 가운데에 두고, 글자를 키워 넘치면 스크롤한다.
            val bodyHeight = maxHeight - AuthBodyTopWithBar - 24.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = AuthBodyTopWithBar, bottom = 24.dp),
            ) {
                ProviderStatus(provider, step, name, Modifier.heightIn(min = bodyHeight))
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
            // 바깥 누르기·뒤로 가기로 닫아도 '확인'과 같이 로그인을 이어 간다(S15P31E102-969).
            onDismissRequest = onLoggedIn,
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

/** 단계별 안내: 진행 중이면 '제공자 화면에서 계속해 주세요'·'확인하고 있어요', 멈추면 이유. */
@Composable
private fun ProviderStatus(provider: SocialProvider, step: OAuthStep, name: String, modifier: Modifier) {
    val (title, body) = when (step) {
        is OAuthStep.Stopped -> null to stringResource(step.problem.message, name)
        OAuthStep.Starting, OAuthStep.Completing -> null to stringResource(Res.string.social_auth_progress)
        else -> stringResource(Res.string.social_auth_waiting_title, name) to stringResource(Res.string.social_auth_waiting_body)
    }
    ProviderWaiting(provider, title, body, busy = step !is OAuthStep.Stopped, modifier = modifier)
}
