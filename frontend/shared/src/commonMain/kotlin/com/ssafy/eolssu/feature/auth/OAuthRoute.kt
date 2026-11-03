package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * AUTH-05 연결. 로그인하면 [onLoggedIn]으로 홈, 같은 이메일 계정이 있으면 [onLinkAvailable]로 계정 연결 확인(AUTH-09),
 * 처음 온 제공자 계정이면 [onSocialSignUp]으로 소셜 가입, '가입된 계정이 없어요' 창(소셜 가입 전 서버)의 이메일 가입은 [onSignUp],
 * 뒤로 가기·닫기는 [onBack]으로 간다.
 */
@Composable
fun OAuthRoute(
    provider: SocialProvider,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onLinkAvailable: () -> Unit,
    onSocialSignUp: () -> Unit,
    onSignUp: () -> Unit,
    viewModel: OAuthViewModel = koinViewModel { parametersOf(provider) },
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    LaunchedEffect(step) {
        when (step) {
            OAuthStep.LoggedIn -> onLoggedIn()
            OAuthStep.MergeAvailable -> onLinkAvailable()
            OAuthStep.SignUpRequired -> onSocialSignUp()
            else -> Unit
        }
    }
    OAuthScreen(
        provider = provider,
        step = step,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onRetry = viewModel::start,
        onReopen = viewModel::reopen,
        onLoggedIn = onLoggedIn,
        onSignUp = onSignUp,
    )
}
