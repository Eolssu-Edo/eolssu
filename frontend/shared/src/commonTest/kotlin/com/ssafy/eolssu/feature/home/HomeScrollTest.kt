package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScrollTest {
    @Test fun startsWithFullArtAndWhiteInkAboveTheFold() {
        val frame = homeScrollFrame(390f, 844f, 0f, 56.5f)
        assertEquals(844f, frame.groundTop)
        assertEquals(699.5f, frame.buttonTop)
        assertEquals(1.65f, frame.artScale)
        assertTrue(frame.darkBehindButton)
        assertEquals(1f, frame.arrowAlpha)
    }

    @Test fun opensToOriginalBandAndDarkInk() {
        val frame = homeScrollFrame(390f, 844f, 400f, 56.5f)
        assertEquals(376f, frame.groundTop)
        assertEquals(388f, frame.buttonTop)
        assertEquals(1f, frame.artScale)
        assertFalse(frame.darkBehindButton)
        assertEquals(0f, frame.arrowAlpha)
    }

    @Test fun inkChangesWhenTheButtonCrossesTheRisingGround() {
        assertTrue(homeScrollFrame(390f, 844f, 160f, 56.5f).darkBehindButton)
        assertFalse(homeScrollFrame(390f, 844f, 320f, 56.5f).darkBehindButton)
    }

    @Test fun snapHasDifferentOpenAndCloseThresholds() {
        assertEquals(0f, homeSnapTarget(47f, wasOpen = false))
        assertEquals(400f, homeSnapTarget(49f, wasOpen = false))
        assertEquals(400f, homeSnapTarget(341f, wasOpen = true))
        assertEquals(0f, homeSnapTarget(339f, wasOpen = true))
        assertEquals(520f, homeSnapTarget(520f, wasOpen = true))
    }

    @Test fun clampsOverscrollAndKeepsShortScreensUsable() {
        assertEquals(0f, homeScrollFrame(390f, 600f, -20f, 64f).progress)
        assertEquals(1f, homeScrollFrame(390f, 600f, 900f, 64f).progress)
        val frame = homeScrollFrame(390f, 320f, 0f, 80f)
        assertTrue(frame.buttonTop >= 80f)
        assertTrue(frame.bandHeight < 320f)
    }
}
