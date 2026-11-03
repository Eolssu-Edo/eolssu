package com.ssafy.eolssu.feature.home

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

internal fun smooth(t: Float): Float = t * t * (3f - 2f * t)
internal fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
