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
import com.ssafy.eolssu.feature.song.edit.component.SheetAdaptationPanel
import com.ssafy.eolssu.feature.song.edit.component.SheetImportGuideCard
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eolssu.shared.generated.resources.sheet_adapt_confirm_title
import eolssu.shared.generated.resources.sheet_adapt_confirm_message
import eolssu.shared.generated.resources.sheet_adapt_confirm_overwrite
import androidx.compose.ui.text.font.FontWeight
import eolssu.shared.generated.resources.sheet_adapt_confirm_apply
import eolssu.shared.generated.resources.common_cancel
import com.ssafy.eolssu.feature.song.edit.component.SheetEditJangdanDialog
import com.ssafy.eolssu.feature.song.edit.component.SheetPitchCorrectionDialog
import com.ssafy.eolssu.feature.song.edit.component.SheetReviewInfoDialog
import com.ssafy.eolssu.feature.song.edit.component.sheetReviewReason
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.sheetedit_msg_audio_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import eolssu.shared.generated.resources.sheet_support_save_error
import eolssu.shared.generated.resources.sheet_support_audio_error
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_too_large
import eolssu.shared.generated.resources.sheet_close_unsaved
import eolssu.shared.generated.resources.sheet_save_draft_close
import eolssu.shared.generated.resources.sheet_close_message_draft
import eolssu.shared.generated.resources.sheet_save_auto_failed
import eolssu.shared.generated.resources.sheet_close_message
import eolssu.shared.generated.resources.sheet_discard_close
import eolssu.shared.generated.resources.sheet_keep_editing
import eolssu.shared.generated.resources.sheet_review_before_reshape
import eolssu.shared.generated.resources.sheet_tempo_before_reshape
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import eolssu.shared.generated.resources.sheetedit_msg_saved_for_practice
import eolssu.shared.generated.resources.sheetedit_msg_saved_needs_review
import eolssu.shared.generated.resources.sheet_save_unfilled
import eolssu.shared.generated.resources.sheet_editor_correction_title
import eolssu.shared.generated.resources.sheet_editor_correct_cell
import eolssu.shared.generated.resources.sheet_editor_correction_location
import eolssu.shared.generated.resources.sheet_review_checked
import eolssu.shared.generated.resources.basic_accompaniment_cleared_after_reshape
import org.jetbrains.compose.resources.stringResource

/**
 * 경고 정간을 누르거나 저장할 때 검수 이유와 수정 방법을 표시한다. 제목은 저장 창, 빠르기는 상단 편집 창, 장단은 정간 도구에서 바꾼다.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetEditRoute(
    viewModel: SheetEditViewModel,
    onClose: () -> Unit,
    onOpenChecks: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var correctionOpen by rememberSaveable { mutableStateOf(false) }
    var pitchCorrection by remember { mutableStateOf<SheetPitchCorrection?>(null) }
    var reviewSelection by remember { mutableStateOf<SheetReviewSelection?>(null) }
    var focusRequestKey by remember { mutableStateOf(0) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val adaptationOpen = state.tab == EditTab.Adaptation && state.adaptation != null
    BackHandler(enabled = state.draft.original != null && !adaptationOpen && !state.closeWarning && !detailsOpen && !saveOpen && !correctionOpen && pitchCorrection == null && !state.confirming) { viewModel.closeEditor(onClose) }
    BackHandler(enabled = adaptationOpen && state.adaptation?.confirmationRequested != true) { viewModel.closeAdaptation() }
    if (state.documentLoading || state.documentFailed) {
        SheetDocumentNotice(state.documentLoading, onClose, viewModel::loadDocument)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = mapOf(
        SheetEditMessage.SaveUnsupportedPitches to stringResource(Res.string.sheet_support_save_error),
        SheetEditMessage.AudioUnsupportedPitches to stringResource(Res.string.sheet_support_audio_error),
        SheetEditMessage.SaveFailed to stringResource(Res.string.sheet_save_failed),
        SheetEditMessage.SaveConflict to stringResource(Res.string.sheet_save_conflict),
        SheetEditMessage.SaveTooLarge to stringResource(Res.string.sheet_save_too_large),
        SheetEditMessage.Draft to stringResource(Res.string.sheetedit_msg_draft),
        SheetEditMessage.Saved to stringResource(Res.string.sheetedit_msg_saved),
        SheetEditMessage.SavedForPractice to stringResource(Res.string.sheetedit_msg_saved_for_practice),
        SheetEditMessage.SavedNeedsReview to stringResource(Res.string.sheetedit_msg_saved_needs_review),
        SheetEditMessage.ComingSoon to stringResource(Res.string.common_coming_soon),
        SheetEditMessage.ReviewBeforeReshape to stringResource(Res.string.sheet_review_before_reshape),
        SheetEditMessage.TempoPlanBeforeReshape to stringResource(Res.string.sheet_tempo_before_reshape),
        SheetEditMessage.BasicAccompanimentCleared to stringResource(Res.string.basic_accompaniment_cleared_after_reshape),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_msg_audio_failed),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
    )
    LaunchedEffect(state.message) {
        val message = state.message?.takeIf { it.shownInEditor() } ?: return@LaunchedEffect
        val text = messages[message] ?: return@LaunchedEffect
        // 저장하기가 끝나면 편집기를 닫고 연습곡 고르기 '내 악보' 목록으로 간다(S15P31E102-768).
        if (message in setOf(SheetEditMessage.Saved, SheetEditMessage.SavedForPractice, SheetEditMessage.SavedNeedsReview)) {
            viewModel.consumeMessage()
            saveOpen = false
            onSaved()
            return@LaunchedEffect
        }
        if (saveOpen && message in setOf(SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge, SheetEditMessage.SaveUnsupportedPitches)) {
            saveError = text
            viewModel.consumeMessage()
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }
    SheetEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        focusRequestKey = focusRequestKey,
        guideContent = {
            state.importGuide?.let { guide -> SheetImportGuideCard(guide,
                onApplyRecommended = viewModel::applyRecommendedAdaptation,
                onOpenAdaptation = viewModel::openAdaptation,
                onOpenReview = { viewModel.openFirstRecognitionReview(); focusRequestKey++ },
                onDismiss = viewModel::dismissImportGuide,
                modifier = Modifier.padding(bottom = 8.dp)) }
        },
        adaptationContent = {
            state.adaptation?.let { panel -> SheetAdaptationPanel(panel,
                onTranspose = viewModel::selectAdaptationTranspose,
                onApply = viewModel::requestApplyAdaptation, onRetry = viewModel::openAdaptation) }
        },
        actions = SheetEditActions(
            onClose = { viewModel.closeEditor(onClose) },
            onRetryDraft = viewModel::retryDraftSave,
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
                val current = viewModel.uiState.value
                pitchCorrection = current.pitchCorrectionsAt(index).firstOrNull()
                reviewSelection = SheetReviewSelection(index).takeIf { pitchCorrection == null && current.reviewIssuesAt(index).isNotEmpty() }
            },
            onSelectUnit = { index, sub ->
                viewModel.selectUnit(index, sub)
                val current = viewModel.uiState.value
                pitchCorrection = current.pitchCorrectionsAt(index, sub).firstOrNull()
                reviewSelection = SheetReviewSelection(index, sub).takeIf { pitchCorrection == null && current.reviewIssuesAt(index, sub).isNotEmpty() }
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
            onAdapt = { pitchCorrection = null; correctionOpen = false; viewModel.openAdaptation() },
            reviewDetails = {
                state.reviewIssuesAt(target.cell, target.sub).filter { it.kind !in setOf("UNREGISTERED_PITCH", "OUT_OF_RANGE") }
                    .forEach { Text(sheetReviewReason(it), style = MaterialTheme.typography.bodyMedium) }
            },
        ) else LaunchedEffect(target) { pitchCorrection = null }
    }
    reviewSelection?.let { selection ->
        if (state.reviewIssuesAt(selection.cell, selection.sub).isNotEmpty()) SheetReviewInfoDialog(state, selection) { reviewSelection = null }
        else LaunchedEffect(selection) { reviewSelection = null }
    }
    LaunchedEffect(state.tempoIssue) { if (state.tempoIssue != null) saveOpen = true }
    if (saveOpen) SheetEditDetailsDialog(
        initialTitle = state.draft.title,
        tempoIssue = state.tempoIssue,
        saving = state.confirming,
        canApply = !state.saving && !state.saveConflict && state.completionValidation.canComplete,
        durationSeconds = state.draft.estimatedDurationSeconds(state.draft.bpm),
        onApply = { title ->
            saveError = null
            viewModel.updateTitle(title)
            viewModel.requestSave()
        },
        onDismiss = { saveOpen = false },
        notice = {
            SheetCompletionNotice(
                state,
                onFixCells = { saveOpen = false; viewModel.openCompletionIssue(); focusRequestKey++ },
                onChangeJangdan = { saveOpen = false; viewModel.changeJangdan() },
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
            onChangeJangdan = { correctionOpen = false; viewModel.changeJangdan() },
            onClearJangdan = viewModel::clearJangdan,
            onRetryCatalog = viewModel::retryCompletionCatalog,
            showCellAction = false,
        )
        state.selectedReviewIssue?.let { issue ->
            Text(sheetReviewReason(issue, state.canCheckSelectedReviewIssue), style = MaterialTheme.typography.bodySmall)
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
        hasTempoPlan = state.hasTempoPlan,
        saving = state.saving,
        selectedCell = state.selected,
        onApply = { edited ->
            viewModel.updateTempoSettings(edited)
            detailsOpen = false
        },
        onDismiss = { detailsOpen = false; viewModel.closeJangdanPicker() },
    )
    if (state.adaptation?.confirmationRequested == true) EolssuDialog(
        title = stringResource(Res.string.sheet_adapt_confirm_title),
        onDismissRequest = viewModel::cancelApplyAdaptation,
    ) {
        Text(stringResource(Res.string.sheet_adapt_confirm_overwrite),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.sheet_adapt_confirm_message), style = MaterialTheme.typography.bodyMedium)
        EolssuButton(stringResource(Res.string.sheet_adapt_confirm_apply), viewModel::applyAdaptation)
        EolssuButton(stringResource(Res.string.common_cancel), viewModel::cancelApplyAdaptation, style = EolssuButtonStyle.Secondary)
    }
    state.jangdanPicker?.takeUnless { detailsOpen }?.let { picker ->
        SheetEditJangdanDialog(picker, state.draft.jangdanId, state.draft,
            onSelect = viewModel::selectJangdan, onClear = viewModel::clearJangdan, onReload = viewModel::changeJangdan, onDismiss = viewModel::closeJangdanPicker,
            hasTempoPlan = state.hasTempoPlan, selectedCell = state.selected, onBasic = viewModel::selectBasicAccompaniment)
    }
    if (state.closeWarning) EolssuDialog(
        title = stringResource(if (state.saveStatus == SheetSaveStatus.DraftFailed) Res.string.sheet_save_auto_failed
            else Res.string.sheet_close_unsaved),
        message = stringResource(if (state.canSaveDraft) Res.string.sheet_close_message_draft else Res.string.sheet_close_message),
        onDismissRequest = viewModel::continueEditing,
    ) {
        // 편집은 자동저장하지 않으므로 닫을 때 임시저장을 먼저 권한다(S15P31E102-889).
        if (state.canSaveDraft) EolssuButton(stringResource(Res.string.sheet_save_draft_close), { viewModel.saveDraftAndClose(onClose) })
        EolssuButton(stringResource(Res.string.sheet_keep_editing), viewModel::continueEditing,
            style = if (state.canSaveDraft) EolssuButtonStyle.Secondary else EolssuButtonStyle.Primary)
        EolssuButton(stringResource(Res.string.sheet_discard_close), { viewModel.discardChangesAndClose(onClose) }, style = EolssuButtonStyle.Secondary)
    }
}
