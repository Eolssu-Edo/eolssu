package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/**
 * AUTH-01 연결(회원가입 첫 단계). '보기'는 [onOpenTerms]로 약관 상세, 동의 버튼은 필수 약관을 모두 골랐을 때만 [onStart]로 다음 가입 단계에 간다.
 * [onBack]은 상단 바 뒤로 가기다. [path]는 동의 뒤 이어질 가입 경로로, 상단 단계 표시의 단계 수를 정한다.
 */
@Composable
fun ConsentRoute(
    path: SignupPath,
    onBack: () -> Unit,
    onOpenTerms: (String) -> Unit,
    onStart: () -> Unit,
    viewModel: ConsentViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConsentScreen(
        state = state,
        path = path,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onToggleAll = viewModel::toggleAll,
        onOpenTerms = onOpenTerms,
        onStart = { if (viewModel.accept()) onStart() },
        onRetry = { scope.launch { viewModel.load(force = true) } },
    )
}
