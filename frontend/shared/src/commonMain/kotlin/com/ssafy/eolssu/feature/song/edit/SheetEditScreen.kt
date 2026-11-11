package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.edit.component.EditInputActions
import com.ssafy.eolssu.feature.song.edit.component.EditInputPanel
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.EditorTopBar
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.PillButton
import com.ssafy.eolssu.feature.song.edit.component.editorMarks
import com.ssafy.eolssu.feature.song.edit.component.editorUnitMarks
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_banner_import
import eolssu.shared.generated.resources.sheetedit_banner_open
import eolssu.shared.generated.resources.sheet_review_checked
import eolssu.shared.generated.resources.sheet_review_location
import eolssu.shared.generated.resources.sheet_review_missing_pitch
import eolssu.shared.generated.resources.sheet_review_pitch_label
import eolssu.shared.generated.resources.sheet_review_range
import eolssu.shared.generated.resources.sheet_review_removed_cell
import eolssu.shared.generated.resources.sheet_review_save_checks
import eolssu.shared.generated.resources.sheet_review_uncertain
import eolssu.shared.generated.resources.sheetedit_close
import eolssu.shared.generated.resources.sheetedit_preview
import eolssu.shared.generated.resources.sheet_editor_tempo
import eolssu.shared.generated.resources.sheet_editor_edit
import eolssu.shared.generated.resources.sheet_completion_saving
import eolssu.shared.generated.resources.sheet_editor_audio
import org.jetbrains.compose.resources.stringResource

/** 정간보 편집기의 사용자 행동. */
class SheetEditActions(
    val onClose: () -> Unit,
    val onPreview: () -> Unit,
    val onSave: () -> Unit,
    val onOpenDetails: () -> Unit,
    val onOpenChecks: () -> Unit,
    val onSelectCell: (Int) -> Unit,
    val onSelectUnit: (Int, Int) -> Unit,
    val input: EditInputActions,
    val onOpenReviewCell: () -> Unit = {},
    val onCheckReviewIssue: () -> Unit = {},
    val onFixCompletionIssue: () -> Unit = {},
    val onClearJangdan: () -> Unit = {},
    val onRetryCompletionCatalog: () -> Unit = {},
)

/** 정간보 단일 편집 화면. 제목과 빠르기는 왼쪽 편집 버튼으로 변경한다. */
@Composable
fun SheetEditScreen(state: SheetEditUiState, snackbarHostState: SnackbarHostState, actions: SheetEditActions) {
    val draft = state.draft
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EditorTopBar(
                title = draft.title,
                subtitle = jangdanLabel(draft.jangdanName),
                closeLabel = stringResource(Res.string.sheetedit_close),
                onClose = actions.onClose,
            ) {
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
                Text(
                    stringResource(Res.string.sheet_editor_tempo, draft.bpm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EolssuTheme.colors.text,
                )
                if (state.canEditDetails) {
                    PillButton(stringResource(Res.string.sheet_editor_edit), actions.onOpenDetails, enabled = !state.saving)
                }
                Spacer(Modifier.weight(1f))
                PillButton(stringResource(Res.string.sheet_editor_audio), actions.onPreview, enabled = !state.confirming)
            }
            JeongganboContent(state, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun JeongganboContent(state: SheetEditUiState, actions: SheetEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    val song = remember(draft) { draft.toSong() }
    Column(modifier) {
        if (state.reviewIssues.isNotEmpty()) ReviewBanner(state, actions)
        else if (state.pendingChecks > 0) ImportBanner(state.pendingChecks, actions.onOpenChecks)
        SheetCompletionNotice(state, actions.onFixCompletionIssue, actions.input.onChangeJangdan, actions.onClearJangdan, actions.onRetryCompletionCatalog,
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), showReviewSummary = false)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EditScoreBoard(
                song = song,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                marks = editorMarks(draft, state.selected, state.selectedSub),
                unitMarks = editorUnitMarks(draft, state.selected, state.selectedSub),
                annotations = remember(draft) { draft.annotations() },
                focusColumn = draft.columnOf(state.selected),
                onUnitClick = { column, row, unitRow, unitCol ->
                    val index = draft.indexOf(column, row)
                    actions.onSelectUnit(index, draft.subIndexOf(index, unitRow, unitCol))
                },
            )
        }
        EditInputPanel(draft, state.selected, state.selectedSub, state.tab, state.yulRegister, state.availablePitches, state.canUndo, state.canRedo, actions.input)
    }
}

@Composable
private fun ReviewBanner(state: SheetEditUiState, actions: SheetEditActions) {
    val issue = state.nextReviewIssue
    val cellIndex = state.nextReviewCellIndex
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            .background(EolssuTheme.colors.accentTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (issue == null) {
            HintText(stringResource(Res.string.sheet_review_save_checks))
        } else {
            HintText(stringResource(Res.string.sheetedit_banner_import, state.pendingChecks))
            val reason = when (issue.kind) {
                "UNREGISTERED_PITCH" -> issue.pitch?.let { (name, octave) ->
                    stringResource(Res.string.sheet_review_pitch_label, name, octave)
                } ?: stringResource(Res.string.sheet_review_missing_pitch)
                "OUT_OF_RANGE" -> stringResource(Res.string.sheet_review_range)
                else -> stringResource(Res.string.sheet_review_uncertain)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (cellIndex == null) stringResource(Res.string.sheet_review_removed_cell, reason)
                    else stringResource(Res.string.sheet_review_location, state.draft.columnOf(cellIndex) + 1, state.draft.rowOf(cellIndex) + 1, reason),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (cellIndex != null && cellIndex != state.selected) {
                    PillButton(stringResource(Res.string.sheetedit_banner_open), actions.onOpenReviewCell)
                } else {
                    PillButton(stringResource(Res.string.sheet_review_checked), actions.onCheckReviewIssue, enabled = state.canCheckNextReviewIssue)
                }
            }
        }
    }
}

/** 인식 결과에서 온 편집: '불러온 악보 · 인식을 확인할 정간 n개가 남았어요'와 '확인 정간 보기'. */
@Composable
private fun ImportBanner(pending: Int, onOpen: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            .background(colors.accentTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val text = stringResource(Res.string.sheetedit_banner_import, pending)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Surface(
            onClick = onOpen,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = CircleShape,
            color = colors.surface,
            contentColor = colors.text,
            border = BorderStroke(1.dp, colors.accentTintStrong),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.sheetedit_banner_open), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** EDIT-02에서도 띄우는 안내: 편집기에서 생긴 것과, 뒤에서 만들던 음원의 완료·실패·확인 못 함 알림. */
internal fun SheetEditMessage.shownInEditor(): Boolean = this in EditorMessages

private val EditorMessages = setOf(
    SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge,
    SheetEditMessage.Saved, SheetEditMessage.SavedForPractice, SheetEditMessage.SavedNeedsReview, SheetEditMessage.Draft,
    SheetEditMessage.ReviewBeforeReshape,
    SheetEditMessage.ComingSoon,
    SheetEditMessage.AudioReady,
    SheetEditMessage.AudioFailed,
    SheetEditMessage.AudioStatusUnknown,
)
