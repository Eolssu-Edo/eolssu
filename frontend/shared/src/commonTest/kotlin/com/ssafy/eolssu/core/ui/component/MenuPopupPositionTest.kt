package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** 앱 드롭다운 창의 위치: 아래에 열고, 모자라면 위로 뒤집고, 화면 밖으로 나가지 않는다(S15P31E102-970). */
class MenuPopupPositionTest {
    private val window = IntSize(400, 800)
    private val menu = IntSize(200, 300)

    private fun place(anchor: IntRect, align: EolssuMenuAlign = EolssuMenuAlign.Start, size: IntSize = menu) =
        BelowAnchor(gap = 8, align = align).calculatePosition(anchor, window, LayoutDirection.Ltr, size)

    @Test
    fun opensBelowWhenRoomBelow() {
        assertEquals(IntOffset(20, 148), place(IntRect(20, 100, 120, 140)))
    }

    @Test
    fun flipsAboveWhenBelowOverflows() {
        // 아래 공간 800-(600+8)=192 < 300, 위 공간 500-8=492 ≥ 300.
        assertEquals(IntOffset(20, 500 - 8 - 300), place(IntRect(20, 500, 120, 540)))
    }

    @Test
    fun picksLargerSideAndClampsWhenNeitherFits() {
        val tall = IntSize(200, 700)
        // 위가 더 넓으므로 위로 열되 화면 위 끝(0)에서 멈춘다.
        assertEquals(IntOffset(20, 0), place(IntRect(20, 500, 120, 540), size = tall))
        // 아래가 더 넓으므로 아래로 열되 화면 아래 끝에 맞춘다.
        assertEquals(IntOffset(20, 100), place(IntRect(20, 200, 120, 240), size = tall))
    }

    @Test
    fun endAlignMatchesAnchorRightAndStaysOnScreen() {
        assertEquals(IntOffset(380 - 200, 148), place(IntRect(300, 100, 380, 140), EolssuMenuAlign.End))
        // 기준이 왼쪽 끝 가까이면 0에서 멈춘다.
        assertEquals(IntOffset(0, 148), place(IntRect(0, 100, 60, 140), EolssuMenuAlign.End))
        // 시작 맞춤이 오른쪽 끝을 넘으면 화면 안으로 당긴다.
        assertEquals(IntOffset(200, 148), place(IntRect(350, 100, 390, 140)))
    }
}
