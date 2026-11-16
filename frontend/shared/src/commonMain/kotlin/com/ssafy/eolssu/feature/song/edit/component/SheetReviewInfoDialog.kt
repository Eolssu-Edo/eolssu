package com.ssafy.eolssu.feature.song.edit.component

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

/** 클릭한 위치의 정보만 보여 준다. 전체 저장 검수와 확인 처리는 저장 경로에 둔다. */
@Composable
internal fun SheetReviewInfoDialog(state: SheetEditUiState, selection: SheetReviewSelection, onDismiss: () -> Unit) {
    val issues = state.reviewIssuesAt(selection.cell, selection.sub)
    val notes = state.draft.cells.getOrNull(selection.cell).orEmpty()
    if (issues.isEmpty() || notes.isEmpty()) return
    val selectedNotes = selection.sub?.let { listOfNotNull(notes.getOrNull(it)) } ?: notes
    val location = if (selection.sub != null && notes.size > 1) stringResource(Res.string.sheet_review_info_unit,
        state.draft.columnOf(selection.cell) + 1, state.draft.rowOf(selection.cell) + 1, selection.sub + 1)
    else stringResource(Res.string.sheet_review_info_location, state.draft.columnOf(selection.cell) + 1, state.draft.rowOf(selection.cell) + 1)
    EolssuDialog(title = stringResource(Res.string.sheet_review_info_title), message = location, onDismissRequest = onDismiss) {
        val labels = selectedNotes.map { note -> when (note) {
            ScoreNote.Rest -> stringResource(Res.string.sheet_review_info_rest)
            ScoreNote.Hold -> stringResource(Res.string.sheet_review_info_hold)
            ScoreNote.Blank -> stringResource(Res.string.sheet_review_info_blank)
            is ScoreNote.Pitch -> "${note.yul.fullName} (${note.symbol})"
            is ScoreNote.SourcePitch -> "${note.fullName} (${note.symbol})"
        } }
        Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        issues.forEach { Text(sheetReviewReason(it), style = MaterialTheme.typography.bodyMedium) }
        EolssuButton(stringResource(Res.string.common_close), onDismiss, style = EolssuButtonStyle.Secondary)
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
