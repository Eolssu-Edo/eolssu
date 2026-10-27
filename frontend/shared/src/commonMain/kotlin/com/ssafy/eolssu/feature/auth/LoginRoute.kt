package com.ssafy.eolssu.feature.auth

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_find_password_notice
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-04 연결. 로그인에 성공하면 [onLoggedIn], 소셜 버튼은 [onSocialLogIn], 회원가입은 [onSignUp]으로 간다.
 * [startMessage]는 마이크를 허용하지 않고 왔을 때처럼 이전 화면이 남긴 안내다.
 */
@Composable
fun LoginRoute(
    startMessage: StringResource?,
    onStartMessageShown: () -> Unit,
    onLoggedIn: () -> Unit,
    onSocialLogIn: (SocialProvider) -> Unit,
    onSignUp: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val findPasswordNotice = stringResource(Res.string.auth_find_password_notice)
    StartMessageEffect(snackbarHostState, startMessage, onStartMessageShown)

    LoginScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEmailChange = viewModel::changeEmail,
        onPasswordChange = viewModel::changePassword,
        onLogIn = { if (viewModel.logIn()) onLoggedIn() },
        onSocialLogIn = onSocialLogIn,
        onFindPassword = {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(findPasswordNotice)
            }
        },
        onSignUp = onSignUp,
    )
}
