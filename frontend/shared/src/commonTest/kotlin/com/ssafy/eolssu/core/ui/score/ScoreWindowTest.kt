package com.ssafy.eolssu.core.ui.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreWindowTest {
    @Test fun aLongScoreKeepsOnlyVisibleStacksAndOneNeighbourOnEitherSide() {
        assertEquals(0..5, visibleScoreStacks(138, 0, 400, 80f))
        assertEquals(9..15, visibleScoreStacks(138, 800, 400, 80f))
        assertEquals(132..137, visibleScoreStacks(138, 10640, 400, 80f))
    }

    @Test fun resizingAndShortScoresKeepTheirOriginalReadingIndices() {
        assertEquals(0..2, visibleScoreStacks(3, 0, 400, 80f))
        assertEquals(9..13, visibleScoreStacks(138, 800, 240, 80f))
        assertTrue(visibleScoreStacks(0, 0, 400, 80f).isEmpty())
    }

    @Test fun omittedStacksKeepTheExactScrollExtentAfterPixelRounding() {
        val count = 138
        val columnPx = 281
        val gapPx = 7
        for (offset in listOf(0, 4000, 38000)) {
            val window = visibleScoreStacks(count, offset, 1440, (columnPx + gapPx).toFloat())
            val blocks = listOf(window.first, window.count(), count - 1 - window.last).filter { it > 0 }
            val measured = blocks.sumOf { scoreStackSpanPx(it, columnPx, gapPx) } + (blocks.size - 1) * gapPx
            assertEquals(39737, measured, "Windowing must not change the score width at scroll $offset")
        }
    }
}
