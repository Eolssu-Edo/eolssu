package com.ssafy.eolssu.feature.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
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

/** 펼친 뒤 추가 스크롤은 유지한다. 스냅 중 발생한 위치를 새 제스처로 처리하지 않는다. */
internal fun homeSnapTarget(scroll: Float, wasOpen: Boolean): Float = when {
    scroll >= HomeScrollRange -> scroll
    scroll <= 0f -> 0f
    scroll > HomeScrollRange * if (wasOpen) .85f else .12f -> HomeScrollRange
    else -> 0f
}

/** 손을 뗀 뒤 관성이 멈출 [landing]으로 무대의 스냅 목표를 정한다. 펼친 뒤 카드 구간에 닿으면 null(관성 그대로)이다. */
internal fun homeFlingTarget(landing: Float, wasOpen: Boolean): Float? =
    if (landing >= HomeScrollRange) null else homeSnapTarget(landing, wasOpen)

/**
 * 무대(0~[HomeScrollRange]) 안에서 손을 떼면 관성이 멈출 곳으로 스냅 목표를 먼저 정하고,
 * 손가락 속도를 이어받은 한 번의 움직임으로 목표까지 간다. 관성이 카드 구간에 닿으면 [free]로 그대로 미끄러진다.
 *
 * 기본 관성이 목표와 상관없이 무대 중간에서 멈춘 뒤 정지 상태에서 스냅을 다시 시작하던 두 번의 움직임을 하나로 합친다.
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
        val target = homeFlingTarget(landing / pxPerDp, wasOpen())?.let { (it * pxPerDp).roundToInt().toFloat() }
            ?: return with(free) { performFling(initialVelocity) }
        val distance = target - start
        if (distance == 0f) return 0f
        if (reduceMotion) {
            scrollBy(distance)
            return 0f
        }
        var applied = 0f
        AnimationState(0f, initialVelocity).animateTo(distance, HomeSnapSpring) {
            // 손을 뗀 속도로 목표를 지나치면 되튀지 않고 목표에서 멈춘다.
            val next = if (distance > 0f) value.coerceAtMost(distance) else value.coerceAtLeast(distance)
            val delta = next - applied
            val consumed = scrollBy(delta)
            applied += consumed
            if (next == distance || abs(delta - consumed) > .5f) cancelAnimation()
        }
        return 0f
    }
}

/** 이전 스냅(500ms)과 비슷한 시간에 멈추고, 손가락 속도를 이어받도록 튀지 않는 스프링을 쓴다. 1px 안에 들면 끝낸다. */
private val HomeSnapSpring = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow, visibilityThreshold = 1f)

internal fun smooth(t: Float): Float = t * t * (3f - 2f * t)
internal fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
