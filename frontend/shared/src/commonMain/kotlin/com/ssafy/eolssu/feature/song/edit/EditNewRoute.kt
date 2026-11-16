package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.feature.song.ConversionNotice
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblem
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_conversion_reimport_needed
import eolssu.shared.generated.resources.mysongs_conversion_retry_failed
import eolssu.shared.generated.resources.sheet_delete_failed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * EDIT-01 연결. 정간보·오선보 카드는 누르는 즉시 [onImport]로 악보 올리기 화면에 가고,
 * '내 악보·제공 곡 불러오기'는 [onLoadExisting]으로 목록(EditPick)에 간다. 뒤로 가기는 내 악보로 돌아간다.
 * 내 악보의 '다시 불러오기'로 왔으면 [reimportProblem] 안내 창을 먼저 띄우고, '다시 선택하기'는 고른 종류로 파일 선택에 간다.
 *
 * 아래 내 악보 줄은 [onOpenSheet]로 열람 또는 연습에, [onEditSheet]로 편집에 간다.
 * 화면이 보이는 동안에만 목록을 새로 받고 변환 중인 악보의 상태를 묻는다.
 */
@Composable
fun EditNewRoute(
    onBack: () -> Unit,
    onImport: (ImportKind, HwangPitch) -> Unit,
    onLoadExisting: () -> Unit,
    reimportProblem: SheetFileProblem? = null,
    onOpenSheet: (MySheet) -> Unit = {},
    onEditSheet: (MySheet) -> Unit = {},
    viewModel: EditNewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.showReimportProblem(reimportProblem) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSheets()
        viewModel.watchConversions()
        onPauseOrDispose { viewModel.stopWatchingConversions() }
    }
    val reimportNeeded = stringResource(Res.string.mysongs_conversion_reimport_needed)
    val retryFailed = stringResource(Res.string.mysongs_conversion_retry_failed)
    LaunchedEffect(state.mine.notice) {
        val notice = state.mine.notice ?: return@LaunchedEffect
        viewModel.consumeConversionNotice()
        snackbarHostState.showSnackbar(if (notice == ConversionNotice.Reimport) reimportNeeded else retryFailed)
    }
    val deleteFailure = stringResource(Res.string.sheet_delete_failed)
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            viewModel.consumeDeleteFailure()
            snackbarHostState.showSnackbar(deleteFailure)
        }
    }
    val openDevicePick = { onImport(state.kind, HwangPitch.Default) }
    EditNewScreen(
        state = state,
        onBack = onBack,
        // 종류는 올리기 화면에서 고르므로 정간보로 열어 둔다.
        onUpload = { onImport(ImportKind.Jeongganbo, HwangPitch.Default) },
        onLoadExisting = onLoadExisting,
        onOpenDevicePick = openDevicePick,
        onDismissProblem = viewModel::dismissProblem,
        sheetActions = EditNewSheetActions(
            onOpen = onOpenSheet,
            onEdit = onEditSheet,
            onDelete = viewModel::askDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onRetryConversion = viewModel::retryConversion,
            onReimport = { sheet -> if (!viewModel.reimport(sheet)) openDevicePick() },
            onRetryLoad = viewModel::refreshSheets,
        ),
        snackbarHostState = snackbarHostState,
    )
}
