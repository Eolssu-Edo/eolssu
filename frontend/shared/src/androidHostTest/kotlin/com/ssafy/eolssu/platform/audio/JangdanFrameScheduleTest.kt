package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JangdanFrameScheduleTest {
    private fun plan(bpm: Int = 77, startTick: Long = 0) = planJangdan(
        JangdanPlayback("12", "4박", startTick, 4, listOf("덩", "덕", "궁", "덕"), listOf(4)), bpm, 1)!!

    @Test fun three77BpmCyclesFlowIntoTheScoreWithoutRoundedBeatDrift() {
        val schedule = JangdanFrameSchedule(plan(), 10000, 3, 4, 9)
        val descriptor = schedule.describe(2_000_000_000)
        assertEquals(listOf(10000L, 147455L, 284909L), descriptor.cycleStartFrames)
        assertEquals(422364L, descriptor.recordingStartFrame)
        assertEquals(12, descriptor.preparationEvents.size)
        repeat(10000) { index ->
            val expected = 10000L + (index * 60.0 * 44100 / 77).roundToLong()
            assertEquals(expected, schedule.nextFrame)
            val hit = schedule.takeAt(expected).single()
            assertEquals(expected, hit.frame)
            assertEquals(.65f, hit.gain)
        }
    }

    @Test fun preparationIgnoresScorePickupButTheScoreKeepsItsStartTick() {
        val schedule = JangdanFrameSchedule(plan(startTick = 2), 0, 3, 1, 1)
        val descriptor = schedule.describe(1)
        assertEquals(0L, descriptor.preparationEvents.first().frame)
        descriptor.preparationEvents.forEach { assertEquals(it, schedule.takeAt(it.frame).single()) }
        assertEquals((14 * 60.0 * 44100 / 77).roundToLong(), schedule.nextFrame)
        assertEquals((12 * 60.0 * 44100 / 77).roundToLong(), descriptor.recordingStartFrame)
    }

    @Test fun changingTempoUsesANewAbsoluteFrameGrid() {
        val slow = JangdanFrameSchedule(plan(), 100, 3, 1, 1).describe(1)
        val fast = JangdanFrameSchedule(plan(bpm = 154), 100, 3, 1, 1).describe(1)
        assertEquals(206182L, fast.recordingStartFrame - 100)
        assertEquals(412364L, slow.recordingStartFrame - 100)
    }

    @Test fun blocksSplitAtTheAttackAndShortWritesPublishOnlyAcceptedStarts() {
        val schedule = JangdanFrameSchedule(plan(), 5, 3, 1, 1)
        val mixer = JangdanPcmMixer()
        assertTrue(schedule.takeAt(0).isEmpty())
        assertEquals(5, schedule.blockFrames(0, 441))
        val before = mixer.render(schedule.blockFrames(0, 441), 0)
        assertTrue(before.accept(5).isEmpty())
        val hit = schedule.takeAt(5).single()
        mixer.play(hit.clip, shortArrayOf(1000, 2000, 3000), hit.gain)
        val attack = mixer.render(schedule.blockFrames(5, 441), 5)
        assertTrue(attack.accept(0).isEmpty())
        assertEquals(listOf(hit), attack.accept(1))
        assertTrue(attack.accept(1).isEmpty())
        assertEquals(650, attack.pcm[0].toInt())
    }

    @Test fun matchingPreparationIsAdoptedOnceAndCancellationOrGenerationChangesInvalidateIt() {
        val current = plan()
        val schedule = JangdanFrameSchedule(current, 0, 3, 7, 9)
        assertFalse(schedule.adopt(current, 10))
        assertFalse(schedule.adopt(plan(bpm = 78), 9))
        assertTrue(schedule.adopt(current, 9))
        assertFalse(schedule.adopt(current, 9))
        schedule.cancel()
        assertEquals(null, schedule.nextFrame)
        assertTrue(schedule.takeAt(100000).isEmpty())
        assertEquals(441, schedule.blockFrames(100000, 441))
        val cancelled = JangdanFrameSchedule(current, 0, 3, 7, 9)
        cancelled.cancel()
        assertFalse(cancelled.adopt(current, 9))
    }

    @Test fun aMissedFrameFailsInsteadOfReplayingLateStrikesTogether() {
        val schedule = JangdanFrameSchedule(plan(), 100, 3, 1, 1)
        assertFailsWith<IllegalStateException> { schedule.takeAt(101) }
    }

    @Test fun simultaneousGraceAndMainAttacksKeepOneFrameWithoutZeroLengthBlocks() {
        val plan = planJangdan(JangdanPlayback("3", "기덕", 0, 4, listOf("기덕", "쿵"), listOf(2)), 77, 1)!!
        val schedule = JangdanFrameSchedule(plan, 0, 3, 1, 1)
        val hits = schedule.takeAt(0)
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.frame == 0L })
        assertTrue(schedule.blockFrames(0, 441) > 0)
        val mixer = JangdanPcmMixer()
        hits.forEach { mixer.play(it.clip, shortArrayOf(1000, 0), it.gain) }
        assertEquals(hits, mixer.render(2, 0).accept(1))
    }
}
