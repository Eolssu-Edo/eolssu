package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** AUTH-07 연결(이메일 가입 3/3). 뒤로 가기는 비밀번호 설정(AUTH-08)으로, 가입을 마치면 [onDone]으로 홈에 간다. */
@Composable
fun ProfileSetupRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ProfileSetupViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileSetupScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onNicknameChange = viewModel::changeNickname,
        onSelectInstrument = viewModel::selectInstrument,
        onComplete = { scope.launch { if (viewModel.complete()) onDone() } },
    )
}
