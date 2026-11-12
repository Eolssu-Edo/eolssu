package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetAdaptationUi
import com.ssafy.eolssu.feature.song.edit.originalPitchLayoutMatches
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.json.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetAdaptationDialog(
    state: SheetAdaptationUi, onInstrument: (String) -> Unit, onTranspose: (Int) -> Unit,
    onApply: () -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit, onLocate: (String) -> Unit,
) {
    var expanded by remember(state.selectedId, state.selectedTranspose) { mutableStateOf(false) }
    var allChoices by remember(state.selectedId) { mutableStateOf(false) }
    EolssuDialog(title = stringResource(Res.string.sheet_adapt_title), onDismissRequest = onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.instruments.forEach { instrument ->
                    FilterChip(selected = state.selectedId == instrument.instrumentId,
                        onClick = { onInstrument(instrument.instrumentId) }, label = { Text(instrument.name) })
                }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.size(24.dp))
                state.failed -> {
                    Text(stringResource(Res.string.sheet_adapt_failed), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.sheet_upload_reload)) }
                }
                else -> state.selected?.let { selected ->
                    val recognized = state.baseline.authoring?.get("source")?.jsonObject?.get("kind")?.jsonPrimitive?.content == "RECOGNIZED" &&
                        state.baseline.originalPitchLayoutMatches()
                    Text(stringResource(if (recognized) Res.string.sheet_adapt_source else Res.string.sheet_adapt_current),
                        style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (allChoices) state.plans else state.plans.take(3)).forEachIndexed { index, plan ->
                            val shift = if (plan.transpose == 0) stringResource(Res.string.sheet_adapt_original_key)
                                else stringResource(Res.string.sheet_adapt_semitones, signed(plan.transpose))
                            FilterChip(selected = selected.transpose == plan.transpose,
                                onClick = { onTranspose(plan.transpose) }, label = {
                                    Text(if (index == 0) stringResource(Res.string.sheet_adapt_recommended, shift) else shift)
                                })
                        }
                    }
                    if (!allChoices) TextButton(onClick = { allChoices = true }) { Text(stringResource(Res.string.sheet_adapt_more)) }
                    Text(stringResource(Res.string.sheet_adapt_summary, selected.octaveChanges, selected.unresolved),
                        style = MaterialTheme.typography.bodyMedium)
                    if (selected.unresolved > 0) Text(stringResource(Res.string.sheet_adapt_unresolved),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(Res.string.sheet_adapt_changes)) }
                    if (expanded) selected.notes.filter { it.target == null || it.target.midi != it.source.midi }.forEach { note ->
                        val location = note.source.cellId.removePrefix("c").split("-j")
                        val label = stringResource(Res.string.sheet_adapt_change,
                            (location.getOrNull(0)?.toIntOrNull() ?: 0) + 1,
                            (location.getOrNull(1)?.toIntOrNull() ?: 0) + 1, pitchName(note.source.midi),
                            note.target?.midi?.let(::pitchName) ?: stringResource(Res.string.sheet_adapt_needs_edit))
                        if (note.target == null) TextButton(onClick = { onLocate(note.source.cellId) }) { Text(label) }
                        else Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply), onApply,
            enabled = !state.loading && !state.failed && state.selected != null)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary)
    }
}

private fun signed(value: Int) = if (value > 0) "+$value" else value.toString()
private fun pitchName(midi: Int): String = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")[midi.mod(12)] + (kotlin.math.floor(midi / 12.0).toInt() - 1)
