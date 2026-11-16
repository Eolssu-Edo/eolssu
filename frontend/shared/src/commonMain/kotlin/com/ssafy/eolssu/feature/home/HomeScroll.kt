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
    /** 제목이 첫 화면 자리에서 펼친 화면 자리로 옮겨 간 정도(0~1). 스크롤 앞쪽 60% 동안 다 옮겨 간다. */
    val headlineProgress: Float,
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
        headlineProgress = eh,
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

/**
 * 창 모양에 따라 고르는 홈 그림. 휴대폰·태블릿의 세로·가로마다 구도를 따로 잡은 그림을 쓴다(S15P31E102-983, 그림 교체 S15P31E102-1015).
 *
 * @property aspect 그림의 높이/너비 비.
 * @property openFocusX 펼친 화면의 그림 띠에 남길 가로 위치(0 왼쪽 끝 ~ 1 오른쪽 끝).
 * @property openFocusY 펼친 화면의 그림 띠에 남길 세로 위치(0 위 끝 ~ 1 아래 끝). 전각과 연주자가 띠에 들어오는 값이다.
 */
internal enum class HomeArtKind(val aspect: Float, val openFocusX: Float, val openFocusY: Float) {
    PhonePortrait(1254f / 580f, .5f, .62f),
    PhoneLandscape(852f / 1846f, .75f, .5f),
    TabletPortrait(1028f / 644f, .5f, .55f),
    TabletLandscape(992f / 1586f, .45f, .5f),
}

/** 짧은 변이 이 값(dp) 이상이면 태블릿 그림을 쓴다. 배치 판정의 Medium 시작 너비와 같다. */
private const val HomeTabletMinSide = 600f

/** 창 크기(dp)로 그림을 고른다. 방향은 너비·높이 비교로, 휴대폰·태블릿은 짧은 변으로 가른다. */
internal fun homeArtKind(width: Float, height: Float): HomeArtKind {
    val tablet = minOf(width, height) >= HomeTabletMinSide
    return when {
        width > height -> if (tablet) HomeArtKind.TabletLandscape else HomeArtKind.PhoneLandscape
        else -> if (tablet) HomeArtKind.TabletPortrait else HomeArtKind.PhonePortrait
    }
}

/**
 * 제목 묶음(얼쑤! 로고 위에 '나도 이제 국악인' 붓글씨 171×42를 4dp 간격으로 쌓은 것)의 배율 1 크기(dp). 간격은 배율과 관계없이 4dp로 보인다.
 * 로고는 붓글씨보다 눈에 띄도록 원래 96×47에서 120×59로 키웠다(S15P31E102-1015).
 */
internal const val HomeTitleLogoWidth = 120f
internal const val HomeTitleLogoHeight = 59f
internal const val HomeTitleWidth = 171f
internal const val HomeTitleStackHeight = HomeTitleLogoHeight + 42f
internal const val HomeTitleGap = 4f

/** 배율 [scale]에서 화면에 보이는 제목 묶음의 높이(dp). */
internal fun homeTitleHeight(scale: Float): Float = HomeTitleStackHeight * scale + HomeTitleGap

/** 첫 화면 제목의 배율과 오른쪽 아래 모서리 위치(dp). */
internal data class HomeHeroTitle(val scale: Float, val right: Float, val bottom: Float)

/**
 * 첫 화면 제목의 크기와 자리. 받은 그림은 고치지 않고, 제목이 오른쪽 먹 영역 안에 사방 16dp 이상 여유를 두고 들어오도록 그림마다 정했다(S15P31E102-1015).
 * 세로 화면은 평가 시작 버튼 바로 위 오른쪽에 둔다. 휴대폰 세로는 먹 영역이 좁아 기본 배율보다 작게 쓴다.
 * 가로 화면은 먹 영역이 화면 높이 전체에 걸쳐 넓으므로 창 너비에 비례해 크게 키우고, 오른쪽 먹 영역의 세로 가운데 쪽에 둔다.
 * 그림을 바꾸면 `frontend/tools/home-art/build_home_art.py`로 여유를 다시 점검한다.
 */
internal fun homeHeroTitle(kind: HomeArtKind, width: Float, height: Float, buttonHeight: Float): HomeHeroTitle = when (kind) {
    HomeArtKind.PhonePortrait, HomeArtKind.TabletPortrait -> HomeHeroTitle(
        scale = if (kind == HomeArtKind.PhonePortrait) .8f else 1.3f,
        right = width - 20f,
        bottom = (height - 88f - buttonHeight).coerceAtLeast(80f) - 10f,
    )
    HomeArtKind.PhoneLandscape, HomeArtKind.TabletLandscape -> {
        // 제목 너비 = 창 너비의 비율, 오른쪽 여백 = 창 너비의 5%, 제목 가운데 = 창 높이의 비율.
        val phone = kind == HomeArtKind.PhoneLandscape
        val scale = width * (if (phone) .29f else .23f) / HomeTitleWidth
        val centerY = height * (if (phone) .38f else .5f)
        HomeHeroTitle(scale, width * .95f, centerY + homeTitleHeight(scale) / 2f)
    }
}

/**
 * 펼친 화면에서 그림 띠 아래를 흰 바탕으로 흐려 보내는 구간(dp). 띠 아래 끝([ground]) 기준으로 위 [HomeArtFadeAbove]부터
 * 아래 [HomeArtFadeBelow]까지 그림을 이어 그리며 바탕색으로 덮는다. 위쪽 구간은 띠 안의 제목(띠 아래 끝에서 10dp 위)을 덜 가리도록 짧게 둔다.
 */
internal const val HomeArtFadeAbove = 24f
internal const val HomeArtFadeBelow = 56f

/** 흰 흐림 구간 안의 위치(0 = 시작, 1 = 끝)별 바탕색 덮임 비율. 끝으로 갈수록 빨리 덮여 띠 아래 끝이 선으로 보이지 않는다. */
internal fun homeArtFade(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/** 칸 안에서 그림을 그릴 크기와 칸 왼쪽 위 기준 위치(dp). 위치는 0 이하다. */
internal data class HomeArtPlacement(val width: Float, val height: Float, val x: Float, val y: Float)

/**
 * 그림이 칸을 빈틈없이 덮도록 비율을 지켜 키우고, 넘치는 쪽은 [focusX]·[focusY] 위치가 남게 자른다.
 * 스크롤로 칸이 줄어드는 동안에도 같은 식을 써서 그림이 끊기지 않고 이어진다.
 */
internal fun homeArtPlacement(kind: HomeArtKind, boxWidth: Float, boxHeight: Float, focusX: Float, focusY: Float): HomeArtPlacement {
    val width = maxOf(boxWidth, boxHeight / kind.aspect)
    val height = width * kind.aspect
    return HomeArtPlacement(width, height, (boxWidth - width) * focusX, (boxHeight - height) * focusY)
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
