package com.ssafy.eolssu.core.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowLayoutTest {

    @Test
    fun widthBelow600IsCompact() {
        assertEquals(WindowWidthClass.Compact, windowLayoutOf(width = 360.dp, height = 800.dp).widthClass)
        assertEquals(WindowWidthClass.Compact, windowLayoutOf(width = 599.9.dp, height = 800.dp).widthClass)
    }

    @Test
    fun widthFrom600Below840IsMedium() {
        assertEquals(WindowWidthClass.Medium, windowLayoutOf(width = 600.dp, height = 1280.dp).widthClass)
        assertEquals(WindowWidthClass.Medium, windowLayoutOf(width = 800.dp, height = 1280.dp).widthClass)
        assertEquals(WindowWidthClass.Medium, windowLayoutOf(width = 839.9.dp, height = 1280.dp).widthClass)
    }

    @Test
    fun widthFrom840IsExpanded() {
        assertEquals(WindowWidthClass.Expanded, windowLayoutOf(width = 840.dp, height = 800.dp).widthClass)
        assertEquals(WindowWidthClass.Expanded, windowLayoutOf(width = 1280.dp, height = 800.dp).widthClass)
        assertEquals(WindowWidthClass.Expanded, windowLayoutOf(width = 1920.dp, height = 1000.dp).widthClass)
    }

    @Test
    fun heightBelowLandscapeMinimumIsShort() {
        assertTrue(windowLayoutOf(width = 1440.dp, height = 500.dp).isShortHeight)
        assertTrue(windowLayoutOf(width = 1280.dp, height = 649.9.dp).isShortHeight)
    }

    @Test
    fun heightFromLandscapeMinimumIsNotShort() {
        assertFalse(windowLayoutOf(width = 1280.dp, height = 650.dp).isShortHeight)
        assertFalse(windowLayoutOf(width = 1280.dp, height = 800.dp).isShortHeight)
        assertFalse(windowLayoutOf(width = 800.dp, height = 1280.dp).isShortHeight)
    }

    @Test
    fun phoneLandscapeWebWindowsAreCompactHeight() {
        // 데스크톱에서 줄인 창, iPhone 가로, 큰 휴대폰 가로, 주소창을 뺀 Android Chrome 가로
        for ((width, height) in listOf(800 to 380, 844 to 390, 932 to 430, 780 to 300)) {
            assertTrue(windowLayoutOf(width = width.dp, height = height.dp).isCompactHeight, "${width}x$height")
        }
        assertTrue(windowLayoutOf(width = 1280.dp, height = 479.9.dp).isCompactHeight)
    }

    @Test
    fun laptopAndPortraitWindowsAreNotCompactHeight() {
        // 낮은 노트북 브라우저 창은 isShortHeight일 뿐 Compact 높이가 아니다.
        for ((width, height) in listOf(1280 to 480, 1280 to 600, 1366 to 625, 600 to 900, 390 to 844)) {
            assertFalse(windowLayoutOf(width = width.dp, height = height.dp).isCompactHeight, "${width}x$height")
        }
        assertTrue(windowLayoutOf(width = 1280.dp, height = 600.dp).isShortHeight)
    }

    @Test
    fun heightDoesNotChangeWidthClass() {
        val wideShort = windowLayoutOf(width = 1440.dp, height = 400.dp)
        assertEquals(WindowWidthClass.Expanded, wideShort.widthClass)
        assertTrue(wideShort.isShortHeight)
    }
}
