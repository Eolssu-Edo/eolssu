package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** AUTH-06 연결. 입력이 맞으면 [onNext]로 프로필 설정(2/2)에 간다. */
@Composable
fun SignupRoute(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: SignupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignupScreen(
        state = state,
        onBack = onBack,
        onEmailChange = viewModel::changeEmail,
        onPasswordChange = viewModel::changePassword,
        onPasswordConfirmChange = viewModel::changePasswordConfirm,
        onNext = { if (viewModel.next()) onNext() },
    )
}
