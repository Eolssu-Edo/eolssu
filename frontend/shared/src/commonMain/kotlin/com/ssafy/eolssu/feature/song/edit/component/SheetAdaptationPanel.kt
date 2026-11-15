package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
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
    val colors = EolssuTheme.colors
    val held = sliderInteractions.collectIsDraggedAsState().value || sliderInteractions.collectIsPressedAsState().value
    // 앱 슬라이더(재생 위치·속도)와 같은 연두 길·옅은 회색 남은 길(S15P31E102-970).
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceStrong,
        activeTickColor = colors.surface, inactiveTickColor = colors.lineStrong,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleRow = maxWidth >= 500.dp * density.fontScale
        val instrumentPicker: @Composable () -> Unit = {
            Box {
                val chosen = state.instruments.firstOrNull { it.instrumentId == state.selectedId }
                val enabled = state.instruments.isNotEmpty()
                // 다른 악기 드롭다운과 같은 흰 알약(36dp, 누르는 영역 48dp)과 앱 메뉴 창(S15P31E102-970).
                Surface(
                    onClick = { instrumentsOpen = true },
                    enabled = enabled,
                    modifier = Modifier.height(36.dp).alpha(if (enabled) 1f else 0.45f).semantics { role = Role.DropdownList },
                    shape = CircleShape,
                    color = colors.surface,
                    contentColor = colors.text,
                    border = BorderStroke(1.dp, colors.lineSoft),
                ) {
                    Row(Modifier.padding(start = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 고른 악기 그림을 이름 앞에 둔다(S15P31E102-831). 모르는 악기 코드는 그림 없이 이름만 둔다.
                        instrumentOfServerCode(chosen?.code)?.let { InstrumentPicture(it, size = InstrumentPictureCompactSize) }
                        Text(chosen?.name ?: stringResource(Res.string.sheet_adapt_choose_instrument),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(EolssuIcons.ChevronDown, contentDescription = null,
                            modifier = Modifier.size(12.dp).rotate(if (instrumentsOpen) 180f else 0f), tint = colors.textMuted)
                    }
                }
                if (instrumentsOpen) {
                    EolssuMenuPopup(onDismissRequest = { instrumentsOpen = false }) {
                        EolssuMenuPanel(width = 200.dp) {
                            state.instruments.forEach { instrument ->
                                val known = instrumentOfServerCode(instrument.code)
                                EolssuMenuItem(instrument.name, instrument.instrumentId == state.selectedId, onClick = {
                                    instrumentsOpen = false; onInstrument(instrument.instrumentId)
                                }, leading = known?.let { { InstrumentPicture(it) } })
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    instrumentPicker()
                    CircularProgressIndicator(Modifier.size(24.dp), color = colors.textMuted, strokeWidth = 2.dp)
                }
                state.failed -> {
                    instrumentPicker()
                    Text(stringResource(Res.string.sheet_adapt_failed), style = MaterialTheme.typography.bodySmall)
                    EolssuButton(stringResource(Res.string.sheet_upload_reload), onRetry, style = EolssuButtonStyle.Secondary, small = true,
                        containerColor = colors.surface, borderColor = colors.surface)
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
                            SquareIconButton(SheetEditIcons.Minus, decreaseLabel, { onTranspose(selected.transpose - 1) },
                                enabled = selected.transpose > -6)
                            val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(transposeLabel(selected.transpose), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(56.dp)) {
                                    Slider(value = selected.transpose.toFloat(), onValueChange = { onTranspose(it.roundToInt()) },
                                        valueRange = -6f..6f, steps = 11,
                                        interactionSource = sliderInteractions,
                                        colors = sliderColors,
                                        // 앱 슬라이더와 같은 장구 손잡이(S15P31E102-970).
                                        thumb = { DrumThumb(held, modifier = Modifier.onSizeChanged {
                                            thumbInset = with(density) { (it.width / 2f).toDp() }
                                        }) },
                                        track = { SliderDefaults.Track(it, modifier = Modifier.onSizeChanged { size ->
                                            trackCorner = with(density) { (size.height / 2f).toDp() }
                                        }, colors = sliderColors, thumbTrackGapSize = 0.dp, drawStopIndicator = null, drawTick = { offset, color ->
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
                            SquareIconButton(EolssuIcons.Plus, increaseLabel, { onTranspose(selected.transpose + 1) },
                                enabled = selected.transpose < 6)
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        instrumentPicker()
                        if (singleRow) transposeControl(Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        // 두 개수를 한 줄에 나란히 두고, 적용은 작은 네모 버튼(32dp)으로 둔다(S15P31E102-889).
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.sheet_adapt_octave_count, selected.octaveChanges), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1)
                            Text("·", style = MaterialTheme.typography.labelMedium, color = colors.lineStrong)
                            Text(stringResource(Res.string.sheet_adapt_unresolved_count, selected.unresolved), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                color = if (selected.unresolved > 0) colors.recText else colors.text)
                        }
                        PillButton(stringResource(Res.string.sheet_details_apply), onApply, compact = true)
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
    val color = EolssuTheme.colors.text
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
