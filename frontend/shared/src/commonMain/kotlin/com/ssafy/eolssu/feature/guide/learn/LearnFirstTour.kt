package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HolePoint
import com.ssafy.eolssu.core.model.fingering.image
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuDoneDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_tour_back
import eolssu.shared.generated.resources.learn_tour_cheong
import eolssu.shared.generated.resources.learn_tour_cheong_body
import eolssu.shared.generated.resources.learn_tour_chilseong
import eolssu.shared.generated.resources.learn_tour_chilseong_body
import eolssu.shared.generated.resources.learn_tour_done_body
import eolssu.shared.generated.resources.learn_tour_done_next
import eolssu.shared.generated.resources.learn_tour_done_title
import eolssu.shared.generated.resources.learn_tour_holes
import eolssu.shared.generated.resources.learn_tour_left_body
import eolssu.shared.generated.resources.learn_tour_mouth
import eolssu.shared.generated.resources.learn_tour_mouth_body
import eolssu.shared.generated.resources.learn_tour_right_body
import eolssu.shared.generated.resources.learn_tour_thumb_body
import kotlin.math.hypot
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * 1단계 '악기 알기' 할 일 박스. 악기 그림에서 취구 → 구멍을 박스 하나씩 동그라미로 짚으며 한 줄씩 알려 주고,
 * 다 마치면 '완료했어요!'를 띄운 뒤 다음 단계로 간다. 악기 부분 이름·손 위치는 운지 그림(구멍 좌표)과 같은 앱 쪽 안내라 여기에 둔다.
 */

/**
 * 짚어 줄 한 곳.
 *
 * @param title 이름(예: 취구). [titleArg]가 있으면 `%1$s` 자리에 넣는다(예: 1·2공).
 * @param points 동그라미를 그릴 곳(악기 그림 원본 픽셀).
 * @param grouped 붙어 있는 구멍을 하나로 감싸 짚는다(정악대금 칠성공).
 */
internal class TourSpot(
    val title: StringResource,
    val body: StringResource,
    val points: List<HolePoint>,
    val titleArg: String? = null,
    val grouped: Boolean = false,
)

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
                TourSpot(Res.string.learn_tour_chilseong, Res.string.learn_tour_chilseong_body, listOf(HolePoint(1795, 323), HolePoint(1875, 323)), grouped = true),
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
 * 할 일 박스 진행(S15P31E102-688). 처음부터 1번 박스가 열려 있고, '해 봤어요'로 앞에서부터 마치면 다음 박스가 열린다.
 * 마친 박스는 다시 펼쳐 볼 수 있다. [celebrating]이면 '완료했어요!'를 띄운다. 화면 회전 뒤에도 이어지도록 저장한다.
 *
 * @property open 펼친 박스. null이면 모두 접혀 있다.
 * @property doneCount 앞에서부터 마친 박스 수. 그다음 박스가 지금 할 일이고, 그 뒤는 아직 열리지 않는다.
 */
@Stable
internal class LearnTour(
    private val openState: MutableState<Int?> = mutableStateOf(FirstOpenBox),
    private val doneState: MutableState<Int> = mutableStateOf(0),
    private val celebrateState: MutableState<Boolean> = mutableStateOf(false),
) {
    val open: Int? get() = openState.value
    val doneCount: Int get() = doneState.value
    val celebrating: Boolean get() = celebrateState.value

    fun isDone(index: Int): Boolean = index < doneCount

    fun allDone(count: Int): Boolean = count > 0 && doneCount >= count

    /** [index]번 박스를 '해 봤어요': 마침으로 남기고 아직 안 한 첫 박스를 연다(없으면 모두 접는다). 다 마쳤으면 true. 열리지 않은 박스는 무시한다. */
    fun complete(index: Int, count: Int): Boolean {
        if (index > doneCount || index >= count) return false
        doneState.value = maxOf(doneCount, index + 1)
        openState.value = doneCount.takeIf { it < count }
        return allDone(count)
    }

    /** 마친 박스·지금 박스를 펼치거나 접는다. 열리지 않은 박스는 무시한다(바로 펼친 박스는 접을 수 있다). */
    fun toggle(index: Int) {
        if (index > doneCount && open != index) return
        openState.value = if (open == index) null else index
    }

    /** 앞 박스를 마치지 않아도 [index]번 박스를 바로 펼친다(아래 '연습 시작'이 연습 박스를 연다). 앞 박스는 마침으로 남기지 않는다. */
    fun jumpTo(index: Int) {
        openState.value = index
    }

    /**
     * 마친 [index]번 박스의 체크를 푼다(S15P31E102-749). 앞에서부터 차례로 하므로 그 뒤 박스도 함께 다시 할 일이 되고,
     * 그 박스를 펼친다. '완료했어요!'는 거둔다. 마치지 않은 박스면 false.
     */
    fun undo(index: Int): Boolean {
        if (index < 0 || index >= doneCount) return false
        doneState.value = index
        openState.value = index
        celebrateState.value = false
        return true
    }

    /**
     * 마지막 연습 박스를 통과했다(3·4·7단계, S15P31E102-1030). 할 일 [count]개를 모두 마침으로 남겨 진행 막대를 채운다.
     * 통과 문구를 보도록 펼친 박스는 그대로 둔다.
     */
    fun passPractice(count: Int) {
        if (count > doneCount) doneState.value = count
    }

    fun finish() { celebrateState.value = true }
    fun dismiss() { celebrateState.value = false }
}

private const val FirstOpenBox = 0

/**
 * 단계마다의 할 일 진행. [saved]로 지난번에 마친 박스 수를 불러와 그다음 박스를 펼친 채 시작하고,
 * 마친 박스 수가 바뀔 때마다 [onSave]로 남긴다(S15P31E102-749). 단계나 [owner](악기)가 바뀌면 그 단계의 저장된 진행으로 바꾼다.
 */
@Composable
internal fun rememberLearnTour(
    stage: Int,
    owner: Any? = null,
    saved: () -> Int = { 0 },
    onSave: (Int) -> Unit = {},
): LearnTour {
    val done = rememberSaveable(stage, owner) { mutableStateOf(saved().coerceAtLeast(0)) }
    val open = rememberSaveable(stage, owner) { mutableStateOf<Int?>(done.value) }
    val celebrate = rememberSaveable(stage, owner) { mutableStateOf(false) }
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(done) { snapshotFlow { done.value }.drop(1).collect { save(it) } }
    return remember(open, done, celebrate) { LearnTour(open, done, celebrate) }
}

/** 1단계 할 일 박스: 짚을 곳마다 박스 하나. 펼치면 악기 그림에서 그곳을 동그라미로 짚고 한 줄 설명을 보인다. */
@Composable
internal fun LearnFirstTourFigure(instrument: GuideInstrument, tour: LearnTour, imageMaxWidth: Dp = Dp.Unspecified, onAllDone: () -> Unit = {}) {
    val spots = instrument.tourSpots
    val tasks = spots.map { LearnTask(spotTitle(it), stringResource(it.body)) }
    TourPanel(tasks, tour, onAllDone) { s ->
        TourInstrumentImage(instrument, imageMaxWidth = imageMaxWidth) { SpotRings(instrument, spots[s]) }
    }
}

/** 악기 그림. 그림이 Fit으로 들어가 칸과 비율이 같으므로 [overlay]는 칸 크기에 바로 맞춰 그린다. */
@Composable
internal fun TourInstrumentImage(
    instrument: GuideInstrument,
    modifier: Modifier = Modifier,
    imageMaxWidth: Dp = Dp.Unspecified,
    overlay: @Composable () -> Unit = {},
) {
    Box(
        modifier
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
        overlay()
    }
}

@Composable
private fun spotTitle(spot: TourSpot): String = if (spot.titleArg != null) stringResource(spot.title, spot.titleArg) else stringResource(spot.title)

/**
 * 짚는 곳 동그라미의 반지름(px). 구멍보다 넉넉히 크게 하되 작은 화면에서도 [minRadius]보다 작아지지 않고,
 * 한 번에 짚는 구멍끼리는 서로 겹치지 않도록 가장 가까운 두 구멍 사이의 절반에서 [gap]을 뺀 만큼으로 줄인다.
 *
 * @param scale 그림 원본 픽셀 → 화면 픽셀 비율.
 */
internal fun spotRingRadius(points: List<HolePoint>, holeRadius: Int, scale: Float, minRadius: Float, gap: Float): Float {
    val base = maxOf(holeRadius * 1.9f * scale, minRadius)
    val spacing = points.zipWithNext { a, b -> hypot((a.x - b.x).toFloat(), (a.y - b.y).toFloat()) }.minOrNull() ?: return base
    return minOf(base, spacing * scale / 2 - gap)
}

/**
 * 짚는 곳의 깜빡이며 퍼지는 동그라미. 그림이 Fit으로 들어가 칸과 비율이 같으므로 칸 크기에 바로 맞춘다.
 * [TourSpot.grouped]이면(붙어 있는 정악대금 칠성공 두 구멍) 동그라미 대신 둘을 함께 감싸는 둥근 띠 하나를 그린다.
 */
@Composable
internal fun SpotRings(instrument: GuideInstrument, spot: TourSpot, color: Color = EolssuTheme.colors.rec) {
    val pulse by rememberInfiniteTransition(label = "spotPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "spotPulseValue",
    )
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val k = size.width / instrument.imageWidth
        val stroke = 3.dp.toPx()
        val minRadius = 14.dp.toPx()
        val halo = color.copy(alpha = 0.35f * (1f - pulse))
        if (spot.grouped) {
            val r = maxOf(instrument.holeRadius * 1.9f * k, minRadius)
            val left = spot.points.minOf { it.x } * k - r
            val right = spot.points.maxOf { it.x } * k + r
            val cy = spot.points.map { it.y }.average().toFloat() * k
            val grow = 0.7f * r * pulse
            drawRoundRect(halo, Offset(left - grow, cy - r - grow), Size(right - left + 2 * grow, 2 * (r + grow)), CornerRadius(r + grow))
            drawRoundRect(color, Offset(left, cy - r), Size(right - left, 2 * r), CornerRadius(r), style = Stroke(stroke))
            return@Canvas
        }
        val r = spotRingRadius(spot.points, instrument.holeRadius, k, minRadius, gap = stroke / 2 + 1.dp.toPx())
        spot.points.forEach { p ->
            val c = Offset(p.x * k, p.y * k)
            drawCircle(halo, radius = r * (1f + 0.7f * pulse), center = c)
            drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        }
    }
}

/** '완료했어요!': 호랑이가 통통 튀어 오르고 [body]로 익힌 것을 알린다. '다음 단계로'를 누르면 [onNext](완료 표시 뒤 다음 단계)를 부른다. */
@Composable
internal fun LearnTourDoneDialog(tour: LearnTour, body: StringResource = Res.string.learn_tour_done_body, onNext: () -> Unit) {
    if (!tour.celebrating) return
    LearnDoneDialog(body, onDismiss = tour::dismiss, onNext = onNext)
}

/** 따라 배우기 밖(단계 연습 통과 등)에서 띄우는 같은 '완료했어요!' 창. */
@Composable
internal fun LearnDoneDialog(body: StringResource, onDismiss: () -> Unit, onNext: () -> Unit, action: StringResource = Res.string.learn_tour_done_next) {
    EolssuDoneDialog(
        title = stringResource(Res.string.learn_tour_done_title),
        body = stringResource(body),
        action = stringResource(action),
        onDismiss = onDismiss,
        onAction = onNext,
    )
}
