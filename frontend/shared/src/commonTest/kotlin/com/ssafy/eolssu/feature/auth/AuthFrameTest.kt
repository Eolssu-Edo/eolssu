package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthFrameTest {

    @Test
    fun phoneKeepsCompactFrameForEveryScreen() {
        assertEquals(AuthFrame.Compact, authFrameOf(WindowWidthClass.Compact, split = true))
        assertEquals(AuthFrame.Compact, authFrameOf(WindowWidthClass.Compact, split = false))
        assertFalse(AuthFrame.Compact.isTablet)
    }

    @Test
    fun tabletPortraitIsOneCenteredColumn() {
        assertEquals(AuthFrame.Portrait, authFrameOf(WindowWidthClass.Medium, split = true))
        assertEquals(AuthFrame.Portrait, authFrameOf(WindowWidthClass.Medium, split = false))
    }

    @Test
    fun landscapeSplitsOnlyScreensWithBrandPane() {
        assertEquals(AuthFrame.Split, authFrameOf(WindowWidthClass.Expanded, split = true))
        assertEquals(AuthFrame.Landscape, authFrameOf(WindowWidthClass.Expanded, split = false))
        assertTrue(AuthFrame.Split.isTablet)
        assertTrue(AuthFrame.Landscape.isTablet)
    }
}
