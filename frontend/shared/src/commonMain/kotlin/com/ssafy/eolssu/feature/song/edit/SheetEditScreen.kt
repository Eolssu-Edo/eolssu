package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.edit.component.EditInputActions
import com.ssafy.eolssu.feature.song.edit.component.EditInputPanel
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.EditorTopBar
import com.ssafy.eolssu.feature.song.edit.component.PillButton
import com.ssafy.eolssu.feature.song.edit.component.editorMarks
import com.ssafy.eolssu.feature.song.edit.component.editorUnitMarks
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_close
import eolssu.shared.generated.resources.sheetedit_preview
import eolssu.shared.generated.resources.sheet_editor_tempo
import eolssu.shared.generated.resources.sheet_editor_edit
import eolssu.shared.generated.resources.sheet_completion_saving
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_too_large
import eolssu.shared.generated.resources.sheet_save_auto_complete
import eolssu.shared.generated.resources.sheet_save_auto_failed
import eolssu.shared.generated.resources.sheet_save_confirm_complete
import eolssu.shared.generated.resources.sheet_save_auto_retry
import org.jetbrains.compose.resources.stringResource

/** 정간보 편집기의 사용자 행동. 검토·저장 안내는 요청 시 대화상자에서 제공한다. */
class SheetEditActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onRetryDraft: () -> Unit,
    val onOpenDetails: () -> Unit,
    val onSelectCell: (Int) -> Unit,
    val onSelectUnit: (Int, Int) -> Unit,
    val input: EditInputActions,
)

/** 빠르기·장단과 오른쪽 편집 버튼, 오류 정간 표시를 제공하는 편집 화면. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetEditScreen(state: SheetEditUiState, snackbarHostState: SnackbarHostState, actions: SheetEditActions, focusRequestKey: Int = 0) {
    val draft = state.draft
    val song = remember(draft) { draft.toSong() }
    val corrections = state.completionValidation.correctionCellIndices
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EditorTopBar(draft.title, "", stringResource(Res.string.sheetedit_close), actions.onClose) {
                PillButton(stringResource(if (state.confirming) Res.string.sheet_completion_saving else Res.string.sheetedit_preview), actions.onSave, enabled = !state.confirming)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(Res.string.sheet_editor_tempo, draft.bpm), style = MaterialTheme.typography.bodyMedium)
                    Text(jangdanLabel(draft.jangdanName), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.canEditDetails) PillButton(stringResource(Res.string.sheet_editor_edit), actions.onOpenDetails, enabled = !state.saving, secondary = true)
            }
            SheetSaveFeedback(state, actions.onRetryDraft)
            EditScoreBoard(
                song = song,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                marks = editorMarks(draft, state.selected, state.selectedSub, corrections),
                unitMarks = editorUnitMarks(draft, state.selected, state.selectedSub, corrections),
                annotations = remember(draft) { draft.annotations() },
                focusCell = draft.columnOf(state.selected) to draft.rowOf(state.selected),
                focusRequestKey = focusRequestKey,
                onUnitClick = { column, row, unitRow, unitCol ->
                    val index = draft.indexOf(column, row)
                    actions.onSelectUnit(index, draft.subIndexOf(index, unitRow, unitCol))
                },
            )
            EditInputPanel(draft, state.selected, state.selectedSub, state.tab, state.yulRegister, state.supportedInputPitches, state.canUndo, state.canRedo, actions.input)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SheetSaveFeedback(state: SheetEditUiState, onRetryDraft: () -> Unit) {
    val label = when {
        state.saveConflict -> Res.string.sheet_save_conflict
        state.confirming || state.saving -> Res.string.sheet_completion_saving
        else -> when (state.saveStatus) {
            SheetSaveStatus.DraftSaved -> Res.string.sheet_save_auto_complete
            SheetSaveStatus.Confirmed -> Res.string.sheet_save_confirm_complete
            SheetSaveStatus.DraftFailed -> Res.string.sheet_save_auto_failed
            SheetSaveStatus.ConfirmationFailed -> Res.string.sheet_save_failed
            SheetSaveStatus.TooLarge -> Res.string.sheet_save_too_large
            null -> return
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = when (label) {
                Res.string.sheet_save_conflict, Res.string.sheet_save_auto_failed,
                Res.string.sheet_save_failed, Res.string.sheet_save_too_large -> MaterialTheme.colorScheme.error
                else -> EolssuTheme.colors.textMuted
            },
        )
        if (label == Res.string.sheet_save_auto_failed) {
            PillButton(stringResource(Res.string.sheet_save_auto_retry), onRetryDraft)
        }
    }
}

internal fun SheetEditMessage.shownInEditor(): Boolean = this in EditorMessages

private val EditorMessages = setOf(
    SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge,
    SheetEditMessage.Saved, SheetEditMessage.SavedForPractice, SheetEditMessage.SavedNeedsReview, SheetEditMessage.Draft,
    SheetEditMessage.ReviewBeforeReshape, SheetEditMessage.ComingSoon,
    SheetEditMessage.AudioReady, SheetEditMessage.AudioFailed, SheetEditMessage.AudioStatusUnknown,
)
