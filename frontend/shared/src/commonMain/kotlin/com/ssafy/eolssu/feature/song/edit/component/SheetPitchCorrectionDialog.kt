package com.ssafy.eolssu.feature.song.edit.component

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
        EolssuButton(stringResource(Res.string.sheet_adapt_title), onAdapt, enabled = !state.confirming)
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
                    onClick = { onSelect(choice) }, enabled = !state.confirming,
                    style = EolssuButtonStyle.Secondary,
                )
            }
        }
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary)
    }
}
