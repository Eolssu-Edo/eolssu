package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 정간 안 율명 배치(S15P31E102-552): 넘칠 때만 줄이고, 오류 아이콘·구간 알약 자리와 겹치지 않는다. */
class GlyphFitTest {

    private val cell = Size(50f, 44f)

    private fun GlyphFit.right(content: Size) = topLeft.x + content.width * scale
    private fun GlyphFit.bottom(content: Size) = topLeft.y + content.height * scale

    @Test
    fun fittingGlyphKeepsSizeAndCenter() {
        val glyph = Size(20f, 20f)
        assertEquals(GlyphFit(1f, Offset(15f, 12f)), fitGlyphInCell(cell, glyph))
    }

    @Test
    fun wideSplitGlyphShrinksToCellWidthWithoutClipping() {
        // 가로로 나뉜 세 음 중 하나: 자리 12 × 44에 율명 13 × 13.
        val box = Size(12f, 44f)
        val glyph = Size(13f, 13f)
        val fit = fitGlyphInCell(box, glyph)
        assertEquals(12f / 13f, fit.scale)
        assertTrue(fit.topLeft.x >= 0f && fit.right(glyph) <= box.width + 0.01f)
    }

    @Test
    fun glyphUnderTagMovesOnlyAsFarAsNeeded() {
        // 가운데 율명(13..37, 10..34)이 오른쪽 위 14 × 16 아이콘 자리(36..50)와 겹친다. 왼쪽으로 1만 비키면 크기를 지킨다.
        val glyph = Size(24f, 24f)
        val fit = fitGlyphInCell(cell, glyph, topEnd = Size(14f, 16f))
        assertEquals(1f, fit.scale)
        assertEquals(Offset(12f, 10f), fit.topLeft)
        assertTrue(fit.right(glyph) <= cell.width - 14f)
    }

    @Test
    fun narrowUnitUnderTagMovesBelowTheTag() {
        // 두 음으로 나뉜 오른쪽 소박: 왼쪽으로 비킬 폭이 없으니 아이콘 아래로 내린다.
        val box = Size(20f, 44f)
        val glyph = Size(14f, 14f)
        val fit = fitGlyphInCell(box, glyph, topEnd = Size(14f, 16f))
        assertEquals(1f, fit.scale)
        assertTrue(fit.topLeft.y >= 16f && fit.bottom(glyph) <= box.height)
    }

    @Test
    fun glyphAvoidsBothTagAndEdgePill() {
        val glyph = Size(30f, 30f)
        val fit = fitGlyphInCell(cell, glyph, topEnd = Size(14f, 16f), bottomStart = Size(30f, 18f))
        val right = fit.right(glyph)
        val bottom = fit.bottom(glyph)
        val hitsTag = right > cell.width - 14f && fit.topLeft.y < 16f
        val hitsPill = fit.topLeft.x < 30f && bottom > cell.height - 18f
        assertTrue(!hitsTag && !hitsPill, "겹침: $fit")
        assertTrue(fit.topLeft.x >= 0f && fit.topLeft.y >= 0f && right <= cell.width + 0.01f && bottom <= cell.height + 0.01f)
    }
}
