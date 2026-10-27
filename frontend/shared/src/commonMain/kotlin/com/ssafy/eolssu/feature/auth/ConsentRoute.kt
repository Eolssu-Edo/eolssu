package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** AUTH-01 연결. '보기'는 [onOpenTerms]로 약관 화면, 시작하기는 [onStart]로 마이크 안내 화면에 간다. */
@Composable
fun ConsentRoute(
    onOpenTerms: (Agreement) -> Unit,
    onStart: () -> Unit,
    viewModel: ConsentViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConsentScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onToggle = viewModel::toggle,
        onToggleAll = viewModel::toggleAll,
        onOpenTerms = onOpenTerms,
        onStart = { if (state.canStart) onStart() },
    )
}
