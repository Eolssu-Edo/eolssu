package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** MY-03 연결. 바꾸면 [onChanged]로 마이페이지에 돌아간다. */
@Composable
fun PasswordChangeRoute(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: PasswordChangeViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PasswordChangeScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onCurrentChange = viewModel::changeCurrent,
        onNewChange = viewModel::changeNew,
        onConfirmChange = viewModel::changeConfirm,
        onSubmit = { scope.launch { if (viewModel.submit()) onChanged() } },
    )
}
