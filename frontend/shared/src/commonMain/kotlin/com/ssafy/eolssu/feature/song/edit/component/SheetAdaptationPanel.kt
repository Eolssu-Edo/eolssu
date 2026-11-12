package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetAdaptationUi
import com.ssafy.eolssu.feature.song.edit.recommendedAdaptations
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Fixed input-panel footprint; expanded text and narrow screens scroll inside it. */
@Composable
internal fun SheetAdaptationPanel(
    state: SheetAdaptationUi, onInstrument: (String) -> Unit, onTranspose: (Int) -> Unit,
    onApply: () -> Unit, onRetry: () -> Unit,
) {
    var instrumentsOpen by remember { mutableStateOf(false) }
    var criteriaOpen by remember { mutableStateOf(false) }
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    Column(Modifier.fillMaxWidth().height(232.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { instrumentsOpen = true }, enabled = state.instruments.isNotEmpty(), colors = textButtonColors) {
                    Text(stringResource(Res.string.sheet_adapt_instrument,
                        state.instruments.firstOrNull { it.instrumentId == state.selectedId }?.name
                            ?: stringResource(Res.string.sheet_adapt_choose_instrument)))
                }
                DropdownMenu(expanded = instrumentsOpen, onDismissRequest = { instrumentsOpen = false }) {
                    state.instruments.forEach { instrument ->
                        DropdownMenuItem(text = { Text(instrument.name) }, onClick = {
                            instrumentsOpen = false; onInstrument(instrument.instrumentId)
                        })
                    }
                }
            }
            TextButton(onClick = { criteriaOpen = true }, colors = textButtonColors) { Text(stringResource(Res.string.sheet_adapt_criteria)) }
        }
        when {
            state.loading -> CircularProgressIndicator(Modifier.size(24.dp))
            state.failed -> {
                Text(stringResource(Res.string.sheet_adapt_failed), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry, colors = textButtonColors) { Text(stringResource(Res.string.sheet_upload_reload)) }
            }
            state.selected == null -> Text(stringResource(Res.string.sheet_adapt_empty))
            else -> state.selected?.let { selected ->
                val recommendations = recommendedAdaptations(state.plans)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recommendations.forEachIndexed { index, plan ->
                        val reason = stringResource(when {
                            recommendations.size == 1 -> Res.string.sheet_adapt_both_minimum
                            index == 0 -> Res.string.sheet_adapt_octave_minimum
                            else -> Res.string.sheet_adapt_key_minimum
                        })
                        FilterChip(selected = selected.transpose == plan.transpose, onClick = { onTranspose(plan.transpose) },
                            label = { Text(stringResource(Res.string.sheet_adapt_choice, transposeLabel(plan.transpose), reason)) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(transposeLabel(selected.transpose), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 64.dp))
                    val decreaseLabel = stringResource(Res.string.sheet_adapt_decrease)
                    IconButton(onClick = { onTranspose(selected.transpose - 1) }, enabled = selected.transpose > -6,
                        modifier = Modifier.semantics { contentDescription = decreaseLabel }) { Text("−") }
                    val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
                    Slider(value = selected.transpose.toFloat(), onValueChange = { onTranspose(it.roundToInt()) },
                        valueRange = -6f..6f, steps = 11, modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel })
                    val increaseLabel = stringResource(Res.string.sheet_adapt_increase)
                    IconButton(onClick = { onTranspose(selected.transpose + 1) }, enabled = selected.transpose < 6,
                        modifier = Modifier.semantics { contentDescription = increaseLabel }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.sheet_adapt_octave_count, selected.octaveChanges), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(Res.string.sheet_adapt_unresolved_count, selected.unresolved), style = MaterialTheme.typography.bodySmall,
                            color = if (selected.unresolved > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    EolssuButton(stringResource(Res.string.sheet_details_apply), onApply, small = true)
                }
                if (selected.unresolved > 0) Text(stringResource(Res.string.sheet_adapt_unresolved), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (criteriaOpen) EolssuDialog(title = stringResource(Res.string.sheet_adapt_criteria_title),
        message = stringResource(Res.string.sheet_adapt_criteria_message), onDismissRequest = { criteriaOpen = false }) {
        EolssuButton(stringResource(Res.string.common_confirm), { criteriaOpen = false })
    }
}

@Composable
private fun transposeLabel(value: Int): String = if (value == 0) stringResource(Res.string.sheet_adapt_original_key)
    else stringResource(Res.string.sheet_adapt_semitones, if (value > 0) "+$value" else value.toString())
