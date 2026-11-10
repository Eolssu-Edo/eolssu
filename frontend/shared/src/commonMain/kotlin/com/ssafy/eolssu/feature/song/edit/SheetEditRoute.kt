package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.backhandler.BackHandler
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.feature.song.edit.component.EditInputActions
import com.ssafy.eolssu.feature.song.edit.component.SheetEditDetailsDialog
import com.ssafy.eolssu.feature.song.edit.component.SheetEditJangdanDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.sheetedit_msg_audio_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_too_large
import eolssu.shared.generated.resources.sheet_close_unsaved
import eolssu.shared.generated.resources.sheet_close_message
import eolssu.shared.generated.resources.sheet_discard_close
import eolssu.shared.generated.resources.sheet_keep_editing
import eolssu.shared.generated.resources.sheet_review_before_reshape
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-02 · 03 연결. [viewModel]은 편집 화면 경로에 묶인 것을 받아 미리보기(EDIT-04)와 같은 악보를 쓴다.
 * 닫기는 [onClose]로 내 악보에, '미리보기'는 [onPreview]로, 인식 결과에서 왔으면 '확인 정간 보기'가 [onOpenChecks]로 인식 결과(EDIT-06)에 간다.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetEditRoute(
    viewModel: SheetEditViewModel,
    onClose: () -> Unit,
    onPreview: () -> Unit,
    onOpenChecks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.draft.original != null && !state.closeWarning && !detailsOpen) { viewModel.closeEditor(onClose) }
    if (state.documentLoading || state.documentFailed) {
        SheetDocumentNotice(state.documentLoading, onClose, viewModel::loadDocument)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = mapOf(
        SheetEditMessage.SaveFailed to stringResource(Res.string.sheet_save_failed),
        SheetEditMessage.SaveConflict to stringResource(Res.string.sheet_save_conflict),
        SheetEditMessage.SaveTooLarge to stringResource(Res.string.sheet_save_too_large),
        SheetEditMessage.ComingSoon to stringResource(Res.string.common_coming_soon),
        SheetEditMessage.ReviewBeforeReshape to stringResource(Res.string.sheet_review_before_reshape),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_msg_audio_failed),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
    )
    // 미리보기의 저장 안내는 미리보기 화면이 띄운다. 편집기는 편집기에서 생긴 안내와, 뒤에서 만들던 음원의 완료·실패 알림만 띄운다.
    LaunchedEffect(state.message) {
        val text = state.message?.takeIf { it.shownInEditor() }?.let(messages::get) ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    SheetEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = SheetEditActions(
            onClose = { viewModel.closeEditor(onClose) },
            onPreview = onPreview,
            onOpenDetails = { detailsOpen = true },
            onOpenChecks = onOpenChecks,
            onOpenReviewCell = viewModel::openNextReviewCell,
            onCheckReviewIssue = viewModel::checkReviewIssue,
            onSelectView = viewModel::selectView,
            onSelectCell = viewModel::selectCell,
            onSelectUnit = viewModel::selectUnit,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onSelectStaffNote = viewModel::selectStaffNote,
            onShiftStaffNote = viewModel::shiftStaffNote,
            input = EditInputActions(
                onSelectTab = viewModel::selectTab,
                onPut = viewModel::put,
                onPutYul = { viewModel.putYul(it) },
                onPutYulAt = { base, octave -> viewModel.putYul(base, octave) },
                onRaiseRegister = viewModel::raiseRegister,
                onLowerRegister = viewModel::lowerRegister,
                onResetRegister = viewModel::resetRegister,
                onErase = viewModel::erase,
                onToggleSigimsae = viewModel::toggleSigimsae,
                onRemoveSigimsae = viewModel::removeSigimsae,
                onSplit = viewModel::split,
                onMerge = viewModel::merge,
                onAddColumn = viewModel::addColumn,
                onDeleteColumn = viewModel::deleteColumn,
                onChangeJangdan = viewModel::changeJangdan,
            ),
        ),
    )
    if (detailsOpen && state.canEditDetails) SheetEditDetailsDialog(
        initialTitle = state.draft.title,
        initialBpm = state.draft.bpm,
        onApply = { title, bpm ->
            viewModel.updateDetails(title, bpm)
            detailsOpen = false
        },
        onDismiss = { detailsOpen = false },
    )
    state.jangdanPicker?.let { picker ->
        SheetEditJangdanDialog(picker, state.draft.jangdanId, state.draft.original?.music?.bpmUnit,
            onSelect = viewModel::selectJangdan, onReload = viewModel::changeJangdan, onDismiss = viewModel::closeJangdanPicker)
    }
    if (state.closeWarning) EolssuDialog(
        title = stringResource(Res.string.sheet_close_unsaved),
        message = stringResource(Res.string.sheet_close_message),
        onDismissRequest = viewModel::continueEditing,
    ) {
        EolssuButton(stringResource(Res.string.sheet_keep_editing), viewModel::continueEditing)
        EolssuButton(stringResource(Res.string.sheet_discard_close), { viewModel.discardChangesAndClose(onClose) }, style = EolssuButtonStyle.Secondary)
    }
}
