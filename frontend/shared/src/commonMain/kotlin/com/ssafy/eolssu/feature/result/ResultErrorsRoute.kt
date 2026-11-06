package com.ssafy.eolssu.feature.result

import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.record_delete_busy
import eolssu.shared.generated.resources.record_delete_failed
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-03 오류 위치 연결. [sourceId]는 평가한 연습 세션 ID다.
 *
 * @param onSelectSection ‘취약 구간 골라서 연습하기’ → PRACTICE-02 취약 구간 선택.
 * @param onDeleted 이전 연주 결과에서 '기록 삭제'를 마친 뒤. 기본은 뒤로 가기(연습 기록 목록).
 */
@Composable
fun ResultErrorsRoute(
    sourceId: String,
    onBack: () -> Unit,
    onSelectSection: () -> Unit,
    historical: Boolean = false,
    onOpenRecording: (() -> Unit)? = null,
    onDeleted: () -> Unit = onBack,
    viewModel: ResultErrorsViewModel = koinViewModel(key = "result-errors-$historical-$sourceId") { parametersOf(sourceId, historical) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteError = when (state.delete) {
        RecordDeleteState.Busy -> stringResource(Res.string.record_delete_busy)
        RecordDeleteState.Failed -> stringResource(Res.string.record_delete_failed)
        else -> null
    }
    val latestOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(state.delete) {
        when {
            state.delete == RecordDeleteState.Deleted -> latestOnDeleted()
            deleteError != null -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(deleteError)
                viewModel.dismissDeleteError()
            }
        }
    }
    ResultErrorsScreen(
        state = state,
        onBack = onBack,
        onSelectFilter = viewModel::selectFilter,
        onSelectCell = viewModel::selectCell,
        onSelectSection = onSelectSection,
        onRetry = viewModel::retry,
        historical = historical,
        onOpenRecording = onOpenRecording,
        snackbarHostState = snackbarHostState,
        deleteActions = if (viewModel.canDelete) {
            RecordDeleteActions(onAsk = viewModel::askDelete, onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        } else {
            null
        },
    )
}
