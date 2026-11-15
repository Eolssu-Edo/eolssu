package com.ssafy.eolssu.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class CenteredTitleRowTest {

    @Test
    fun keepsTitleCenteredWhenItFitsBetweenBothSides() {
        val slot = centeredTitleSlot(width = 336, startWidth = 48, endWidth = 60, gap = 8)
        assertEquals(56..268, slot)
        assertEquals(118, centeredTitleX(336, 100, slot))
    }

    @Test
    fun movesTitleIntoSpaceLeftBetweenSidesWhenCenterDoesNotFit() {
        // 360dp 휴대폰에서 오른쪽 악기 고르기가 넓을 때: 가운데 자리(60)보다 남는 폭(142)이 넓다.
        val slot = centeredTitleSlot(width = 336, startWidth = 48, endWidth = 130, gap = 8)
        assertEquals(56..198, slot)
        assertEquals(83, centeredTitleX(336, 115, slot), "오른쪽 칸과 겹치지 않게 왼쪽으로 민다")
        assertEquals(138, centeredTitleX(336, 115, centeredTitleSlot(336, 130, 48, 8)), "왼쪽이 넓으면 오른쪽으로 민다")
    }

    @Test
    fun emptySideUsesRowEdgeWithoutGap() {
        assertEquals(0..336, centeredTitleSlot(336, 0, 0, 8))
    }

    @Test
    fun endWidthLeftForTitleKeepsWholeTitle() {
        // 320dp 휴대폰: 줄 296, 뒤로 가기 48, 제목 108이면 오른쪽 칸은 사이 간격을 빼고 124까지 쓸 수 있다.
        assertEquals(124, endWidthLeftForTitle(width = 296, startWidth = 48, titleWidth = 108, gap = 8))
        val slot = centeredTitleSlot(296, 48, 124, 8)
        assertEquals(108, slot.last - slot.first, "줄인 오른쪽 칸 옆에 제목이 꼭 맞게 들어간다")
        assertEquals(0, endWidthLeftForTitle(width = 100, startWidth = 48, titleWidth = 108, gap = 8), "음수가 되지 않는다")
        assertEquals(180, endWidthLeftForTitle(width = 296, startWidth = 0, titleWidth = 108, gap = 8), "왼쪽 칸이 비면 그쪽 간격은 없다")
    }
}
