package com.ssafy.eolssu.feature.result

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.backhandler.BackHandler
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
 *
 * @param onConfirmBackChange 저장하지 않은 결과라 떠나기 전에 확인할 때 화면의 뒤로 가기 동작을, 아니면 null을 알린다.
 *   Web 브라우저 뒤로 가기처럼 [BackHandler]를 거치지 않는 뒤로 가기를 화면 밖에서 가로챌 때 쓴다(S15P31E102-1214).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ResultRoute(
    evaluationId: String,
    onBack: () -> Unit,
    onPracticeAgain: (songId: String) -> Unit,
    onOtherSong: () -> Unit,
    onHome: () -> Unit,
    onOpenRecord: (recordId: String) -> Unit = {},
    onConfirmBackChange: ((() -> Unit)?) -> Unit = {},
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
    // 저장하지 않은 결과면 기기 뒤로 가기·Web 브라우저 뒤로 가기에 ‘연주 결과를 버릴까요?’를 띄운다(S15P31E102-1214).
    // 저장을 마쳤거나 결과를 못 받았으면 가로채지 않아 전처럼 바로 나간다. 창이 열려 있으면 창이 뒤로 가기를 먼저 받는다.
    val leave = { if (viewModel.requestLeave()) onBack() }
    @Suppress("DEPRECATION") // ui-backhandler 1.12의 BackHandler. 대신할 NavigationEventHandler는 아직 의존성에 없다.
    BackHandler(enabled = state.confirmsLeave && !state.leaveWarning) { leave() }
    val currentLeave by rememberUpdatedState(leave)
    val currentOnConfirmBackChange by rememberUpdatedState(onConfirmBackChange)
    DisposableEffect(state.confirmsLeave) {
        currentOnConfirmBackChange(if (state.confirmsLeave) { { currentLeave() } } else null)
        onDispose { currentOnConfirmBackChange(null) }
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
                onKeepResult = viewModel::dismissLeaveWarning,
                onDiscardResult = {
                    viewModel.dismissLeaveWarning()
                    onBack()
                },
                // ‘기록으로 이동’: 시트를 닫고 방금 저장한 기록 상세로 간다(S15P31E102-815).
                onOpenRecord = {
                    state.savedRecord?.id?.let { id ->
                        viewModel.closeSheet()
                        onOpenRecord(id)
                    }
                },
            ),
        )
    }
}
