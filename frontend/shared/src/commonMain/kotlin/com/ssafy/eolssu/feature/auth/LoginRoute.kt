package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.LaunchedEffect
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_withdrawn
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
    providers: List<SocialProvider>,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 탈퇴 신청 안내는 지나가는 알림 대신 확인 창으로 한 번 띄운다(S15P31E102-768).
    val withdrawn = startMessage?.text == Res.string.my_withdrawn
    LaunchedEffect(withdrawn) {
        if (withdrawn) {
            viewModel.showWithdrawn()
            onStartMessageShown()
        }
    }
    StartMessageEffect(snackbarHostState, startMessage.takeUnless { withdrawn }, onStartMessageShown)

    LoginScreen(
        state = state,
        providers = providers,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onEmailChange = viewModel::changeEmail,
        onPasswordChange = viewModel::changePassword,
        onRememberEmailChange = viewModel::changeRememberEmail,
        onKeepSignedInChange = viewModel::changeKeepSignedIn,
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
