package com.ssafy.eolssu.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/** 글자로 쓰는 색 조합이 WCAG AA 본문 기준(4.5:1)을 넘는지 확인한다. */
class EolssuColorsContrastTest {
    private val colors = LightEolssuColors

    @Test
    fun bodyTextIsReadableOnBackgroundAndSurface() {
        assertReadable("text / background", colors.text, colors.background)
        assertReadable("text / surface", colors.text, colors.surface)
        assertReadable("textMuted / background", colors.textMuted, colors.background)
        assertReadable("textMuted / surface", colors.textMuted, colors.surface)
    }

    @Test
    fun textOnFilledButtonsIsReadable() {
        assertReadable("onPrimary / primary", colors.onPrimary, colors.primary)
        assertReadable("onPrimary / play", colors.onPrimary, colors.play)
        assertReadable("onPrimary / rec", colors.onPrimary, colors.rec)
    }

    @Test
    fun badgeTextIsReadableOnSoftBackgrounds() {
        assertReadable("playText / playSoft", colors.playText, colors.playSoft)
        assertReadable("recText / recSoft", colors.recText, colors.recSoft)
        assertReadable("text / accentSoft", colors.text, colors.accentSoft)
        assertReadable("accentText / surface", colors.accentText, colors.surface)
        assertReadable("text / doneSoft", colors.text, colors.doneSoft)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5f, "$name 대비 ${(ratio * 100).toInt() / 100f} < 4.5")
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
