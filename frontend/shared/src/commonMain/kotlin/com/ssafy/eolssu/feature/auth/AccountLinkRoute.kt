package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-09 연결. 연결하면 [onLinked]로 연결한 제공자를 넘기고 홈, '연결하지 않기'·뒤로 가기·연결 시간 만료 안내의 확인은 [onCancel]로
 * 로그인 화면, 비밀번호 찾기는 [onFindPassword]로 간다.
 */
@Composable
fun AccountLinkRoute(
    onCancel: () -> Unit,
    onFindPassword: () -> Unit,
    onLinked: (SocialProvider) -> Unit,
    viewModel: AccountLinkViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val cancel = {
        viewModel.cancel()
        onCancel()
    }
    AccountLinkScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = cancel,
        onPasswordChange = viewModel::changePassword,
        onFindPassword = onFindPassword,
        onLink = { scope.launch { if (viewModel.link()) onLinked(state.provider) } },
    )
}
