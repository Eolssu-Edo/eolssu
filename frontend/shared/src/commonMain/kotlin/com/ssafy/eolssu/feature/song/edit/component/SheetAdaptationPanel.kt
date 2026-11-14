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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.ssafy.eolssu.core.model.instrumentOfServerCode
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetAdaptationUi
import com.ssafy.eolssu.feature.song.edit.PitchAdaptationPlan
import com.ssafy.eolssu.feature.song.edit.recommendedAdaptations
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Keep the controls together; narrow screens put the slider below the actions. */
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleRow = maxWidth >= 500.dp * density.fontScale
        val instrumentPicker: @Composable () -> Unit = {
            Box {
                val chosen = state.instruments.firstOrNull { it.instrumentId == state.selectedId }
                TextButton(onClick = { instrumentsOpen = true }, enabled = state.instruments.isNotEmpty(), colors = textButtonColors) {
                    // 다른 악기 드롭다운처럼 고른 악기 그림을 이름 앞에 둔다(S15P31E102-831). 모르는 악기 코드는 그림 없이 이름만 둔다.
                    instrumentOfServerCode(chosen?.code)?.let {
                        InstrumentPicture(it, size = InstrumentPictureCompactSize)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(Res.string.sheet_adapt_instrument,
                        chosen?.name ?: stringResource(Res.string.sheet_adapt_choose_instrument)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = instrumentsOpen, onDismissRequest = { instrumentsOpen = false }) {
                    state.instruments.forEach { instrument ->
                        val known = instrumentOfServerCode(instrument.code)
                        DropdownMenuItem(text = { Text(instrument.name) }, onClick = {
                            instrumentsOpen = false; onInstrument(instrument.instrumentId)
                        }, leadingIcon = known?.let { { InstrumentPicture(it) } })
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    instrumentPicker()
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
                state.failed -> {
                    instrumentPicker()
                    Text(stringResource(Res.string.sheet_adapt_failed), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry, colors = textButtonColors) { Text(stringResource(Res.string.sheet_upload_reload)) }
                }
                state.selected == null -> {
                    instrumentPicker()
                    Text(stringResource(Res.string.sheet_adapt_empty))
                }
                else -> state.selected?.let { selected ->
                    val recommendations = recommendedAdaptations(state.plans)
                    val transposeControl: @Composable (Modifier) -> Unit = { modifier ->
                        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                            val decreaseLabel = stringResource(Res.string.sheet_adapt_decrease)
                            IconButton(onClick = { onTranspose(selected.transpose - 1) }, enabled = selected.transpose > -6,
                                modifier = Modifier.semantics { contentDescription = decreaseLabel }) { Text("−") }
                            val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(transposeLabel(selected.transpose), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(56.dp)) {
                                    Slider(value = selected.transpose.toFloat(), onValueChange = { onTranspose(it.roundToInt()) },
                                        valueRange = -6f..6f, steps = 11,
                                        interactionSource = sliderInteractions,
                                        thumb = { SliderDefaults.Thumb(sliderInteractions, modifier = Modifier.onSizeChanged {
                                            thumbInset = with(density) { (it.width / 2f).toDp() }
                                        }) },
                                        track = { SliderDefaults.Track(it, modifier = Modifier.onSizeChanged { size ->
                                            trackCorner = with(density) { (size.height / 2f).toDp() }
                                        }, drawStopIndicator = null, drawTick = { offset, color ->
                                            // The endpoint thumb and diamond sit at the track end, outside its rounded corner.
                                            val corner = size.height / 2f
                                            val x = when {
                                                offset.x <= corner -> 0f
                                                offset.x >= size.width - corner -> size.width
                                                else -> offset.x.roundToInt().toFloat()
                                            }
                                            drawCircle(color, SliderDefaults.TickSize.toPx() / 2f, Offset(x, offset.y))
                                        }) },
                                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).semantics { contentDescription = sliderLabel })
                                    RecommendationMarks(recommendations, thumbInset, trackCorner, Modifier.fillMaxWidth().align(Alignment.TopCenter))
                                }
                                Text(stringResource(if (recommendations.size > 1) Res.string.sheet_adapt_marker_legend
                                    else Res.string.sheet_adapt_marker_single), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1)
                            }
                            val increaseLabel = stringResource(Res.string.sheet_adapt_increase)
                            IconButton(onClick = { onTranspose(selected.transpose + 1) }, enabled = selected.transpose < 6,
                                modifier = Modifier.semantics { contentDescription = increaseLabel }) { Text("+") }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        instrumentPicker()
                        if (singleRow) transposeControl(Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        Column {
                            Text(stringResource(Res.string.sheet_adapt_octave_count, selected.octaveChanges), style = MaterialTheme.typography.labelSmall)
                            Text(stringResource(Res.string.sheet_adapt_unresolved_count, selected.unresolved), style = MaterialTheme.typography.labelSmall,
                                color = if (selected.unresolved > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                        EolssuButton(stringResource(Res.string.sheet_details_apply), onApply, small = true)
                    }
                    if (!singleRow) transposeControl(Modifier.fillMaxWidth())
                }
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
