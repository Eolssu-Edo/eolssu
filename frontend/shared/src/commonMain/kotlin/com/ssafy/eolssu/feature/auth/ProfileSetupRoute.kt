package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-07 연결. [provider]가 있으면 소셜 첫 가입, 없으면 이메일 가입 2/2다.
 * 뒤로 가기는 들어온 화면(AUTH-05 또는 AUTH-06)으로, 가입을 마치면 [onDone]으로 홈에 간다.
 */
@Composable
fun ProfileSetupRoute(
    provider: SocialProvider?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ProfileSetupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileSetupScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        provider = provider,
        onBack = onBack,
        onNicknameChange = viewModel::changeNickname,
        onSelectInstrument = viewModel::selectInstrument,
        onComplete = { if (viewModel.complete()) onDone() },
    )
}
