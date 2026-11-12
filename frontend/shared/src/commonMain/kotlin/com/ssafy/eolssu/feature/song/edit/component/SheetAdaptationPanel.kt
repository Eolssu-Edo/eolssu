package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetAdaptationUi
import com.ssafy.eolssu.feature.song.edit.PitchAdaptationPlan
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
    val sliderInteractions = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var thumbInset by remember { mutableStateOf(10.dp) }
    var trackCorner by remember { mutableStateOf(8.dp) }
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    Column(Modifier.fillMaxWidth().height(200.dp).verticalScroll(rememberScrollState()),
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(transposeLabel(selected.transpose), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 64.dp))
                    val decreaseLabel = stringResource(Res.string.sheet_adapt_decrease)
                    IconButton(onClick = { onTranspose(selected.transpose - 1) }, enabled = selected.transpose > -6,
                        modifier = Modifier.semantics { contentDescription = decreaseLabel }) { Text("−") }
                    val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(56.dp)) {
                            Slider(value = selected.transpose.toFloat(), onValueChange = { onTranspose(it.roundToInt()) },
                                valueRange = -6f..6f, steps = 11,
                                interactionSource = sliderInteractions,
                                thumb = { SliderDefaults.Thumb(sliderInteractions, modifier = Modifier.onSizeChanged {
                                    thumbInset = with(density) { (it.width / 2f).toDp() }
                                }) },
                                track = { SliderDefaults.Track(it, modifier = Modifier.onSizeChanged { size ->
                                    trackCorner = with(density) { (size.height / 2f).toDp() }
                                }) },
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).semantics { contentDescription = sliderLabel })
                            RecommendationMarks(recommendations, thumbInset, trackCorner, Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("−6", style = MaterialTheme.typography.labelSmall)
                            Text("0", style = MaterialTheme.typography.labelSmall)
                            Text("+6", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    val increaseLabel = stringResource(Res.string.sheet_adapt_increase)
                    IconButton(onClick = { onTranspose(selected.transpose + 1) }, enabled = selected.transpose < 6,
                        modifier = Modifier.semantics { contentDescription = increaseLabel }) { Text("+") }
                }
                Text(stringResource(if (recommendations.size > 1) Res.string.sheet_adapt_marker_legend
                    else Res.string.sheet_adapt_marker_single), style = MaterialTheme.typography.labelSmall)
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
}

@Composable
private fun RecommendationMarks(plans: List<PitchAdaptationPlan>, thumbInset: Dp, trackCorner: Dp, modifier: Modifier) {
    val description = plans.mapIndexed { index, plan ->
        stringResource(if (index == 0) Res.string.sheet_adapt_marker_primary else Res.string.sheet_adapt_marker_alternative,
            transposeLabel(plan.transpose))
    }.joinToString(" · ")
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.height(12.dp).padding(horizontal = thumbInset).semantics { contentDescription = description }) {
        val radius = 4.dp.toPx()
        plans.forEachIndexed { index, plan ->
            // Native discrete sliders inset intermediate steps by the rounded track ends.
            val fraction = (plan.transpose + 6) / 12f
            val corner = trackCorner.toPx()
            val x = if (plan.transpose == -6 || plan.transpose == 6) fraction * size.width
                else (corner + fraction * (size.width - 2 * corner)).roundToInt().toFloat()
            val y = size.height / 2
            val diamond = Path().apply {
                moveTo(x, y - radius); lineTo(x + radius, y); lineTo(x, y + radius); lineTo(x - radius, y); close()
            }
            if (index == 0) drawPath(diamond, color)
            else drawPath(diamond, color, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun transposeLabel(value: Int): String = if (value == 0) stringResource(Res.string.sheet_adapt_original_key)
    else stringResource(Res.string.sheet_adapt_semitones, if (value > 0) "+$value" else value.toString())
