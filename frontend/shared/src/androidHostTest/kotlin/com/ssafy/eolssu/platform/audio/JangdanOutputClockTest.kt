package com.ssafy.eolssu.platform.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JangdanOutputClockTest {
    @Test
    fun onlyAdvancingHardwareTimestampsEstablishTheClock() {
        val clock = JangdanOutputClock()
        assertNull(clock.observe(441, 1_010_000_000, 882, 1_000_000_000))
        // A future committed-to-present timestamp is valid; write completion is not frame zero.
        assertEquals(1_000_000_000L, clock.observe(882, 1_020_000_000, 1323, 1_005_000_000))
        assertEquals(1_000_000_000L, clock.observe(882, 1_020_000_000, 1764, 1_015_000_000))
    }

    @Test
    fun staleOrRegressingTimestampRequiresFreshWarmup() {
        val clock = JangdanOutputClock()
        clock.observe(441, 1_010_000_000, 882, 1_000_000_000)
        clock.observe(882, 1_020_000_000, 1323, 1_005_000_000)
        assertNull(clock.observe(882, 1_020_000_000, 1764, 2_000_000_000))
        assertNull(clock.observe(441, 2_010_000_000, 882, 2_000_000_000))
        assertEquals(2_000_000_000L, clock.observe(882, 2_020_000_000, 1323, 2_005_000_000))
        assertNull(clock.observe(100, 2_030_000_000, 1323, 2_020_000_000))
    }

    @Test
    fun unsignedFrameRolloverStaysOnTheSameStreamTimeline() {
        val clock = JangdanOutputClock()
        val wrap = 0x1_0000_0000L
        val firstFrame = wrap - 100
        val secondFrame = wrap + 100
        val frameZero = 1_000_000_000L
        val firstTime = frameZero + firstFrame * 1_000_000_000L / 44_100
        val secondTime = frameZero + secondFrame * 1_000_000_000L / 44_100
        assertNull(clock.observe(firstFrame, firstTime, wrap, firstTime))
        assertEquals(frameZero, clock.observe(100, secondTime, wrap + 441, secondTime))
    }
}
