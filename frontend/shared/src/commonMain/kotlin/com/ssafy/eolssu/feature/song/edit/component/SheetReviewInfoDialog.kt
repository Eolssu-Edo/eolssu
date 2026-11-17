package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.feature.song.edit.SheetEditUiState
import com.ssafy.eolssu.feature.song.edit.SheetReviewIssue
import com.ssafy.eolssu.feature.song.edit.SheetReviewSelection
import com.ssafy.eolssu.feature.song.edit.label
import com.ssafy.eolssu.feature.song.edit.reviewIssuesAt
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 다시 볼 칸을 고르면 무엇이 문제인지와 그 문제를 고치는 버튼 하나를 보인다(S15P31E102-1042).
 * 앱은 원본 악보를 보여 주지 않으므로 원본과 비교하라고 하지 않는다. 문제 종류마다 고치는 곳(율명·시김새·정간·장단)으로 바로 보내고,
 * 지금 그대로가 맞으면 `이대로 둘게요`로 확인을 끝낸다. 위치(몇 각 몇 정간)는 악보에서 칸이 선택돼 보이므로 적지 않는다.
 */
@Composable
internal fun SheetReviewInfoDialog(
    state: SheetEditUiState,
    selection: SheetReviewSelection,
    onDismiss: () -> Unit,
    onEditYul: () -> Unit = onDismiss,
    onEditSigimsae: () -> Unit = onDismiss,
    onEditCells: () -> Unit = onDismiss,
    onChangeJangdan: () -> Unit = onDismiss,
    onConfirmSame: (() -> Unit)? = null,
) {
    val issues = state.reviewIssuesAt(selection.cell, selection.sub)
    val notes = state.draft.cells.getOrNull(selection.cell).orEmpty()
    if (issues.isEmpty() || notes.isEmpty()) return
    val selectedNotes = selection.sub?.let { listOfNotNull(notes.getOrNull(it)) } ?: notes
    EolssuDialog(title = stringResource(Res.string.sheet_review_fix_title), onDismissRequest = onDismiss) {
        val labels = selectedNotes.map { note -> when (note) {
            ScoreNote.Rest -> stringResource(Res.string.sheet_review_info_rest)
            ScoreNote.Hold -> stringResource(Res.string.sheet_review_info_hold)
            ScoreNote.Blank -> stringResource(Res.string.sheet_review_info_blank)
            is ScoreNote.Pitch -> "${note.yul.fullName} (${note.symbol})"
            is ScoreNote.SourcePitch -> "${note.fullName} (${note.symbol})"
        } }
        Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        issues.forEach { Text(sheetReviewReason(it), style = MaterialTheme.typography.bodyMedium) }
        val rhythm = issues.firstNotNullOfOrNull { it.rhythmReview }
        when {
            rhythm?.reason == "TIME_SIGNATURE_REVIEW_REQUIRED" ->
                EolssuButton(stringResource(Res.string.sheetedit_cell_jangdan_change), onChangeJangdan)
            rhythm != null -> EolssuButton(stringResource(Res.string.sheet_review_fix_cells), onEditCells)
            issues.any { it.kind == "ORNAMENT_DROPPED" } -> EolssuButton(stringResource(Res.string.sheet_review_fix_sigimsae), onEditSigimsae)
            else -> EolssuButton(stringResource(Res.string.sheet_review_fix_yul), onEditYul)
        }
        if (onConfirmSame != null) EolssuButton(stringResource(Res.string.sheet_review_fix_same), onConfirmSame, style = EolssuButtonStyle.Secondary)
        EolssuButton(stringResource(Res.string.common_close), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_REVIEW_INFO_DIALOG_BUTTON_01)
    }
}

@Composable
internal fun sheetReviewReason(issue: SheetReviewIssue, correctedPitch: Boolean = false): String =
    if (issue.rhythmReview != null) issue.rhythmReview.label()
    else if (correctedPitch && issue.kind in setOf("UNREGISTERED_PITCH", "OUT_OF_RANGE")) stringResource(Res.string.sheet_pitch_corrected_review)
    else when (issue.kind) {
        "UNREGISTERED_PITCH" -> issue.pitch?.let { (name, octave) -> stringResource(Res.string.sheet_review_pitch_label, name, octave) }
            ?: stringResource(Res.string.sheet_review_missing_pitch)
        "OUT_OF_RANGE" -> stringResource(Res.string.sheet_review_range)
        "ORNAMENT_DROPPED" -> stringResource(Res.string.sheet_review_ornament_dropped)
        "LOW_CONFIDENCE" -> if (issue.reason == "GAP_IN_VOICE") stringResource(Res.string.sheet_review_gap_in_voice)
            else stringResource(Res.string.sheet_review_uncertain)
        else -> stringResource(Res.string.sheet_review_uncertain)
    }
