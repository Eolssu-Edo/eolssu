package com.ssafy.eolssu.feature.result

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuDoneDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.learn_tour_done_body_song
import eolssu.shared.generated.resources.learn_tour_done_next
import eolssu.shared.generated.resources.learn_tour_done_title
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
 * @param onLearnNextStage 기초부터 배우기 8단계 통과 창의 '다음 단계로'. 통과한 트랙(`GuideInstrument.id`, 모르면 null)을 받는다.
 */
@Composable
fun ResultRoute(
    evaluationId: String,
    onBack: () -> Unit,
    onPracticeAgain: () -> Unit,
    onOtherSong: () -> Unit,
    onHome: () -> Unit,
    onGoRecords: ((recordId: String) -> Unit)?,
    onViewAllErrors: (() -> Unit)? = null,
    onWeakSection: (() -> Unit)? = null,
    onLearnNextStage: ((track: String?) -> Unit)? = null,
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
    // 곡 전체 결과로 기초부터 배우기 8단계를 통과했으면 따라 배우기와 같은 '완료했어요!'를 한 번 띄운다(S15P31E102-611·626).
    // 완료 표시는 이미 남겼으므로 '다음 단계로'는 그 악기의 다음 단계 상세를 연다.
    if (state.learnSongPassed) {
        EolssuDoneDialog(
            title = stringResource(Res.string.learn_tour_done_title),
            body = stringResource(Res.string.learn_tour_done_body_song),
            action = stringResource(Res.string.learn_tour_done_next),
            onDismiss = viewModel::dismissLearnSongPassed,
            onAction = { onLearnNextStage?.invoke(state.learnSongTrack) },
        )
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
            onPracticeAgain = onPracticeAgain,
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
