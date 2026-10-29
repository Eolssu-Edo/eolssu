package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** MY-04 연결. 신청하면 [onWithdrawn]으로 로그인 화면에 간다. */
@Composable
fun WithdrawRoute(
    onBack: () -> Unit,
    onWithdrawn: () -> Unit,
    viewModel: WithdrawViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WithdrawScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onPasswordChange = viewModel::changePassword,
        onSubmit = { if (viewModel.submit()) onWithdrawn() },
    )
}
