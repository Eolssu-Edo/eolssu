package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetAdaptationUi
import com.ssafy.eolssu.feature.song.edit.PitchAdaptationPlan
import com.ssafy.eolssu.feature.song.edit.recommendedAdaptations
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.theme.EolssuTheme
import kotlin.math.roundToInt

/**
 * 곡 전체 높이 맞추기(S15P31E102-979·1042). 악기는 악보를 만들 때 고르므로 여기서는 보이지 않는다.
 * 첫 줄 왼쪽 흰 상자에 지금 고른 높이(`+1반음`), 오른쪽에 개수와 적용을 두고, 아래에 추천 칩, −·슬라이더·+,
 * '추천'의 뜻과 높이만으로 다 못 고칠 때 남는 음 수·다음 할 일을 쌓는다.
 */
@Composable
internal fun SheetAdaptationPanel(
    state: SheetAdaptationUi, onTranspose: (Int) -> Unit,
    onApply: () -> Unit, onRetry: () -> Unit,
) {
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
    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            state.loading -> Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.textMuted, strokeWidth = 2.dp)
            }
            state.failed -> {
                Text(stringResource(Res.string.sheet_adapt_failed), style = MaterialTheme.typography.bodySmall)
                EolssuButton(stringResource(Res.string.sheet_upload_reload), onRetry, style = EolssuButtonStyle.Secondary, small = true,
                    containerColor = colors.surface, borderColor = colors.surface)
            }
            state.selected == null -> Text(stringResource(Res.string.sheet_adapt_empty))
            else -> state.selected?.let { selected ->
                val recommended = recommendedAdaptations(state.plans).firstOrNull()
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    TransposeBox(transposeLabel(selected.transpose))
                    Spacer(Modifier.weight(1f))
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
                // 칩 영역은 −·+ 버튼 폭만큼 안쪽에 두어 슬라이더 칸과 같은 가로 위치를 쓴다.
                RecommendationMark(recommended, thumbInset, trackCorner,
                    Modifier.fillMaxWidth().padding(horizontal = StepButtonSize))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val decreaseLabel = stringResource(Res.string.sheet_adapt_decrease)
                    SquareIconButton(SheetEditIcons.Minus, decreaseLabel, { onTranspose(selected.transpose - 1) },
                        enabled = selected.transpose > -6)
                    val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
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
                            // 양끝 칸은 막대 끝 자체가 -6/+6이라 점을 그리지 않는다. 둥근 끝 밖으로 점이 삐져나오지 않게 한다.
                            val corner = size.height / 2f
                            if (offset.x > corner && offset.x < size.width - corner)
                                drawCircle(color, SliderDefaults.TickSize.toPx() / 2f, Offset(offset.x.roundToInt().toFloat(), offset.y))
                        }) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel })
                    val increaseLabel = stringResource(Res.string.sheet_adapt_increase)
                    SquareIconButton(EolssuIcons.Plus, increaseLabel, { onTranspose(selected.transpose + 1) },
                        enabled = selected.transpose < 6)
                }
                // '추천'이 무엇을 고른 것인지 한 줄로 말한다. 어느 높이로도 다 못 고치면 남는 수와 다음 할 일을 알린다.
                if (recommended != null) Text(stringResource(Res.string.sheet_adapt_recommend_help),
                    style = MaterialTheme.typography.bodySmall, color = colors.textSub)
                if (selected.unresolved > 0 && state.unavoidableUnresolved > 0) Text(
                    stringResource(Res.string.sheet_adapt_remaining_help, selected.unresolved),
                    style = MaterialTheme.typography.bodySmall, color = colors.recText)
            }
        }
    }
}

private val StepButtonSize = 48.dp

/** 지금 고른 높이를 흰 상자에 담아 슬라이더 값과 같은 말로 보인다. */
@Composable
private fun TransposeBox(label: String) {
    Box(Modifier.heightIn(min = 36.dp).background(EolssuTheme.colors.surface, RoundedCornerShape(10.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/**
 * 추천 이조를 슬라이더 칸 위의 `추천` 칩 하나로 보여 준다(S15P31E102-979).
 * 칩 아래 꼭짓점이 해당 칸을 가리키고, 양끝 칸에서도 칩을 막대 안으로 밀지 않아 꼭짓점이 칩 가운데에 남는다.
 */
@Composable
private fun RecommendationMark(plan: PitchAdaptationPlan?, thumbInset: Dp, trackCorner: Dp, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    if (plan == null) { Spacer(modifier.height(20.dp)); return }
    val description = stringResource(Res.string.sheet_adapt_marker_primary, transposeLabel(plan.transpose))
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.sheet_adapt_marker_primary_short),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, color = colors.onPrimary,
                    modifier = Modifier.background(colors.primary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp))
                Canvas(Modifier.size(MarkPointer * 2, MarkPointer)) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, colors.primary)
                }
            }
        },
        modifier = modifier.padding(horizontal = thumbInset).clearAndSetSemantics { contentDescription = description },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        layout(width, placeable.height) {
            // Native discrete sliders inset intermediate steps by the rounded track ends.
            val fraction = (plan.transpose + 6) / 12f
            val corner = trackCorner.toPx()
            val x = if (plan.transpose == -6 || plan.transpose == 6) fraction * width
                else corner + fraction * (width - 2 * corner)
            placeable.place(x.roundToInt() - placeable.width / 2, 0)
        }
    }
}

private val MarkPointer = 4.dp

@Composable
internal fun transposeLabel(value: Int): String = if (value == 0) stringResource(Res.string.sheet_adapt_original_key)
    else stringResource(Res.string.sheet_adapt_semitones, if (value > 0) "+$value" else value.toString())
