package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JangdanPcmMixerTest {
    private val deong = JangdanClip(JangdanSample.Deong)
    private val kung = JangdanClip(JangdanSample.Kung)

    @Test
    fun overlappingVoicesKeepTheirGainAndExactFirstOutputFrame() {
        val mixer = JangdanPcmMixer()
        mixer.play(deong, shortArrayOf(1000, 2000, 3000), 0.65f)
        val first = mixer.render(1, 100)
        assertContentEquals(shortArrayOf(650), first.pcm)
        assertEquals(listOf(RenderedJangdanEvent(deong, 0.65f, 100)), first.events)
        mixer.play(kung, shortArrayOf(1000, -1000), 0.5f)
        val overlap = mixer.render(3, 101)
        assertContentEquals(shortArrayOf(1800, 1450, 0), overlap.pcm)
        assertEquals(listOf(RenderedJangdanEvent(kung, 0.5f, 101)), overlap.events)
        assertFalse(mixer.active)
        assertFalse(overlap.clipped)
    }

    @Test
    fun ninthVoiceReplacesOldestAndReportsReferenceDiscontinuity() {
        val mixer = JangdanPcmMixer()
        repeat(8) { assertFalse(mixer.play(deong, shortArrayOf(100, 100), 1f)) }
        assertTrue(mixer.play(kung, shortArrayOf(900, 900), 1f))
        val block = mixer.render(2, 0)
        assertContentEquals(shortArrayOf(1600, 1600), block.pcm)
        assertEquals(8, block.events.size)
        assertEquals(kung, block.events.last().clip)
    }

    @Test
    fun clearDropsVoicesWithoutInventingEventsInTheFollowingSilence() {
        val mixer = JangdanPcmMixer()
        mixer.play(deong, shortArrayOf(100, 200, 300), 1f)
        mixer.render(1, 0)
        mixer.clear()
        assertFalse(mixer.active)
        val silence = mixer.render(3, 1)
        assertContentEquals(shortArrayOf(0, 0, 0), silence.pcm)
        assertTrue(silence.events.isEmpty())
        assertEquals(0, silence.voiceFrames)
    }

    @Test
    fun shortWritesPublishAStartOnlyWhenItsFirstFrameIsAccepted() {
        val mixer = JangdanPcmMixer()
        mixer.play(deong, shortArrayOf(100, 200, 300), 1f)
        val block = mixer.render(4, 40)
        assertTrue(block.accept(0).isEmpty())
        assertEquals(4, block.remaining)
        assertEquals(listOf(RenderedJangdanEvent(deong, 1f, 40)), block.accept(1))
        assertEquals(3, block.remaining)
        assertTrue(block.accept(2).isEmpty())
        assertEquals(1, block.remaining)
        assertTrue(block.accept(1).isEmpty())
        assertEquals(0, block.remaining)
    }

    @Test
    fun clippingIsSaturatedAndReportedInsteadOfWrappingTheSign() {
        val mixer = JangdanPcmMixer()
        repeat(2) { mixer.play(deong, shortArrayOf(30_000, -30_000), 0.65f) }
        val clipped = mixer.render(2, 0)
        assertContentEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), clipped.pcm)
        assertTrue(clipped.clipped)
        assertFalse(mixer.render(2, 2).clipped)
    }

    @Test
    fun fullFourSecondRollIsNotTruncatedToTheOriginalSoundPoolLimit() {
        val mixer = JangdanPcmMixer()
        val roll = JangdanClip(JangdanSample.Gutgeori59, 176_400)
        mixer.play(roll, ShortArray(176_400) { 1000 }, 0.65f)
        val begin = mixer.render(176_399, 0)
        assertEquals(176_399, begin.voiceFrames)
        assertTrue(mixer.active)
        assertContentEquals(shortArrayOf(650, 0), mixer.render(2, 176_399).pcm)
        assertFalse(mixer.active)
    }
}
