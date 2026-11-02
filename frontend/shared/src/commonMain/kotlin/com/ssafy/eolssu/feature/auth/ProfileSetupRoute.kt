package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * AUTH-07 연결. 이메일 가입(3/3)은 뒤로 가기로 비밀번호 설정(AUTH-08)에, 간편 로그인 가입([social])은 앞 화면에 돌아간다.
 * 가입을 마치면 [onDone]으로 홈에 간다.
 */
@Composable
fun ProfileSetupRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    social: Boolean = false,
    viewModel: ProfileSetupViewModel = koinViewModel { parametersOf(social) },
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
        social = social,
    )
}
