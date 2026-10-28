package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-09 연결. [email]은 간편 로그인 409 응답이 알려 준 얼쑤 계정 이메일이다.
 * 연결하면 [onLinked]로 홈, '연결하지 않기'는 [onCancel]로 로그인 화면, 비밀번호 찾기는 [onFindPassword]로 간다.
 */
@Composable
fun AccountLinkRoute(
    provider: SocialProvider,
    email: String,
    needsPassword: Boolean,
    onCancel: () -> Unit,
    onFindPassword: () -> Unit,
    onLinked: () -> Unit,
    viewModel: AccountLinkViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountLinkScreen(
        provider = provider,
        email = email,
        needsPassword = needsPassword,
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onCancel,
        onPasswordChange = viewModel::changePassword,
        onFindPassword = onFindPassword,
        onLink = { if (viewModel.link(needsPassword)) onLinked() },
    )
}
