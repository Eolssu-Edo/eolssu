package com.ssafy.eolssu.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 홈 화면 연결. 'AI 연주 평가 시작'은 [onStartEvaluation]으로 곡 목록에 간다.
 * 연습·학습 카드는 [onOpenFeature]가 화면을 열면 true를 돌려주고, 아직 연결된 화면이 없으면 false라 '준비 중' 안내를 띄운다.
 * [startMessage]는 가입을 마치고 왔을 때처럼 이전 화면이 남긴 안내다.
 */
@Composable
fun HomeRoute(
    onStartEvaluation: () -> Unit,
    onOpenFeature: (HomeFeature) -> Boolean,
    startMessage: StringResource? = null,
    onStartMessageShown: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
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
    StartMessageEffect(snackbarHostState, startMessage, onStartMessageShown)

    HomeScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onOpenInstrumentSheet = viewModel::openInstrumentSheet,
        onDismissInstrumentSheet = viewModel::dismissInstrumentSheet,
        onSelectInstrument = viewModel::selectInstrument,
        onStartEvaluation = onStartEvaluation,
        onFeatureClick = { feature -> if (!onOpenFeature(feature)) showComingSoon() },
    )
}
