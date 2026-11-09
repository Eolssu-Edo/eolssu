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
        assertReadable("textSub / background", colors.textSub, colors.background)
        assertReadable("textSub / surface", colors.textSub, colors.surface)
    }

    @Test
    fun textOnFilledButtonsIsReadable() {
        assertReadable("onPrimary / primary", colors.onPrimary, colors.primary)
        assertReadable("onPlay / play", colors.onPlay, colors.play)
        assertReadable("onRec / rec", colors.onRec, colors.rec)
        assertReadable("onPrimary / green", colors.onPrimary, colors.green)
        assertReadable("onInk / selected tab and completed badge", colors.onInk, colors.text)
        assertReadable("selection outline / background", colors.accentText, colors.background, minimum = 3f)
        assertReadable("selection outline / surface", colors.accentText, colors.surface, minimum = 3f)
    }

    /** 부수 강조색은 24sp 이상 큰 글자에만 쓰므로 큰 글자 기준(3:1)을 본다. 검은 율명과도 구분돼야 한다. */
    @Test
    fun radicalTintIsReadableAsLargeText() {
        assertReadable("radical / background", colors.radical, colors.background, minimum = 3f)
        assertReadable("radical / text", colors.radical, colors.text, minimum = 3f)
    }

    @Test
    fun badgeTextIsReadableOnSoftBackgrounds() {
        assertReadable("playText / playSoft", colors.playText, colors.playSoft)
        assertReadable("recText / recSoft", colors.recText, colors.recSoft)
        assertReadable("text / accentSoft", colors.text, colors.accentSoft)
        assertReadable("accentText / surface", colors.accentText, colors.surface)
        assertReadable("text / doneSoft", colors.text, colors.doneSoft)
        assertReadable("pitchErrorText / pitchErrorSoft", colors.pitchErrorText, colors.pitchErrorSoft)
        assertReadable("rhythmErrorText / rhythmErrorSoft", colors.rhythmErrorText, colors.rhythmErrorSoft)
        assertReadable("text / accentTint", colors.text, colors.accentTint)
        assertReadable("playText / accentTint", colors.playText, colors.accentTint)
        assertReadable("accentText / accentTint (level 1)", colors.accentText, colors.accentTint)
        assertReadable("text / accentTintStrong (level 2)", colors.text, colors.accentTintStrong)
        assertReadable("background / text (level 3)", colors.background, colors.text)
        assertReadable("textMuted / surface (outline badge)", colors.textMuted, colors.surface)
    }

    /**
     * 틀린 정간 표시는 연하게 두되 흰 정간에서 테두리가 보여야 한다(1.5:1).
     * 아이콘 상자는 흰 아이콘이 그래픽 기준(3:1)을 넘어야 한다.
     */
    @Test
    fun softErrorMarksStayVisible() {
        listOf(
            "pitch" to colors.pitchErrorLine,
            "rhythm" to colors.rhythmErrorLine,
            "length" to colors.lengthErrorLine,
            "missed" to colors.missedErrorLine,
        ).forEach { (name, line) -> assertReadable("$name line / surface", line, colors.surface, minimum = 1.5f) }
        listOf(
            "pitch" to colors.pitchError,
            "rhythm" to colors.rhythmError,
            "length" to colors.lengthError,
            "missed" to colors.textMuted,
        ).forEach { (name, tag) -> assertReadable("white icon / $name tag", Color.White, tag, minimum = 3f) }
    }

    /**
     * 비활성 칩·버튼 글자는 WCAG 1.4.3 예외(비활성 UI)라 4.5:1을 요구하지 않는다.
     * 대신 바탕과 아예 구분되지 않을 만큼 흐려지지 않았는지(2:1)만 본다.
     */
    @Test
    fun disabledTextStaysVisible() {
        assertReadable("textDisabled / surfaceStrong", colors.textDisabled, colors.surfaceStrong, minimum = 2f)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color, minimum: Float = 4.5f) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$name 대비 ${(ratio * 100).toInt() / 100f} < $minimum")
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
