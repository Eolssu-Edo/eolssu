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
    fun heightDoesNotChangeWidthClass() {
        val wideShort = windowLayoutOf(width = 1440.dp, height = 400.dp)
        assertEquals(WindowWidthClass.Expanded, wideShort.widthClass)
        assertTrue(wideShort.isShortHeight)
    }

    @Test
    fun narrowTextWindowCountsFontScale() {
        assertFalse(isNarrowTextWidth(width = 360.dp, fontScale = 1f), "360dp 기본 글꼴은 좁지 않다")
        assertTrue(isNarrowTextWidth(width = 359.9.dp, fontScale = 1f))
        assertTrue(isNarrowTextWidth(width = 320.dp, fontScale = 1f))
        assertTrue(isNarrowTextWidth(width = 360.dp, fontScale = 1.15f), "글꼴을 키우면 같은 폭도 좁게 본다")
        assertFalse(isNarrowTextWidth(width = 411.dp, fontScale = 1.1f))
        assertTrue(isNarrowTextWidth(width = 411.dp, fontScale = 1.3f))
    }
}
