package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** MY-04 연결. 비밀번호로 신청하거나 간편 로그인 재인증으로 신청하면 [onWithdrawn]으로 로그인 화면에 간다. */
@Composable
fun WithdrawRoute(
    onBack: () -> Unit,
    onWithdrawn: () -> Unit,
    viewModel: WithdrawViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    LaunchedEffect(state.withdrawn) { if (state.withdrawn) onWithdrawn() }
    WithdrawScreen(
        state = state,
        profile = profile,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onPasswordChange = viewModel::changePassword,
        onSubmit = { scope.launch { if (viewModel.submit()) onWithdrawn() } },
        onReauth = viewModel::startReauth,
        onReopen = viewModel::reopen,
    )
}
