package com.ssafy.eolssu.feature.result

import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.record_save_failed
import eolssu.shared.generated.resources.record_save_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ssafy.eolssu.core.model.Song

/**
 * 연주 결과(RESULT-02·03) 연결. 취약 구간 연습은 연습 기록의 ‘구간 연습’에서만 들어간다(S15P31E102-794).
 */
@Composable
fun ResultRoute(
    evaluationId: String,
    onBack: () -> Unit,
    onPracticeAgain: (songId: String) -> Unit,
    onOtherSong: () -> Unit,
    onHome: () -> Unit,
    initialSong: Song? = null,
    perf: PerformanceVisit? = null,
    viewModel: ResultViewModel = koinViewModel(key = evaluationId) { parametersOf(evaluationId, initialSong, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveErrorMessage = when (state.recordSaveError) {
        RecordSaveError.Unavailable -> stringResource(Res.string.record_save_unavailable)
        RecordSaveError.Failed -> stringResource(Res.string.record_save_failed)
        null -> null
    }
    LaunchedEffect(state.recordSaveError) {
        if (saveErrorMessage != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(saveErrorMessage)
            viewModel.dismissRecordSaveError()
        }
    }
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val showComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoon)
        }
    }
    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        ResultScreen(
            state = state,
            widthClass = currentWindowLayout().widthClass,
            snackbarHostState = snackbarHostState,
            actions = ResultActions(
                onBack = onBack,
                onSave = viewModel::save,
                onConfirmSave = viewModel::confirmSave,
                onPracticeAgain = { (state.result?.songId ?: state.scorePreview?.songId)?.let(onPracticeAgain) },
                onOtherSong = onOtherSong,
                onHome = onHome,
                onMemoChange = viewModel::changeMemo,
                onCloseSheet = viewModel::closeSheet,
                onSelectFilter = viewModel::selectFilter,
                onRetrySong = viewModel::retrySong,
                onRetryResult = viewModel::retryResult,
            ),
        )
    }
}
