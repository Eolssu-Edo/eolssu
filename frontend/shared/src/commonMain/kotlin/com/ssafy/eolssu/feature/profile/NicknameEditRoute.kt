package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** MY-02 연결. 저장하면 [onSaved]로 마이페이지에 돌아간다. */
@Composable
fun NicknameEditRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: NicknameEditViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NicknameEditScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onNicknameChange = viewModel::changeNickname,
        onSave = { if (viewModel.save()) onSaved() },
    )
}
