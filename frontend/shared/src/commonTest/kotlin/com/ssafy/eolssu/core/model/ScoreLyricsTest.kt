package com.ssafy.eolssu.core.model

import kotlin.test.*

class ScoreLyricsTest {
    @Test fun dorajiLongNotesKeepRaAtItsOriginalThirdSlot() {
        assertEquals(listOf("", "", "라"), scoreLyricSlots("- - 라", 3))
        assertEquals(listOf("지", "", "화"), scoreLyricSlots("지 - 화", 3))
    }

    @Test fun onlyHangulIsDisplayedAndVerticalMarkupIsRemovedAsAWhole() {
        assertEquals(listOf("도", "라", "지"), scoreLyricSlots("도 | 라 | 지", 3))
        assertEquals(listOf("심", "", ""), scoreLyricSlots("심 {세로표}", 3))
        assertEquals(listOf("", "", "한글"), scoreLyricSlots("ㅡ — !한글123abc", 3))
        assertEquals(listOf("", ""), scoreLyricSlots("{세로표} | -", 2))
    }

    @Test fun emptyAndUnsplitLyricsDoNotCreateExtraSlots() {
        assertEquals(listOf("", ""), scoreLyricSlots(null, 2))
        assertEquals(listOf("아리랑", ""), scoreLyricSlots("아리랑", 2))
        assertTrue(scoreLyricSlots("도", 0).isEmpty())
        assertEquals(listOf("도라지"), scoreLyricSlots("도 라 지", 1))
    }

    @Test fun standaloneVerticalMarkersKeepFollowingSyllablesAtTheirSourceSlots() {
        assertEquals(listOf("", "노"), scoreLyricSlots("{세로표} 노", 2))
        assertEquals(listOf("", "", "리"), scoreLyricSlots("{세로표} - 리", 3))
        assertEquals(listOf("넘", "", "자"), scoreLyricSlots("넘 {세로표} 자", 3))
    }
}
