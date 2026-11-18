package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.feature.song.edit.SheetEditUiState
import com.ssafy.eolssu.feature.song.edit.SheetPitchCorrection
import com.ssafy.eolssu.feature.song.edit.pitchAlternatives
import com.ssafy.eolssu.feature.song.edit.toCommonScore
import eolssu.shared.generated.resources.*
import com.ssafy.eolssu.feature.song.edit.rangeFixGuide
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

@Composable
internal fun SheetPitchCorrectionDialog(
    state: SheetEditUiState,
    target: SheetPitchCorrection,
    onSelect: (Pair<Yul, Int>) -> Unit,
    onDismiss: () -> Unit,
    onAdapt: () -> Unit,
    reviewDetails: @Composable () -> Unit = {},
    onApplyRecommended: (() -> Unit)? = null,
) {
    val (base, octave) = target.pitch
    val name = if (octave in -2..2) ScoreNote.SourcePitch(base.hangul, octave).fullName
        else stringResource(Res.string.sheet_pitch_raw_octave, base.hangul, if (octave > 0) "+$octave" else octave.toString())
    val capability = state.authoringCapabilities?.items?.firstOrNull { it.yul == base.hangul && it.octave == octave }
    val unregistered = state.availablePitches?.contains(base.hangul to octave) == false
    EolssuDialog(
        title = stringResource(Res.string.sheet_pitch_correction_title),
        message = stringResource(when {
            state.draft.original != null && state.draft.toCommonScore().events.firstOrNull { it.id == target.eventId }?.let {
                it.yul to it.octave != base.hangul to octave
            } == true -> Res.string.sheet_pitch_notation_mismatch
            "FINGERING_UNKNOWN" in capability?.reasonCodes.orEmpty() -> Res.string.sheet_pitch_fingering_unknown
            "NOTATION_UNSUPPORTED" in capability?.reasonCodes.orEmpty() -> Res.string.sheet_pitch_notation_unsupported
            "SYNTHESIS_UNSUPPORTED" in capability?.reasonCodes.orEmpty() -> Res.string.sheet_pitch_synthesis_unsupported
            state.draft.original?.music?.hwangHz?.let { state.authoringCapabilities?.matchesTuning(it) } == false -> Res.string.sheet_pitch_tuning_mismatch
            unregistered -> Res.string.sheet_pitch_correction_unregistered
            else -> Res.string.sheet_pitch_correction_range
        }, name),
        onDismissRequest = onDismiss,
    ) {
        reviewDetails()
        // 곡 전체 높이는 편집 화면 위 안내와 같은 줄로 보여, 추천이 있으면 그 자리에서 바로 바꾼다(S15P31E102-1085).
        val rangeGuide = state.rangeFixGuide()?.takeIf { onApplyRecommended != null && !state.confirming }
        if (rangeGuide != null) SheetImportGuideCard(rangeGuide, onApplyRecommended = requireNotNull(onApplyRecommended),
            onOpenAdaptation = onAdapt, onMoveReview = {})
        else EolssuButton(stringResource(Res.string.sheet_adapt_title), onAdapt, enabled = !state.confirming, qaTarget = QaTarget.SHEET_PITCH_CORRECTION_DIALOG_BUTTON_01)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (target.isContinuation) Text(
                stringResource(Res.string.sheet_pitch_correction_hold, state.draft.columnOf(target.originCell) + 1, state.draft.rowOf(target.originCell) + 1),
                style = MaterialTheme.typography.bodySmall,
            )
            val choices = state.pitchAlternatives(target.pitch)
            Text(stringResource(if (choices.isEmpty()) Res.string.sheet_pitch_correction_no_choices else Res.string.sheet_pitch_correction_choices),
                style = MaterialTheme.typography.bodySmall)
            choices.forEach { choice ->
                val difference = choice.first.ordinal + choice.second * 12 - base.ordinal - octave * 12
                val distance = stringResource(if (difference > 0) Res.string.sheet_pitch_correction_up else Res.string.sheet_pitch_correction_down, abs(difference))
                val reason = if (choice.first == base) stringResource(Res.string.sheet_pitch_correction_same_yul, distance) else distance
                EolssuButton(
                    stringResource(Res.string.sheet_pitch_correction_choice, ScoreNote.SourcePitch(choice.first.hangul, choice.second).hangul, reason),
                    onClick = UserActionLog.click(QaTarget.SHEET_PITCH_CORRECTION_DIALOG_01, { onSelect(choice) }), enabled = !state.confirming,
                    style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_PITCH_CORRECTION_DIALOG_BUTTON_02
                )
            }
        }
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_PITCH_CORRECTION_DIALOG_BUTTON_03)
    }
}
