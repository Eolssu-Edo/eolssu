package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ssafy.eolssu.core.model.SongLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 난이도 배지는 입문 · 초급 · 중급 순서로 매화 · 대나무 · 소나무 모양을 쓴다. */
class LevelBadgeStyleTest {
    @Test
    fun eachLevelUsesItsBoardStep() {
        assertEquals(EolssuBadgeStyle.Level1, levelBadgeStyle(SongLevel.Intro))
        assertEquals(EolssuBadgeStyle.Level2, levelBadgeStyle(SongLevel.Elementary))
        assertEquals(EolssuBadgeStyle.Level3, levelBadgeStyle(SongLevel.Intermediate))
    }

    @Test
    fun eachLevelHasItsOwnMotif() {
        assertEquals(LevelMotif.Plum, levelBadgePalette(EolssuBadgeStyle.Level1)?.motif)
        assertEquals(LevelMotif.Bamboo, levelBadgePalette(EolssuBadgeStyle.Level2)?.motif)
        assertEquals(LevelMotif.Pine, levelBadgePalette(EolssuBadgeStyle.Level3)?.motif)
        assertNull(levelBadgePalette(EolssuBadgeStyle.Soft))
    }

    /** 글자는 옅은 바탕 위에서 본문 기준(4.5:1)을, 테두리는 바탕과 구분되는 그래픽 기준(3:1)을 넘어야 한다. */
    @Test
    fun labelAndLineAreReadableOnBackground() {
        listOf(EolssuBadgeStyle.Level1, EolssuBadgeStyle.Level2, EolssuBadgeStyle.Level3).forEach { style ->
            val palette = requireNotNull(levelBadgePalette(style))
            val text = contrast(palette.text, palette.background)
            val line = contrast(palette.line, palette.background)
            assertTrue(text >= 4.5f, "$style 글자 / 바탕 대비 $text < 4.5")
            assertTrue(line >= 3f, "$style 테두리 / 바탕 대비 $line < 3")
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
