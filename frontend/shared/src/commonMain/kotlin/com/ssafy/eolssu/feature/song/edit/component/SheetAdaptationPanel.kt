package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

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
import androidx.compose.ui.text.font.FontWeight
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
 * 첫 줄 왼쪽에 두 개수를 두 줄로, 오른쪽에 고른 높이를 담은 적용 버튼(`+1반음으로 적용`)을 두고(S15P31E102-1098), 아래에 추천 칩, −·슬라이더·+,
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
                    containerColor = colors.surface, borderColor = colors.surface, qaTarget = QaTarget.SHEET_ADAPTATION_PANEL_BUTTON_01)
            }
            state.selected == null -> Text(stringResource(Res.string.sheet_adapt_empty))
            else -> state.selected?.let { selected ->
                val recommended = recommendedAdaptations(state.plans).firstOrNull()
                // 왼쪽에 두 개수를 두 줄로 쌓고, 오른쪽에 고른 높이를 담은 적용 버튼 하나를 둔다(S15P31E102-1098).
                // 높이 상자와 적용 버튼을 따로 두던 한 줄(FlowRow)은 '원조 유지'나 큰 글꼴에서 적용 버튼만 다음 줄로 밀어냈다.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AdaptCount(stringResource(Res.string.sheet_adapt_octave_count), selected.octaveChanges, colors.text)
                        AdaptCount(stringResource(Res.string.sheet_adapt_unresolved_count), selected.unresolved,
                            if (selected.unresolved > 0) colors.recText else colors.text)
                    }
                    PillButton(applyLabel(selected.transpose), onApply)
                }
                // 칩 영역은 −·+ 버튼 폭만큼 안쪽에 두어 슬라이더 칸과 같은 가로 위치를 쓴다.
                RecommendationMark(recommended, thumbInset, trackCorner,
                    Modifier.fillMaxWidth().padding(horizontal = StepButtonSize))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val decreaseLabel = stringResource(Res.string.sheet_adapt_decrease)
                    SquareIconButton(SheetEditIcons.Minus, decreaseLabel, { onTranspose(selected.transpose - 1) },
                        enabled = selected.transpose > -6)
                    val sliderLabel = stringResource(Res.string.sheet_adapt_slider)
                    Slider(value = selected.transpose.toFloat(), onValueChange = UserActionLog.change(QaTarget.SHEET_ADAPTATION_PANEL_01, { onTranspose(it.roundToInt()) }),
                        valueRange = -6f..6f, steps = 11,
                        interactionSource = sliderInteractions,
                        colors = sliderColors,
                        // 앱 슬라이더와 같은 장구 손잡이(S15P31E102-970).
                        // 손잡이를 앱 기본(30dp)보다 작게 두어 바로 옆 추천 칸 점을 가리지 않는다(S15P31E102-1042).
                        thumb = { DrumThumb(held, size = AdaptThumbSize, modifier = Modifier.onSizeChanged {
                            thumbInset = with(density) { (it.width / 2f).toDp() }
                        }) },
                        track = { SliderDefaults.Track(it, modifier = Modifier.onSizeChanged { size ->
                            trackCorner = with(density) { (size.height / 2f).toDp() }
                        }, colors = sliderColors, thumbTrackGapSize = 0.dp, drawStopIndicator = null, drawTick = { offset, color ->
                            // 양끝 칸은 막대 끝 자체가 -6/+6이라 점을 그리지 않는다. 둥근 끝 밖으로 점이 삐져나오지 않게 한다.
                            val corner = size.height / 2f
                            if (offset.x > corner && offset.x < size.width - corner) {
                                val point = Offset(offset.x.roundToInt().toFloat(), offset.y)
                                val step = ((offset.x - corner) / (size.width - 2 * corner) * 12f).roundToInt() - 6
                                // 추천 칸은 칩과 같은 먹색 고리 점으로 따로 그려 손잡이가 옆에 있어도 보이게 한다.
                                if (recommended != null && step == recommended.transpose) {
                                    drawCircle(colors.text, RecommendDot.toPx(), point)
                                    drawCircle(colors.surface, RecommendDot.toPx() / 2f, point)
                                } else drawCircle(color, SliderDefaults.TickSize.toPx() / 2f, point)
                            }
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
private val AdaptThumbSize = 22.dp
private val RecommendDot = 5.dp

/** 개수 한 줄: 옅은 이름 뒤에 굵은 개수. 개수가 바뀌어도 이름 자리는 그대로라 눈이 숫자만 따라간다. */
@Composable
private fun AdaptCount(label: String, count: Int, countColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = EolssuTheme.colors.textSub, maxLines = 1)
        Text(stringResource(Res.string.sheet_adapt_count, count), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = countColor, maxLines = 1)
    }
}

/** 적용 버튼 글자. 고른 높이를 함께 적어 무엇이 적용되는지 버튼만 봐도 알게 한다. */
@Composable
private fun applyLabel(value: Int): String = if (value == 0) stringResource(Res.string.sheet_adapt_apply_original)
    else stringResource(Res.string.sheet_adapt_apply_semitones, if (value > 0) "+$value" else value.toString())

/**
 * 추천 이조를 슬라이더 칸 위의 `추천` 칩 하나로 보여 준다(S15P31E102-979).
 * 칩 아래 꼭짓점이 해당 칸을 가리키고, 양끝 칸에서도 칩을 막대 안으로 밀지 않아 꼭짓점이 칩 가운데에 남는다.
 */
@Composable
private fun RecommendationMark(plan: PitchAdaptationPlan?, thumbInset: Dp, trackCorner: Dp, modifier: Modifier) {
    val colors = EolssuTheme.colors
    if (plan == null) { Spacer(modifier.height(20.dp)); return }
    val description = stringResource(Res.string.sheet_adapt_marker_primary, transposeLabel(plan.transpose))
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 연두 막대 위에서도 묻히지 않게 먹색 칩에 추천 값을 함께 적는다(S15P31E102-1042).
                Text(description, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, color = colors.surface,
                    modifier = Modifier.background(colors.text, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp))
                Canvas(Modifier.size(MarkPointer * 2, MarkPointer)) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, colors.text)
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

private val MarkPointer = 5.dp

@Composable
internal fun transposeLabel(value: Int): String = if (value == 0) stringResource(Res.string.sheet_adapt_original_key)
    else stringResource(Res.string.sheet_adapt_semitones, if (value > 0) "+$value" else value.toString())
