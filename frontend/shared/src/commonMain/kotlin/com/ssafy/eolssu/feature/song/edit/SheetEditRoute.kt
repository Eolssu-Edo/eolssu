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
import com.ssafy.eolssu.feature.song.edit.component.SheetEditSettingsDialog
import com.ssafy.eolssu.feature.song.edit.component.SheetEditJangdanDialog
import com.ssafy.eolssu.feature.song.edit.component.SheetPitchCorrectionDialog
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
import eolssu.shared.generated.resources.sheet_editor_correction_title
import eolssu.shared.generated.resources.sheet_editor_correct_cell
import eolssu.shared.generated.resources.sheet_editor_correction_location
import eolssu.shared.generated.resources.sheet_review_checked
import eolssu.shared.generated.resources.sheet_review_missing_pitch
import eolssu.shared.generated.resources.sheet_review_pitch_label
import eolssu.shared.generated.resources.sheet_review_range
import eolssu.shared.generated.resources.sheet_review_uncertain
import eolssu.shared.generated.resources.sheet_pitch_corrected_review
import org.jetbrains.compose.resources.stringResource

/**
 * 저장 시에만 수정할 정간과 해결 방법을 표시한다. 제목은 저장 창, 빠르기·장단은 편집 창에서 바꾼다.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetEditRoute(
    viewModel: SheetEditViewModel,
    onClose: () -> Unit,
    onOpenChecks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var correctionOpen by rememberSaveable { mutableStateOf(false) }
    var pitchCorrection by remember { mutableStateOf<SheetPitchCorrection?>(null) }
    var focusRequestKey by remember { mutableStateOf(0) }
    var saveError by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = state.draft.original != null && !state.closeWarning && !detailsOpen && !saveOpen && !correctionOpen && pitchCorrection == null && !state.confirming) { viewModel.closeEditor(onClose) }
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
        focusRequestKey = focusRequestKey,
        actions = SheetEditActions(
            onClose = { viewModel.closeEditor(onClose) },
            onSave = {
                saveError = null
                if (state.completionValidation.correctionCellIndices.isNotEmpty()) {
                    viewModel.openCompletionIssue()
                    focusRequestKey++
                    val current = viewModel.uiState.value
                    pitchCorrection = current.pitchCorrectionsAt(current.selected).firstOrNull()
                    correctionOpen = pitchCorrection == null
                } else saveOpen = true
            },
            onOpenDetails = { detailsOpen = true },
            onSelectCell = { index ->
                viewModel.selectCell(index)
                pitchCorrection = viewModel.uiState.value.pitchCorrectionsAt(index).firstOrNull()
            },
            onSelectUnit = { index, sub ->
                viewModel.selectUnit(index, sub)
                pitchCorrection = viewModel.uiState.value.pitchCorrectionsAt(index, sub).firstOrNull()
            },
            input = EditInputActions(
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSelectTab = viewModel::selectTab,
                onPut = viewModel::put,
                onPutYul = { viewModel.putYul(it) },
                onSelectRegister = viewModel::selectRegister,
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
    pitchCorrection?.let { target ->
        if (target in state.pitchCorrectionsAt(target.cell, target.sub)) SheetPitchCorrectionDialog(
            state = state,
            target = target,
            onSelect = { pitch ->
                viewModel.applyPitchCorrection(target, pitch)
                pitchCorrection = null
                correctionOpen = false
                focusRequestKey++
            },
            onDismiss = { pitchCorrection = null },
        ) else LaunchedEffect(target) { pitchCorrection = null }
    }
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
                onFixCells = { saveOpen = false; viewModel.openCompletionIssue(); focusRequestKey++ },
                onChangeJangdan = { saveOpen = false; detailsOpen = true },
                onClearJangdan = viewModel::clearJangdan,
                onRetryCatalog = viewModel::retryCompletionCatalog,
            )
            SheetConversionNotes(state.reviewNotices)
            val error = saveError ?: when {
                state.saveConflict -> messages[SheetEditMessage.SaveConflict]
                state.emptyWarning -> stringResource(Res.string.sheet_save_unfilled)
                else -> null
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        },
    )
    if (correctionOpen) EolssuDialog(
        title = stringResource(Res.string.sheet_editor_correction_title),
        message = stringResource(Res.string.sheet_editor_correction_location, state.draft.columnOf(state.selected) + 1, state.draft.rowOf(state.selected) + 1),
        onDismissRequest = { correctionOpen = false },
    ) {
        SheetCompletionNotice(
            state,
            onFixCells = { correctionOpen = false; focusRequestKey++ },
            onChangeJangdan = { correctionOpen = false; detailsOpen = true },
            onClearJangdan = viewModel::clearJangdan,
            onRetryCatalog = viewModel::retryCompletionCatalog,
            showCellAction = false,
        )
        state.selectedReviewIssue?.let { issue ->
            val reason = if (state.canCheckSelectedReviewIssue && issue.kind in setOf("UNREGISTERED_PITCH", "OUT_OF_RANGE")) {
                stringResource(Res.string.sheet_pitch_corrected_review)
            } else when (issue.kind) {
                "UNREGISTERED_PITCH" -> issue.pitch?.let { (name, octave) -> stringResource(Res.string.sheet_review_pitch_label, name, octave) }
                    ?: stringResource(Res.string.sheet_review_missing_pitch)
                "OUT_OF_RANGE" -> stringResource(Res.string.sheet_review_range)
                else -> stringResource(Res.string.sheet_review_uncertain)
            }
            Text(reason, style = MaterialTheme.typography.bodySmall)
            EolssuButton(stringResource(Res.string.sheet_review_checked), {
                viewModel.checkSelectedReviewIssue()
                if (viewModel.uiState.value.completionValidation.correctionCellIndices.isNotEmpty()) {
                    viewModel.openCompletionIssue()
                    focusRequestKey++
                } else {
                    correctionOpen = false
                    saveOpen = true
                }
            }, enabled = state.canCheckSelectedReviewIssue)
        }
        EolssuButton(stringResource(Res.string.sheet_editor_correct_cell), { correctionOpen = false; focusRequestKey++ }, style = EolssuButtonStyle.Secondary)
    }
    if (detailsOpen && state.canEditDetails) SheetEditSettingsDialog(
        draft = state.draft,
        picker = state.jangdanPicker,
        saving = state.saving,
        onLoadJangdans = viewModel::changeJangdan,
        onApply = { bpm, jangdanId ->
            viewModel.updateSettings(bpm, jangdanId)
            detailsOpen = false
        },
        onDismiss = { detailsOpen = false; viewModel.closeJangdanPicker() },
    )
    state.jangdanPicker?.takeUnless { detailsOpen }?.let { picker ->
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
