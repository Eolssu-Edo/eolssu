package com.ssafy.eolssu.feature.auth

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.user.VerificationPurpose
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_code_resent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** AUTH-06·AUTH-10 연결. 인증번호가 맞으면 [onVerified]에 확인한 이메일을 넘긴다. 남은 시간은 1초마다 다시 센다. */
@Composable
fun EmailVerificationRoute(
    purpose: VerificationPurpose,
    onBack: () -> Unit,
    onVerified: (email: String) -> Unit,
    viewModel: EmailVerificationViewModel = koinViewModel { parametersOf(purpose) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by produceState(viewModel.currentTime()) {
        while (true) {
            delay(1_000)
            value = viewModel.currentTime()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resent = stringResource(Res.string.auth_code_resent)
    EmailVerificationScreen(
        purpose = purpose,
        state = state,
        now = now,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEmailChange = viewModel::changeEmail,
        onCodeChange = viewModel::changeCode,
        onSendCode = { viewModel.sendCode() },
        onResendCode = {
            if (viewModel.resendCode()) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resent)
                }
            }
        },
        onConfirm = { if (viewModel.confirmCode()) onVerified(viewModel.uiState.value.email) },
    )
}
