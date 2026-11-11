package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.user.VerificationPurpose
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-08·AUTH-11 연결. [email]은 앞 단계에서 인증한 이메일이다.
 * 가입이면 [onDone]으로 프로필 설정(4/4), 비밀번호 찾기면 [onDone]으로 로그인 화면에 간다(자동 로그인하지 않음).
 */
@Composable
fun PasswordSetupRoute(
    purpose: VerificationPurpose,
    email: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PasswordSetupViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PasswordSetupScreen(
        purpose = purpose,
        email = email,
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onPasswordChange = viewModel::changePassword,
        onPasswordConfirmChange = viewModel::changePasswordConfirm,
        onSubmit = { scope.launch { if (viewModel.submitToServer(purpose, email)) onDone() } },
    )
}
