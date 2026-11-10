package com.ssafy.eolssu.feature.result

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
 * RESULT-02 연결.
 *
 * @param onGoRecords 저장 뒤 '기록 보러 가기'. null이면 기록 화면이 아직 없어 '준비 중' 안내를 띄운다.
 * @param onViewAllErrors ‘오류 위치 상세 보기’ → RESULT-03 오류 위치. null이면 '준비 중' 안내를 띄운다.
 * @param onWeakSection ‘취약 구간 연습’ → PRACTICE-02 취약 구간 선택(2차). null이면 '준비 중' 안내를 띄운다.
 */
@Composable
fun ResultRoute(
    evaluationId: String,
    onBack: () -> Unit,
    onPracticeAgain: (songId: String) -> Unit,
    onOtherSong: () -> Unit,
    onHome: () -> Unit,
    onGoRecords: ((recordId: String) -> Unit)?,
    onViewAllErrors: (() -> Unit)? = null,
    onWeakSection: (() -> Unit)? = null,
    initialSong: Song? = null,
    viewModel: ResultViewModel = koinViewModel(key = evaluationId) { parametersOf(evaluationId, initialSong) },
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
    ResultScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = ResultActions(
            onBack = onBack,
            onSave = viewModel::save,
            onViewAllErrors = onViewAllErrors ?: showComingSoon,
            onWeakSection = onWeakSection ?: showComingSoon,
            onPracticeAgain = { (state.result?.songId ?: state.scorePreview?.songId)?.let(onPracticeAgain) },
            onOtherSong = onOtherSong,
            onHome = onHome,
            onMemoChange = viewModel::changeMemo,
            onCloseSheet = viewModel::closeSheet,
            onGoRecords = {
                val recordId = state.savedRecord?.id
                if (onGoRecords != null && recordId != null) {
                    viewModel.closeSheet()
                    onGoRecords(recordId)
                } else {
                    viewModel.closeSheet()
                    showComingSoon()
                }
            },
            onRetrySong = viewModel::retrySong,
            onRetryResult = viewModel::retryResult,
        ),
    )
}
