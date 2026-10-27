package com.ssafy.eolssu.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 홈 화면 연결. 'AI 연주 평가 시작'은 [onStartEvaluation]으로 곡 목록에 간다.
 * 연습곡 선택·학습 화면은 아직 없어서 '준비 중' 안내를 띄운다. 화면이 생기면 이동 콜백을 더한다.
 */
@Composable
fun HomeRoute(onStartEvaluation: () -> Unit, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val showComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoon)
        }
    }

    HomeScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onOpenInstrumentSheet = viewModel::openInstrumentSheet,
        onDismissInstrumentSheet = viewModel::dismissInstrumentSheet,
        onSelectInstrument = viewModel::selectInstrument,
        onStartEvaluation = onStartEvaluation,
        onFeatureClick = { showComingSoon() },
    )
}
