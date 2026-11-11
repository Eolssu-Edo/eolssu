package com.ssafy.eolssu.feature.guide.component

import androidx.compose.ui.geometry.Offset
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
    fun restFillCoversTheInsideOfTheRestGlyph() {
        // 그림의 쉼은 글자 크기 24, 가운데 x 150, 기준선 y 200. 글리프 안쪽 빈 삼각형은 (99, 52) · (500, 746) · (901, 52)다
        val (top, left, right) = restTriangle(150f, 200f, 24f)
        val k = 24f / 1000f
        val hole = listOf(Offset(150f + (99 - 500) * k, 200f - 52 * k), Offset(150f, 200f - 746 * k), Offset(150f + (901 - 500) * k, 200f - 52 * k))
        fun inside(p: Offset): Boolean {
            fun side(a: Offset, b: Offset) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = listOf(side(top, left), side(left, right), side(right, top))
            return s.all { it >= 0f } || s.all { it <= 0f }
        }
        assertTrue(hole.all(::inside))
        // 바깥 윤곽과 같은 크기라 글자 밖으로 번지지 않는다
        assertEquals(150f - 470 * k, left.x, 0.001f)
        assertEquals(200f - 826 * k, top.y, 0.001f)
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
