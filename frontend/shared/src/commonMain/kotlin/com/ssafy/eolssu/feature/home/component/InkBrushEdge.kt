package com.ssafy.eolssu.feature.home.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path

/** 사용자 제공 ink-button-rough.html의 붓털 진행 경계. 그림과 반전 글자에 함께 쓴다. */
internal fun inkRevealPath(size: Size, progress: Float): Path = Path().apply {
    moveTo(-size.width * .2f, 0f)
    InkBristles.forEach { (x, y) -> lineTo(size.width * (x + 1.14f * progress), size.height * y) }
    lineTo(-size.width * .2f, size.height)
    close()
}

private val InkBristles = listOf(
    -0.10179f to 0.00000f,
    -0.09931f to 0.01735f,
    -0.08284f to 0.03145f,
    -0.11125f to 0.04444f,
    -0.08046f to 0.06223f,
    -0.11788f to 0.07806f,
    -0.08271f to 0.08946f,
    -0.08451f to 0.09732f,
    -0.10116f to 0.10848f,
    -0.06651f to 0.13378f,
    -0.08022f to 0.15920f,
    -0.08874f to 0.17976f,
    -0.06834f to 0.19222f,
    -0.06318f to 0.21020f,
    -0.07082f to 0.23333f,
    -0.06383f to 0.25622f,
    -0.09693f to 0.26422f,
    -0.06689f to 0.29101f,
    -0.08775f to 0.31425f,
    -0.08909f to 0.33343f,
    -0.06738f to 0.35764f,
    -0.08156f to 0.36580f,
    -0.09962f to 0.37574f,
    -0.09417f to 0.39615f,
    -0.03174f to 0.41001f,
    -0.04509f to 0.42962f,
    -0.08843f to 0.45058f,
    -0.05532f to 0.47511f,
    -0.08867f to 0.48606f,
    -0.09640f to 0.50165f,
    -0.07591f to 0.52768f,
    -0.07342f to 0.54375f,
    -0.06735f to 0.55103f,
    -0.04660f to 0.55956f,
    -0.06536f to 0.57663f,
    -0.07200f to 0.58789f,
    -0.08424f to 0.60305f,
    -0.06526f to 0.61906f,
    -0.06538f to 0.62655f,
    -0.07810f to 0.63595f,
    -0.07803f to 0.66206f,
    -0.07509f to 0.68162f,
    -0.05255f to 0.70064f,
    -0.10466f to 0.71625f,
    -0.08333f to 0.74042f,
    -0.05542f to 0.75906f,
    -0.09303f to 0.78374f,
    -0.09953f to 0.79187f,
    -0.09952f to 0.80641f,
    -0.07745f to 0.83300f,
    -0.09081f to 0.84881f,
    -0.07491f to 0.87375f,
    -0.09389f to 0.88998f,
    -0.10227f to 0.91465f,
    -0.10565f to 0.93537f,
    -0.10253f to 0.94622f,
    -0.09707f to 0.95429f,
    -0.10629f to 0.96408f,
    -0.11987f to 0.97825f,
    -0.08796f to 1.00000f,
)
