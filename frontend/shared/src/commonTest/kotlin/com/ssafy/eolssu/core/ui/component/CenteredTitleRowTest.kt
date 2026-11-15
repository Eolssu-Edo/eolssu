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
}
