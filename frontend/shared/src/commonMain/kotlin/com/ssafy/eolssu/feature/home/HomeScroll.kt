package com.ssafy.eolssu.feature.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import kotlin.math.abs
import kotlin.math.roundToInt

/** 시안의 400dp 스크롤 트랙. 픽셀 입력은 UI 경계에서 dp로 변환한다. */
internal const val HomeScrollRange = 400f

internal data class HomeScrollFrame(
    val progress: Float,
    val eased: Float,
    val bandHeight: Float,
    val groundTop: Float,
    val buttonTop: Float,
    val artScale: Float,
    val artY: Float,
    val headlineBottom: Float,
    val headlineScale: Float,
    val arrowAlpha: Float,
    val headerAlpha: Float,
    val darkBehindButton: Boolean,
)

internal fun homeScrollFrame(width: Float, height: Float, scroll: Float, buttonHeight: Float): HomeScrollFrame {
    val p = (scroll / HomeScrollRange).coerceIn(0f, 1f)
    val e = smooth(p)
    val eh = smooth((p / .6f).coerceIn(0f, 1f))
    // 낮은 가로 화면에서도 제목·버튼이 남도록 그림 띠를 줄인다.
    val band = minOf(376f, height * .62f)
    val initialTop = (height - 88f - buttonHeight).coerceAtLeast(80f)
    val top = mix(initialTop, band + 12f, e)
    val ground = mix(height, band, e)
    val scale = mix(1.65f, 1f, e)
    return HomeScrollFrame(
        progress = p, eased = e, bandHeight = band, groundTop = ground, buttonTop = top,
        artScale = scale,
        artY = mix(-200f * width / 390f, (band - width * 1448f / 1086f) * .62f, e),
        headlineBottom = mix(initialTop - 10f, band - 10f, eh),
        headlineScale = mix(1.3f, 1f, eh),
        arrowAlpha = (1f - p * 6f).coerceIn(0f, 1f),
        // 첫 화면에서는 상단 판을 숨기고, 스크롤 절반을 지나며 나타나 다 펼치면 온전히 보인다.
        headerAlpha = ((p - .5f) / .4f).coerceIn(0f, 1f),
        darkBehindButton = top + buttonHeight / 2f < ground,
    )
}

/** 그림 원본(1086×1448)의 높이/너비 비. */
internal const val HomeArtAspect = 1448f / 1086f

/**
 * 넓은 창(Expanded)에서 그림·왼쪽 무대의 배치.
 *
 * @property artScale 그림 너비 배율. 첫 화면에서도 창 높이를 덮는 최소 배율만 쓴다.
 * @property artY 그림 위쪽 위치(dp, [heroOffset] 미포함).
 * @property heroOffset 펼친 화면에서 그림 띠·제목·버튼 묶음을 내리는 양(dp). 첫 화면에서는 0이다.
 */
internal data class HomeExpandedHero(val artScale: Float, val artY: Float, val heroOffset: Float)

/**
 * 첫 화면은 펼친 화면과 같은 비율(위 62%)로 그림을 잘라 정자와 제목이 보이게 한다.
 * 펼친 화면은 왼쪽 무대 묶음(그림 띠 + 12 + 버튼)의 세로 가운데를 오른쪽 카드 묶음에 맞추되 창 아래로 넘치지 않게 한다.
 */
internal fun homeExpandedHero(
    width: Float,
    height: Float,
    heroWidth: Float,
    frame: HomeScrollFrame,
    buttonHeight: Float,
    featureTop: Float,
    featureHeight: Float,
): HomeExpandedHero {
    val e = frame.eased
    val band = frame.bandHeight
    val startScale = maxOf(1f, height / (width * HomeArtAspect))
    val startY = (height - width * startScale * HomeArtAspect) * .62f
    val endY = (band - heroWidth * HomeArtAspect) * .62f
    val heroHeight = band + 12f + buttonHeight
    val maxOffset = (height - band - buttonHeight - 58f).coerceAtLeast(0f)
    val offset = (featureTop + featureHeight / 2f - heroHeight / 2f).coerceIn(0f, maxOffset)
    return HomeExpandedHero(mix(startScale, 1f, e), mix(startY, endY, e), offset * e)
}

/** 펼친 뒤 추가 스크롤은 유지한다. 스냅 중 발생한 위치를 새 제스처로 처리하지 않는다. */
internal fun homeSnapTarget(scroll: Float, wasOpen: Boolean): Float = when {
    scroll >= HomeScrollRange -> scroll
    scroll <= 0f -> 0f
    scroll > HomeScrollRange * if (wasOpen) .85f else .12f -> HomeScrollRange
    else -> 0f
}

/**
 * 손을 뗀 [start]와 관성이 멈출 [landing]으로 무대의 스냅 목표를 정한다.
 * 무대 안에서 튕겨 관성이 카드 구간까지 가더라도 펼친 화면에서 멈춘다. 펼친 화면·카드 구간에서 시작해 카드 구간에 닿으면 null(관성 그대로)이다.
 */
internal fun homeFlingTarget(start: Float, landing: Float, wasOpen: Boolean): Float? = when {
    landing < HomeScrollRange -> homeSnapTarget(landing, wasOpen)
    start < HomeScrollRange -> HomeScrollRange
    else -> null
}

/**
 * 무대 스냅 움직임의 길이(초). 손가락 속도를 이어받되 [HomeStageMinSeconds]보다 짧게 끝내지 않아,
 * 짧게 세게 튕겨도 펼침·접힘 과정이 보인다. 느리게 놓으면 이전 스냅(500ms)과 비슷한 [HomeStageMaxSeconds]를 쓴다.
 *
 * @param distance 남은 거리(dp, 양수).
 * @param speed 목표 쪽 손가락 속도(dp/s). 반대 방향이면 0으로 본다.
 */
internal fun homeStageSeconds(distance: Float, speed: Float): Float =
    if (speed <= 0f) HomeStageMaxSeconds
    else (3f * distance / speed).coerceIn(HomeStageMinSeconds, HomeStageMaxSeconds)

/**
 * 시작 기울기가 [slope](0~3)이고 끝에서 멈추는 3차 곡선. 기울기를 손가락 속도에 맞추면 놓는 순간 속도가 이어진다.
 * 3 이하에서는 목표를 지나치지 않고, 3이면 easeOutCubic, 0이면 smoothstep이다.
 */
internal fun homeStageEasing(slope: Float): Easing {
    val m = slope.coerceIn(0f, 3f)
    return Easing { u -> m * u * (1f - u) * (1f - u) + u * u * (3f - 2f * u) }
}

/**
 * 무대(0~[HomeScrollRange])에서 손을 떼면 관성이 멈출 곳으로 스냅 목표를 먼저 정하고,
 * 손가락 속도를 이어받은 한 번의 움직임으로 목표까지 간다. 펼친 화면 이후에서 시작한 관성이 카드 구간에 닿으면 [free]로 그대로 미끄러진다.
 *
 * 기본 관성이 목표와 상관없이 무대 중간에서 멈춘 뒤 정지 상태에서 스냅을 다시 시작하던 두 번의 움직임을 하나로 합친다.
 * 첫 화면에서 짧게 세게 튕기면 기본 관성이 무대를 순식간에 지나 카드 구간까지 가서 펼침 과정이 건너뛰어졌으므로,
 * 무대 안에서 시작한 관성은 [homeStageSeconds] 동안 펼치고 펼친 화면에서 멈춘다.
 *
 * @param position 현재 스크롤 위치(px).
 * @param pxPerDp 화면 밀도. 스냅 기준은 dp, 스크롤은 px이다.
 */
internal class HomeStageFlingBehavior(
    private val position: () -> Float,
    private val pxPerDp: Float,
    private val wasOpen: () -> Boolean,
    private val reduceMotion: Boolean,
    private val decay: DecayAnimationSpec<Float>,
    private val free: FlingBehavior,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = position()
        val landing = decay.calculateTargetValue(start, initialVelocity)
        val target = homeFlingTarget(start / pxPerDp, landing / pxPerDp, wasOpen())?.let { (it * pxPerDp).roundToInt().toFloat() }
            ?: return with(free) { performFling(initialVelocity) }
        val distance = target - start
        if (distance == 0f) return 0f
        if (reduceMotion) {
            scrollBy(distance)
            return 0f
        }
        val distanceDp = abs(distance) / pxPerDp
        val speedDp = (if (distance > 0f) initialVelocity else -initialVelocity).coerceAtLeast(0f) / pxPerDp
        val seconds = homeStageSeconds(distanceDp, speedDp)
        val easing = homeStageEasing(speedDp * seconds / distanceDp)
        var applied = 0f
        AnimationState(0f).animateTo(1f, tween((seconds * 1000f).roundToInt(), easing = easing)) {
            val delta = distance * value - applied
            val consumed = scrollBy(delta)
            applied += consumed
            if (abs(delta - consumed) > .5f) cancelAnimation()
        }
        return 0f
    }
}

private const val HomeStageMinSeconds = .45f
private const val HomeStageMaxSeconds = .55f

internal fun smooth(t: Float): Float = t * t * (3f - 2f * t)
internal fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
