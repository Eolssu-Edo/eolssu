package com.ssafy.eolssu.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 홈 화면 연결. 곡 목록·연습곡 선택·학습 화면이 아직 없어서 해당 버튼은 '준비 중' 안내를 띄운다.
 * 화면이 생기면 `app/navigation`에서 이동 콜백을 넘겨 받도록 바꾼다.
 */
@Composable
fun HomeRoute(viewModel: HomeViewModel = koinViewModel()) {
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
        snackbarHostState = snackbarHostState,
        onOpenInstrumentSheet = viewModel::openInstrumentSheet,
        onDismissInstrumentSheet = viewModel::dismissInstrumentSheet,
        onSelectInstrument = viewModel::selectInstrument,
        onStartEvaluation = showComingSoon,
        onFeatureClick = { showComingSoon() },
    )
}
