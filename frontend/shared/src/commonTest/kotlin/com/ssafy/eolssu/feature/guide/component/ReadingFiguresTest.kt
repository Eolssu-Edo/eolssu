package com.ssafy.eolssu.feature.guide.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingFiguresTest {

    @Test
    fun eachCellSoundsForItsOwnLength() {
        assertEquals(listOf(0f to 1f, 1f to 1f), soundSpans(listOf(listOf("林"), listOf("南"))))
    }

    @Test
    fun twoNotesSplitACellInHalf() {
        assertEquals(listOf(0f to 0.5f, 0.5f to 0.5f, 1f to 1f), soundSpans(listOf(listOf("林", "南"), listOf("太"))))
    }

    @Test
    fun holdMarkExtendsThePreviousSound() {
        assertEquals(listOf(0f to 2f), soundSpans(listOf(listOf("林"), listOf("ㅡ"))))
    }

    @Test
    fun restMarkLeavesTheCellSilent() {
        assertEquals(listOf(0f to 1f), soundSpans(listOf(listOf("林"), listOf("△"))))
    }

    @Test
    fun orderAnimationFillsColumnOneFirst() {
        val frame = orderFrameAt(1.05f)
        assertEquals(0, frame.active)
        assertEquals(0.5f, frame.progress[0], 0.001f)
        assertTrue(frame.progress.drop(1).all { it == 0f })
        assertEquals(1f, frame.alpha)
    }

    @Test
    fun orderAnimationJumpsToColumnTwoAfterColumnOne() {
        val frame = orderFrameAt(2.1f + 0.4f)
        assertEquals(1, frame.active)
        assertEquals(1f, frame.progress[0])
        assertEquals(0.5f, frame.progress[1], 0.001f)
    }

    @Test
    fun orderAnimationHoldsTheFullPictureThenFadesAndRepeats() {
        val hold = orderFrameAt(OrderDurations.sum() + 0.5f)
        assertEquals(-1, hold.active)
        assertTrue(hold.progress.all { it == 1f })
        assertEquals(1f, hold.alpha)

        assertTrue(orderFrameAt(OrderCycle - 0.1f).alpha < 1f)
        val again = orderFrameAt(OrderCycle + 1.05f)
        assertEquals(0, again.active)
        assertEquals(0.5f, again.progress[0], 0.001f)
    }
}
