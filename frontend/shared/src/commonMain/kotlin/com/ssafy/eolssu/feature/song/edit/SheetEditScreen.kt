package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.ssafy.eolssu.core.ui.component.EolssuIcons
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
import androidx.compose.ui.text.style.TextOverflow
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.song.edit.layout.SheetEditExpandedLayout
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
import com.ssafy.eolssu.core.ui.component.formatClock
import eolssu.shared.generated.resources.sheet_save_estimated_duration
import eolssu.shared.generated.resources.sheet_editor_duration_unavailable
import eolssu.shared.generated.resources.sheet_details_edit
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

/**
 * 빠르기와 옆의 연필(제목·빠르기 변경), 오른쪽 끝 예상 음원 길이, 오류 정간 표시를 제공하는 편집 화면.
 * 휴대폰·태블릿 세로는 악보 아래에 입력판을 두고, 태블릿 가로([WindowWidthClass.Expanded])는
 * [SheetEditExpandedLayout]으로 악보 오른쪽에 입력판을 세운다(S15P31E102-965). 상태·동작은 같다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetEditScreen(state: SheetEditUiState, snackbarHostState: SnackbarHostState, actions: SheetEditActions,
    focusRequestKey: Int = 0, adaptationContent: @Composable () -> Unit = {}, guideContent: @Composable () -> Unit = {},
    widthClass: WindowWidthClass = currentWindowLayout().widthClass) {
    val draft = state.draft
    val song = remember(draft) { draft.toSong() }
    val corrections = state.completionValidation.correctionCellIndices
    val correctionUnits = state.completionValidation.correctionUnitKeys
    val expanded = widthClass == WindowWidthClass.Expanded
    val contentMaxWidth = if (expanded) WindowBreakpoints.ContentMaxWidth else CompactContentMaxWidth
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = contentMaxWidth,
        topBar = {
            EditorTopBar(draft.title, "", stringResource(Res.string.sheetedit_close), actions.onClose, maxWidth = contentMaxWidth) {
                PillButton(stringResource(if (state.confirming) Res.string.sheet_completion_saving else Res.string.sheetedit_preview), actions.onSave, enabled = !state.confirming)
            }
        },
    ) {
        val tempoRow: @Composable (Modifier) -> Unit = { modifier ->
            // 빠르기 바로 오른쪽 연필로 제목·빠르기를 고치고, 예상 음원 길이는 오른쪽 끝에 둔다(S15P31E102-889).
            Row(
                modifier.fillMaxWidth().heightIn(min = 40.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${draft.tempoUnitLabel} = ${draft.bpm}", style = MaterialTheme.typography.bodyMedium)
                if (state.canEditDetails) TempoEditButton(actions.onOpenDetails, enabled = !state.saving)
                Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
                val estimate = draft.estimatedDurationSeconds(draft.bpm)
                Text(if (estimate != null) stringResource(Res.string.sheet_save_estimated_duration, formatClock(estimate))
                    else stringResource(Res.string.sheet_editor_duration_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = EolssuTheme.colors.textSub, textAlign = TextAlign.End)
            }
        }
        val scoreBoard: @Composable (Modifier) -> Unit = { modifier ->
            // 가져온 악보 확인 안내는 악보 바로 위에 둬서 처음 열 때 보이게 한다(S15P31E102-979). 두 배치 모두 같다.
            Column(modifier) {
                guideContent()
                EditScoreBoard(
                    song = song,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    marks = editorMarks(draft, state.selected, state.selectedSub, corrections, correctionUnits,
                        state.completionValidation.rangeCellIndices),
                    unitMarks = editorUnitMarks(draft, state.selected, state.selectedSub, correctionUnits,
                        state.completionValidation.rangeUnitKeys),
                    annotations = remember(draft) { draft.annotations() },
                    focusCell = draft.columnOf(state.selected) to draft.rowOf(state.selected),
                    focusRequestKey = focusRequestKey,
                    onUnitClick = { column, row, unitRow, unitCol ->
                        val index = draft.indexOf(column, row)
                        actions.onSelectUnit(index, draft.subIndexOf(index, unitRow, unitCol))
                    },
                )
            }
        }
        val inputPanel: @Composable (docked: Boolean) -> Unit = { docked ->
            EditInputPanel(draft, state.selected, state.selectedSub, state.tab, state.yulRegister, state.supportedInputPitches, state.canUndo, state.canRedo, actions.input,
                saveFeedback = { SheetSaveFeedback(state, actions.onRetryDraft) }, adaptationContent = adaptationContent, docked = docked,
                hasTempoPlan = state.hasTempoPlan, expanded = state.inputPanelExpanded)
        }
        if (expanded) {
            SheetEditExpandedLayout(tempoRow = tempoRow, scoreBoard = scoreBoard, inputPanel = { inputPanel(true) })
        } else {
            Column(Modifier.fillMaxSize()) {
                tempoRow(Modifier.padding(horizontal = 20.dp))
                scoreBoard(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp))
                inputPanel(false)
            }
        }
    }
}

/** 빠르기 옆 작은 연필(아이콘 16dp, 보이는 칸 36dp, 누르는 영역 48dp). 누르면 제목·빠르기 변경 창을 연다. */
@Composable
private fun TempoEditButton(onClick: () -> Unit, enabled: Boolean) {
    val label = stringResource(Res.string.sheet_details_edit)
    Box(
        Modifier
            .padding(start = 2.dp)
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = UserActionLog.click(QaTarget.SHEET_EDIT_SCREEN_01, onClick))
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(EolssuIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp), tint = EolssuTheme.colors.textSub)
    }
}

@Composable
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
    SheetEditMessage.SaveUnsupportedPitches, SheetEditMessage.AudioUnsupportedPitches,
    SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge,
    SheetEditMessage.Saved, SheetEditMessage.SavedForPractice, SheetEditMessage.SavedNeedsReview, SheetEditMessage.Draft,
    SheetEditMessage.ReviewBeforeReshape, SheetEditMessage.TempoPlanBeforeReshape, SheetEditMessage.ComingSoon,
    SheetEditMessage.BasicAccompanimentCleared, SheetEditMessage.SigimsaeReplaced, SheetEditMessage.PitchUnavailable,
    SheetEditMessage.AudioReady, SheetEditMessage.AudioFailed, SheetEditMessage.AudioStatusUnknown,
)
