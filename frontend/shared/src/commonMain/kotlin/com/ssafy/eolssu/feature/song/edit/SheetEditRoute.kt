package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import eolssu.shared.generated.resources.sheetedit_msg_saved_for_practice
import eolssu.shared.generated.resources.sheetedit_msg_saved_needs_review
import eolssu.shared.generated.resources.sheet_save_unfilled
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-02 · 03 연결. [viewModel]은 편집 화면 경로에 묶인 것을 받아 미리보기(EDIT-04)와 같은 악보를 쓴다.
 * 저장은 제목·빠르기 대화상자에서 바로 처리한다. 별도의 미리듣기는 [onPreview]로 연다.
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
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = state.draft.original != null && !state.closeWarning && !detailsOpen && !saveOpen && !state.confirming) { viewModel.closeEditor(onClose) }
    if (state.documentLoading || state.documentFailed) {
        SheetDocumentNotice(state.documentLoading, onClose, viewModel::loadDocument)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = mapOf(
        SheetEditMessage.SaveFailed to stringResource(Res.string.sheet_save_failed),
        SheetEditMessage.SaveConflict to stringResource(Res.string.sheet_save_conflict),
        SheetEditMessage.SaveTooLarge to stringResource(Res.string.sheet_save_too_large),
        SheetEditMessage.Draft to stringResource(Res.string.sheetedit_msg_draft),
        SheetEditMessage.Saved to stringResource(Res.string.sheetedit_msg_saved),
        SheetEditMessage.SavedForPractice to stringResource(Res.string.sheetedit_msg_saved_for_practice),
        SheetEditMessage.SavedNeedsReview to stringResource(Res.string.sheetedit_msg_saved_needs_review),
        SheetEditMessage.ComingSoon to stringResource(Res.string.common_coming_soon),
        SheetEditMessage.ReviewBeforeReshape to stringResource(Res.string.sheet_review_before_reshape),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_msg_audio_failed),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
    )
    LaunchedEffect(state.message) {
        val message = state.message?.takeIf { it.shownInEditor() } ?: return@LaunchedEffect
        val text = messages[message] ?: return@LaunchedEffect
        if (message in setOf(SheetEditMessage.Saved, SheetEditMessage.SavedForPractice, SheetEditMessage.SavedNeedsReview)) saveOpen = false
        viewModel.consumeMessage()
        if (saveOpen && message in setOf(SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge)) {
            saveError = text
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    SheetEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = SheetEditActions(
            onClose = { viewModel.closeEditor(onClose) },
            onPreview = onPreview,
            onSave = { saveError = null; saveOpen = true },
            onOpenDetails = { detailsOpen = true },
            onOpenChecks = onOpenChecks,
            onOpenReviewCell = viewModel::openNextReviewCell,
            onCheckReviewIssue = viewModel::checkReviewIssue,
            onFixCompletionIssue = viewModel::openCompletionIssue,
            onClearJangdan = viewModel::clearJangdan,
            onRetryCompletionCatalog = viewModel::retryCompletionCatalog,
            onSelectCell = viewModel::selectCell,
            onSelectUnit = viewModel::selectUnit,
            input = EditInputActions(
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSelectTab = viewModel::selectTab,
                onPut = viewModel::put,
                onPutYul = { viewModel.putYul(it) },
                onRaiseRegister = viewModel::raiseRegister,
                onLowerRegister = viewModel::lowerRegister,
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
    if (saveOpen) SheetEditDetailsDialog(
        initialTitle = state.draft.title,
        initialBpm = state.draft.bpm,
        saveMode = true,
        saving = state.confirming,
        canApply = !state.saving && !state.saveConflict && state.completionValidation.canComplete,
        durationSeconds = state.draft::estimatedDurationSeconds,
        onApply = { title, bpm ->
            saveError = null
            viewModel.updateDetails(title, bpm)
            viewModel.requestSave()
        },
        onDismiss = { saveOpen = false },
        notice = {
            SheetCompletionNotice(
                state,
                onFixCells = { saveOpen = false; viewModel.openCompletionIssue() },
                onChangeJangdan = { saveOpen = false; viewModel.changeJangdan() },
                onClearJangdan = viewModel::clearJangdan,
                onRetryCatalog = viewModel::retryCompletionCatalog,
            )
            val error = saveError ?: when {
                state.saveConflict -> messages[SheetEditMessage.SaveConflict]
                state.emptyWarning -> stringResource(Res.string.sheet_save_unfilled)
                else -> null
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        },
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
        SheetEditJangdanDialog(picker, state.draft.jangdanId, state.draft,
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
