package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
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
import eolssu.shared.generated.resources.sheetedit_audio_failed
import com.ssafy.eolssu.feature.song.edit.component.sigimsaeLabel
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import eolssu.shared.generated.resources.sheet_support_save_error
import eolssu.shared.generated.resources.sheet_support_audio_error
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_too_large
import eolssu.shared.generated.resources.sheet_close_unsaved
import eolssu.shared.generated.resources.sheet_save_draft_close
import eolssu.shared.generated.resources.sheet_save_draft_closing
import eolssu.shared.generated.resources.sheet_close_message_draft
import eolssu.shared.generated.resources.sheet_save_auto_failed
import eolssu.shared.generated.resources.sheet_close_message
import eolssu.shared.generated.resources.sheet_discard_close
import eolssu.shared.generated.resources.sheet_keep_editing
import eolssu.shared.generated.resources.sheet_review_before_reshape
import eolssu.shared.generated.resources.sheet_tempo_before_reshape
import eolssu.shared.generated.resources.sheet_tempo_mark_before_merge
import eolssu.shared.generated.resources.sheet_tempo_out_of_range_after_jangdan
import eolssu.shared.generated.resources.sheetedit_jangdan_unavailable
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import eolssu.shared.generated.resources.sheetedit_msg_saved_for_practice
import eolssu.shared.generated.resources.sheetedit_msg_saved_needs_review
import eolssu.shared.generated.resources.sheet_save_unfilled
import eolssu.shared.generated.resources.sheet_save_sigimsae_go
import eolssu.shared.generated.resources.sheet_save_sigimsae_rejected
import eolssu.shared.generated.resources.sheet_save_sigimsae_rejected_unknown
import eolssu.shared.generated.resources.sheet_save_sigimsae_remove
import eolssu.shared.generated.resources.sheet_editor_correction_title
import eolssu.shared.generated.resources.sheet_editor_correct_cell
import eolssu.shared.generated.resources.sheet_editor_correction_location
import eolssu.shared.generated.resources.sheet_review_checked
import eolssu.shared.generated.resources.basic_accompaniment_cleared_after_reshape
import eolssu.shared.generated.resources.sheetedit_pitch_unavailable
import eolssu.shared.generated.resources.sheetedit_sig_replaced
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
    // '이대로 둘게요' 뒤 다음 칸 창을 다시 열 차례. 창이 닫히고 칸이 옮겨 간 것을 본 뒤에 연다(S15P31E102-1074).
    var reopenReviewKey by remember { mutableStateOf<Int?>(null) }
    var focusRequestKey by remember { mutableStateOf(0) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val adaptationOpen = state.tab == EditTab.Adaptation && state.adaptation != null
    // 고른 칸에 고칠 음이 있으면 음 바꾸기 창을, 다시 볼 곳이면 할 일 창을 연다.
    fun openCellHelp(index: Int, sub: Int? = null) {
        val current = viewModel.uiState.value
        pitchCorrection = current.pitchCorrectionsAt(index, sub).firstOrNull()
        reviewSelection = SheetReviewSelection(index, sub).takeIf { pitchCorrection == null && current.reviewIssuesAt(index, sub).isNotEmpty() }
    }
    // '다시 볼 곳'에서 첫 칸으로 옮겨 가 고칠 방법을 바로 보인다(S15P31E102-1042).
    fun openFirstReview() {
        viewModel.openFirstRecognitionReview()
        focusRequestKey++
        openCellHelp(viewModel.uiState.value.selected)
    }
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
        SheetEditMessage.JangdanUnavailable to stringResource(Res.string.sheetedit_jangdan_unavailable),
        SheetEditMessage.TempoPlanBeforeReshape to stringResource(Res.string.sheet_tempo_before_reshape),
        SheetEditMessage.TempoMarkBeforeMerge to stringResource(Res.string.sheet_tempo_mark_before_merge),
        SheetEditMessage.TempoOutOfRangeAfterJangdan to stringResource(Res.string.sheet_tempo_out_of_range_after_jangdan),
        SheetEditMessage.BasicAccompanimentCleared to stringResource(Res.string.basic_accompaniment_cleared_after_reshape),
        SheetEditMessage.SigimsaeReplaced to state.sigimsaeSwap?.let { swap ->
            stringResource(Res.string.sheetedit_sig_replaced, swap.removed.map { sigimsaeLabel(it) }.joinToString("·"), sigimsaeLabel(swap.added))
        },
        SheetEditMessage.PitchUnavailable to stringResource(Res.string.sheetedit_pitch_unavailable),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        // 실패 코드를 이유와 할 일로 옮긴다. 다시 받기는 연습 화면에서 한다(S15P31E102-1097).
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_audio_failed,
            stringResource((state.audio as? SheetAudioUi.Failed)?.audio?.failureCode.reasonText())),
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
                // 다시 볼 곳은 칸을 옮겨 보여 주기만 한다. 칸을 누르면 고칠 방법 창이 열린다.
                onMoveReview = { step -> viewModel.moveRecognitionReview(step); focusRequestKey++ },
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
                // 높이를 바꿔 한 번에 고칠 수 있으면 첫 칸의 음 확인 대신 저장 창을 열어
                // "단소로 낼 수 없는 음 N개 · N반음 올려 고치기"를 먼저 보인다(S15P31E102-1085).
                if (state.rangeFixGuide() != null) saveOpen = true
                else if (state.completionValidation.correctionCellIndices.isNotEmpty()) {
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
                openCellHelp(index)
            },
            onSelectUnit = { index, sub ->
                viewModel.selectUnit(index, sub)
                openCellHelp(index, sub)
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
                onSetPanelExpanded = viewModel::setInputPanelExpanded,
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
            // 곡 전체 높이를 바로 바꾸면 이 음 창은 닫고 바뀐 악보를 보여 준다.
            onApplyRecommended = { pitchCorrection = null; correctionOpen = false; viewModel.applyRecommendedAdaptation() },
            reviewDetails = {
                state.reviewIssuesAt(target.cell, target.sub).filter { it.kind !in setOf("UNREGISTERED_PITCH", "OUT_OF_RANGE") }
                    .forEach { Text(sheetReviewReason(it), style = MaterialTheme.typography.bodyMedium) }
            },
        ) else LaunchedEffect(target) { pitchCorrection = null }
    }
    reviewSelection?.let { selection ->
        if (state.reviewIssuesAt(selection.cell, selection.sub).isNotEmpty()) SheetReviewInfoDialog(state, selection,
            onDismiss = { reviewSelection = null },
            onEditYul = { reviewSelection = null; viewModel.selectTab(EditTab.Yul) },
            onEditSigimsae = { reviewSelection = null; viewModel.selectTab(EditTab.Sigimsae) },
            onEditCells = { reviewSelection = null; viewModel.selectTab(EditTab.Cell) },
            onChangeJangdan = { reviewSelection = null; viewModel.changeJangdan() },
            // 지금 그대로가 맞으면 확인으로 끝낸다. 창을 먼저 닫고 다음 다시 볼 칸으로 옮긴 뒤, 잠시 후 그 칸의 창을 연다.
            onConfirmSame = {
                reviewSelection = null
                viewModel.checkSelectedReviewIssue()
                val next = viewModel.uiState.value
                if ((next.completionValidation.correctionCellIndices - next.completionValidation.rangeCellIndices).any { it != selection.cell }) {
                    viewModel.openFirstRecognitionReview()
                    focusRequestKey++
                    reopenReviewKey = (reopenReviewKey ?: 0) + 1
                }
            }.takeIf { state.selected == selection.cell && state.canCheckSelectedReviewIssue })
        else LaunchedEffect(selection) { reviewSelection = null }
    }
    reopenReviewKey?.let { key ->
        LaunchedEffect(key) {
            delay(REVIEW_REOPEN_DELAY_MS)
            reopenReviewKey = null
            openCellHelp(viewModel.uiState.value.selected)
        }
    }
    LaunchedEffect(state.tempoIssue) { if (state.tempoIssue != null) saveOpen = true }
    LaunchedEffect(state.sigimsaeIssue) { if (state.sigimsaeIssue != null) saveOpen = true }
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
        // 저장 창은 제목과 예상 음원 길이만 둔다. 저장을 막는 문제·오류가 있을 때만 그 아래에 보인다(S15P31E102-1042).
        notice = {
            SheetCompletionNotice(
                state,
                onFixCells = { saveOpen = false; viewModel.openCompletionIssue(); focusRequestKey++ },
                onChangeJangdan = { saveOpen = false; viewModel.changeJangdan() },
                onClearJangdan = viewModel::clearJangdan,
                onRetryCatalog = viewModel::retryCompletionCatalog,
                // 높이를 바꿔 고칠 수 있으면 저장 창에서 바로 고친다. 고친 뒤 저장을 다시 누르면 된다.
                onApplyRecommended = viewModel::applyRecommendedAdaptation,
                onOpenAdaptation = { saveOpen = false; viewModel.openAdaptation() },
            )
            val error = saveError ?: when {
                state.saveConflict -> messages[SheetEditMessage.SaveConflict]
                state.emptyWarning -> stringResource(Res.string.sheet_save_unfilled)
                else -> null
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.sigimsaeIssue?.let { issue ->
                // 음원 생성기가 거절한 시김새의 위치·이유를 보여 주고 바로 고치게 한다(S15P31E102-1084).
                val slot = state.sigimsaeIssueSlot
                // 생성기 내부 문구(issue.message)는 화면에 보이지 않고, 위치와 앱 이름의 시김새만 적는다(S15P31E102-1097).
                val symbols = issue.symbols.map { sigimsaeLabel(Sigimsae(it)) }.joinToString("·").ifEmpty { "이" }
                val text = if (slot == null) stringResource(Res.string.sheet_save_sigimsae_rejected_unknown)
                    else stringResource(Res.string.sheet_save_sigimsae_rejected, state.draft.columnOf(slot.cell) + 1,
                        state.draft.rowOf(slot.cell) + 1, symbols)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (slot != null) {
                    EolssuButton(stringResource(Res.string.sheet_save_sigimsae_go),
                        { saveOpen = false; viewModel.openSigimsaeIssue(); focusRequestKey++ },
                        style = EolssuButtonStyle.Secondary, small = true, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_08)
                    EolssuButton(stringResource(Res.string.sheet_save_sigimsae_remove), viewModel::removeRejectedSigimsae,
                        style = EolssuButtonStyle.Secondary, small = true, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_09)
                }
            }
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
            }, enabled = state.canCheckSelectedReviewIssue, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_01)
        }
        EolssuButton(stringResource(Res.string.sheet_editor_correct_cell), { correctionOpen = false; focusRequestKey++ }, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_02)
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
        EolssuButton(stringResource(Res.string.sheet_adapt_confirm_apply), viewModel::applyAdaptation, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_03)
        EolssuButton(stringResource(Res.string.common_cancel), viewModel::cancelApplyAdaptation, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_04)
    }
    state.jangdanPicker?.takeUnless { detailsOpen }?.let { picker ->
        SheetEditJangdanDialog(picker, state.draft.jangdanId, state.draft,
            onSelect = viewModel::selectJangdan, onClear = viewModel::clearJangdan, onReload = viewModel::changeJangdan, onDismiss = viewModel::closeJangdanPicker)
    }
    if (state.closeWarning) EolssuDialog(
        title = stringResource(if (state.saveStatus == SheetSaveStatus.DraftFailed) Res.string.sheet_save_auto_failed
            else Res.string.sheet_close_unsaved),
        message = stringResource(if (state.canSaveDraft) Res.string.sheet_close_message_draft else Res.string.sheet_close_message),
        onDismissRequest = viewModel::continueEditing,
    ) {
        // 편집은 자동저장하지 않으므로 닫을 때 임시저장을 먼저 권한다(S15P31E102-889).
        // 저장 응답을 기다리는 동안 저장 중임을 보이고 모든 버튼을 막는다. 실패하면 창이 그대로 남아 다시 고를 수 있다.
        if (state.canSaveDraft || state.closeSaving) EolssuButton(
            stringResource(if (state.closeSaving) Res.string.sheet_save_draft_closing else Res.string.sheet_save_draft_close),
            { viewModel.saveDraftAndClose(onClose) }, enabled = !state.closeSaving, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_05)
        EolssuButton(stringResource(Res.string.sheet_keep_editing), viewModel::continueEditing,
            style = if (state.canSaveDraft) EolssuButtonStyle.Secondary else EolssuButtonStyle.Primary, enabled = !state.closeSaving, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_06)
        EolssuButton(stringResource(Res.string.sheet_discard_close), { viewModel.discardChangesAndClose(onClose) },
            style = EolssuButtonStyle.Secondary, enabled = !state.closeSaving, qaTarget = QaTarget.SHEET_EDIT_ROUTE_BUTTON_07)
    }
}

/** '이대로 둘게요' 뒤 창이 닫히고 다음 칸으로 옮겨 간 것을 볼 수 있게 다시 열기 전에 기다리는 시간. */
private const val REVIEW_REOPEN_DELAY_MS = 450L
