package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HolePoint
import com.ssafy.eolssu.core.model.fingering.image
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_tiger
import eolssu.shared.generated.resources.learn_tour_back
import eolssu.shared.generated.resources.learn_tour_cheong
import eolssu.shared.generated.resources.learn_tour_cheong_body
import eolssu.shared.generated.resources.learn_tour_chilseong
import eolssu.shared.generated.resources.learn_tour_chilseong_body
import eolssu.shared.generated.resources.learn_tour_done_body
import eolssu.shared.generated.resources.learn_tour_done_next
import eolssu.shared.generated.resources.learn_tour_done_title
import eolssu.shared.generated.resources.learn_tour_finish
import eolssu.shared.generated.resources.learn_tour_holes
import eolssu.shared.generated.resources.learn_tour_left_body
import eolssu.shared.generated.resources.learn_tour_mouth
import eolssu.shared.generated.resources.learn_tour_mouth_body
import eolssu.shared.generated.resources.learn_tour_next
import eolssu.shared.generated.resources.learn_tour_prev
import eolssu.shared.generated.resources.learn_tour_right_body
import eolssu.shared.generated.resources.learn_tour_start
import eolssu.shared.generated.resources.learn_tour_step
import eolssu.shared.generated.resources.learn_tour_thumb_body
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * 1단계 '악기 알기' 따라 배우기. '학습 시작'을 누르면 악기 그림에서 취구 → 구멍을 하나씩 동그라미로 짚으며 한 줄씩 알려 주고,
 * 다 보면 '완료했어요!'를 띄운 뒤 다음 단계로 간다. 악기 부분 이름·손 위치는 운지 그림(구멍 좌표)과 같은 앱 쪽 안내라 여기에 둔다.
 */

/**
 * 짚어 줄 한 곳.
 *
 * @param title 이름(예: 취구). [titleArg]가 있으면 `%1$s` 자리에 넣는다(예: 1·2공).
 * @param points 동그라미를 그릴 곳(악기 그림 원본 픽셀).
 */
internal class TourSpot(val title: StringResource, val body: StringResource, val points: List<HolePoint>, val titleArg: String? = null)

/** 악기마다 짚어 줄 곳. 순서대로 취구 → (청공) → 구멍 → (칠성공). */
internal val GuideInstrument.tourSpots: List<TourSpot>
    get() {
        val h = holes
        return when (this) {
            GuideInstrument.Danso -> listOf(
                TourSpot(Res.string.learn_tour_mouth, Res.string.learn_tour_mouth_body, listOf(HolePoint(82, 354))),
                TourSpot(Res.string.learn_tour_back, Res.string.learn_tour_thumb_body, listOf(h[0])),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_left_body, listOf(h[1], h[2]), "1·2"),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_right_body, listOf(h[3], h[4]), "3·4"),
            )
            GuideInstrument.Sogeum -> listOf(
                TourSpot(Res.string.learn_tour_mouth, Res.string.learn_tour_mouth_body, listOf(HolePoint(206, 330))),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_left_body, h.take(3), "1·2·3"),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_right_body, h.drop(3), "4·5·6"),
                TourSpot(Res.string.learn_tour_chilseong, Res.string.learn_tour_chilseong_body, listOf(HolePoint(1818, 330))),
            )
            GuideInstrument.DaegeumJeongak -> listOf(
                TourSpot(Res.string.learn_tour_mouth, Res.string.learn_tour_mouth_body, listOf(HolePoint(170, 323))),
                TourSpot(Res.string.learn_tour_cheong, Res.string.learn_tour_cheong_body, listOf(HolePoint(610, 323))),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_left_body, h.take(3), "1·2·3"),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_right_body, h.drop(3), "4·5·6"),
                TourSpot(Res.string.learn_tour_chilseong, Res.string.learn_tour_chilseong_body, listOf(HolePoint(1795, 323), HolePoint(1875, 323))),
            )
            GuideInstrument.DaegeumSanjo -> listOf(
                TourSpot(Res.string.learn_tour_mouth, Res.string.learn_tour_mouth_body, listOf(HolePoint(184, 332))),
                TourSpot(Res.string.learn_tour_cheong, Res.string.learn_tour_cheong_body, listOf(HolePoint(572, 332))),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_left_body, h.take(3), "1·2·3"),
                TourSpot(Res.string.learn_tour_holes, Res.string.learn_tour_right_body, h.drop(3), "4·5·6"),
                TourSpot(Res.string.learn_tour_chilseong, Res.string.learn_tour_chilseong_body, listOf(HolePoint(1826, 332))),
            )
        }
    }

/**
 * 따라 배우기 진행. [step]이 null이면 시작 전이고, [celebrating]이면 '완료했어요!'를 띄운다.
 * 화면 회전 뒤에도 이어지도록 저장한다.
 */
@Stable
internal class LearnTour(private val stepState: MutableState<Int?>, private val celebrateState: MutableState<Boolean>) {
    val step: Int? get() = stepState.value
    val celebrating: Boolean get() = celebrateState.value
    val started: Boolean get() = stepState.value != null

    fun start() { stepState.value = 0 }
    fun next(last: Int) { stepState.value = ((stepState.value ?: -1) + 1).coerceAtMost(last) }
    fun prev() { stepState.value = ((stepState.value ?: 0) - 1).coerceAtLeast(0) }
    fun finish() { celebrateState.value = true }
    fun dismiss() { celebrateState.value = false }
}

/** [stage]가 바뀌면(가로 배치에서 다른 단계를 고름) 처음부터 다시 한다. */
@Composable
internal fun rememberLearnTour(stage: Int): LearnTour {
    val step = rememberSaveable(stage) { mutableStateOf<Int?>(null) }
    val celebrate = rememberSaveable(stage) { mutableStateOf(false) }
    return remember(step, celebrate) { LearnTour(step, celebrate) }
}

/** 1단계 본문: 시작 전에는 악기 그림과 부분 이름, 시작하면 짚는 곳에 동그라미와 한 줄 설명, 지나온 곳은 체크로 쌓인다. */
@Composable
internal fun LearnFirstTourFigure(instrument: GuideInstrument, tour: LearnTour, imageMaxWidth: Dp = Dp.Unspecified) {
    val colors = EolssuTheme.colors
    val spots = instrument.tourSpots
    val step = tour.step?.coerceIn(0, spots.lastIndex)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, if (step != null) colors.accentTintLine else colors.lineSoft, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = imageMaxWidth)
                .fillMaxWidth()
                .aspectRatio(instrument.imageWidth.toFloat() / instrument.imageHeight),
        ) {
            Image(
                painterResource(instrument.image),
                contentDescription = stringResource(instrument.label),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (step != null) SpotRings(instrument, spots[step].points)
        }
        // 시작 전에는 부분 이름만, 시작하면 지나온 곳은 체크, 지금 곳은 연두 테두리, 남은 곳은 옅게.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            spots.forEachIndexed { i, spot ->
                SpotChip(spotTitle(spot), done = step != null && i < step, current = i == step, muted = step != null)
            }
        }
        if (step == null) return@Column
        AnimatedContent(step, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tourCaption") { s ->
            val spot = spots[s]
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.accentSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(Res.string.learn_tour_step, s + 1, spots.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.accentText,
                )
                Text(spotTitle(spot), style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Text(stringResource(spot.body), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
        }
    }
}

@Composable
private fun spotTitle(spot: TourSpot): String = if (spot.titleArg != null) stringResource(spot.title, spot.titleArg) else stringResource(spot.title)

/** 짚는 곳의 깜빡이며 퍼지는 동그라미. 그림이 Fit으로 들어가 칸과 비율이 같으므로 칸 크기에 바로 맞춘다. */
@Composable
private fun SpotRings(instrument: GuideInstrument, points: List<HolePoint>) {
    val colors = EolssuTheme.colors
    val pulse by rememberInfiniteTransition(label = "spotPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "spotPulseValue",
    )
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val k = size.width / instrument.imageWidth
        val base = (instrument.holeRadius * 1.9f * k).coerceAtLeast(14.dp.toPx())
        points.forEach { p ->
            val c = Offset(p.x * k, p.y * k)
            drawCircle(colors.rec.copy(alpha = 0.35f * (1f - pulse)), radius = base * (1f + 0.7f * pulse), center = c)
            drawCircle(colors.rec, radius = base, center = c, style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun SpotChip(text: String, done: Boolean, current: Boolean, muted: Boolean) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .height(26.dp)
            .background(if (done) colors.primary else colors.surface, shape)
            .border(if (current) 2.dp else 1.dp, if (current) colors.play else colors.lineSoft, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (done) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(11.dp), tint = colors.onPrimary)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal),
            color = when {
                done -> colors.onPrimary
                current || !muted -> colors.text
                else -> colors.textMuted
            },
        )
    }
}

/** 1단계 아래 버튼: 시작 전 '학습 시작', 진행 중 '이전 · 다음', 마지막 곳에서 '다 익혔어요'. */
@Composable
internal fun LearnFirstTourBar(instrument: GuideInstrument, tour: LearnTour) {
    val last = instrument.tourSpots.lastIndex
    val step = tour.step
    if (step == null) {
        EolssuButton(stringResource(Res.string.learn_tour_start), tour::start, Modifier.fillMaxWidth(), icon = EolssuIcons.Play)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EolssuButton(
            stringResource(Res.string.learn_tour_prev),
            tour::prev,
            Modifier.weight(1f),
            style = EolssuButtonStyle.Secondary,
            enabled = step > 0,
        )
        if (step < last) {
            EolssuButton(stringResource(Res.string.learn_tour_next), { tour.next(last) }, Modifier.weight(1f))
        } else {
            EolssuButton(stringResource(Res.string.learn_tour_finish), tour::finish, Modifier.weight(1f), icon = EolssuIcons.Check)
        }
    }
}

/** '완료했어요!': 호랑이가 통통 튀어 오르고, '다음 단계로'를 누르면 1단계를 완료로 표시하고 2단계로 간다. */
@Composable
internal fun LearnTourDoneDialog(tour: LearnTour, onNext: () -> Unit) {
    if (!tour.celebrating) return
    val colors = EolssuTheme.colors
    val jump = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        jump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 250f))
    }
    Dialog(onDismissRequest = tour::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).background(colors.accentSoft, CircleShape))
                Image(
                    painterResource(Res.drawable.home_tiger),
                    contentDescription = null,
                    modifier = Modifier
                        .size(132.dp, 108.dp)
                        .graphicsLayer {
                            scaleX = 0.6f + 0.4f * jump.value
                            scaleY = 0.6f + 0.4f * jump.value
                            translationY = (1f - jump.value) * 24.dp.toPx()
                        },
                )
            }
            Text(
                stringResource(Res.string.learn_tour_done_title),
                style = EolssuTheme.serif.yul.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.learn_tour_done_body),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            EolssuButton(
                stringResource(Res.string.learn_tour_done_next),
                onClick = {
                    tour.dismiss()
                    onNext()
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}
