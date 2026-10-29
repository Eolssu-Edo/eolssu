package com.ssafy.eolssu.feature.auth

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-04 연결. 로그인에 성공하면 [onLoggedIn], 간편 로그인 버튼은 [onSocialLogIn], 비밀번호 찾기는 [onFindPassword],
 * 회원가입은 [onSignUp]으로 간다. 탈퇴가 취소됐으면 안내 창에서 확인을 누른 뒤 [onLoggedIn]으로 간다.
 * [startMessage]는 마이크를 허용하지 않고 왔을 때처럼 이전 화면이 남긴 안내다.
 */
@Composable
fun LoginRoute(
    startMessage: StartMessage?,
    onStartMessageShown: () -> Unit,
    onLoggedIn: () -> Unit,
    onSocialLogIn: (SocialProvider) -> Unit,
    onFindPassword: () -> Unit,
    onSignUp: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    StartMessageEffect(snackbarHostState, startMessage, onStartMessageShown)

    LoginScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onEmailChange = viewModel::changeEmail,
        onPasswordChange = viewModel::changePassword,
        onLogIn = { scope.launch { if (viewModel.logIn()) onLoggedIn() } },
        onSocialLogIn = onSocialLogIn,
        onFindPassword = {
            viewModel.dismissNotice()
            onFindPassword()
        },
        onSignUp = onSignUp,
        onDismissNotice = viewModel::dismissNotice,
        onWithdrawalCancelledConfirmed = onLoggedIn,
    )
}
