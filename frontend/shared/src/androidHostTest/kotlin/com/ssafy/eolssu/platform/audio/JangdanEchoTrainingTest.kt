package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.EchoCalibrationResult
import com.ssafy.eolssu.core.audio.EchoPlaybackEvent
import com.ssafy.eolssu.core.audio.RecordingEchoFilter
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class JangdanEchoTrainingTest {
    private val lengths = mapOf(
        JangdanClip(JangdanSample.Deong) to 17_640,
        JangdanClip(JangdanSample.Deok) to 11_466,
        JangdanClip(JangdanSample.Kung) to 17_640,
    )

    // Catalog song29, mr203-fixed-danso-025-p1: 48 quarter-note BPM, six ticks per beat.
    private fun arirang() = assertNotNull(planJangdan(JangdanPlayback(
        "1", "세마치", 0, 9, listOf("떵", "", "", "떵", "", "덕", "쿵", "덕", ""), listOf(3, 3, 3),
    ), 48, 6))

    @Test fun actualDansoArirangSelectsThreeCycleTrainingWithoutChangingTempoOrRecordingBoundary() {
        val plan = arirang()
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        assertNull(jangdanEchoPreparation(scheduled.preparationEvents, lengths, 0, 40_000,
            scheduled.recordingStartFrame), "The previous isolated-hit path cannot train this exact catalog pattern")
        val training = assertIs<JangdanEchoTraining.Cycle>(
            selectJangdanEchoTraining(plan, scheduled, lengths, 0, 40_000),
        )
        assertEquals(82_687.5, training.periodFrames)
        assertEquals(scheduled.cycleStartFrames, training.cycleStartFrames)
        assertEquals(292_163L, scheduled.recordingStartFrame)
        assertTrue(training.phaseEndFrame - training.phaseStartFrame > 44_100 * 1.5)
        assertTrue(training.trainingEnd + 441 < scheduled.recordingStartFrame,
            "Fitting must become ready before the probe stops collecting preparation samples")
    }

    @Test fun airplaneStillUsesTheExistingIsolatedResponseModel() {
        val plan = assertNotNull(planJangdan(JangdanPlayback("1", "기본", 0, 4,
            listOf("덩", "덕", "궁", "덕"), listOf(4)), 77, 1))
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        assertIs<JangdanEchoTraining.Isolated>(selectJangdanEchoTraining(plan, scheduled, lengths, 0, 40_000))
    }

    @Test fun physicalInputShiftIsAppliedExactlyOnceToTrainingAndPlayback() {
        val plan = arirang()
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        val baseline = assertIs<JangdanEchoTraining.Cycle>(
            selectJangdanEchoTraining(plan, scheduled, lengths, 0, 40_000))
        for (shift in listOf(-220L, 3_210L)) {
            val shifted = assertIs<JangdanEchoTraining.Cycle>(
                selectJangdanEchoTraining(plan, scheduled, lengths, shift, 40_000 + shift))
            assertEquals(baseline.cycleStartFrames.map { it + shift }, shifted.cycleStartFrames)
            assertEquals(baseline.trainingEnd + shift, shifted.trainingEnd)
            assertEquals(baseline.playbackOriginFrame + shift, shifted.playbackOriginFrame)
            assertEquals(baseline.phaseStartFrame, shifted.phaseStartFrame)
            assertEquals(baseline.phaseEndFrame, shifted.phaseEndFrame)
        }
    }

    @Test fun sectionAndPickupKeepTheirOriginalScorePhaseAfterThreePreparationCycles() {
        for ((startTick, sectionTick) in listOf(0L to 4.5, 0L to 101.25, 3L to 0.0)) {
            val base = arirang()
            val plan = base.copy(mapping = base.mapping.copy(startTick = startTick), recordingStartTick = sectionTick)
            val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
            val training = assertIs<JangdanEchoTraining.Cycle>(
                selectJangdanEchoTraining(plan, scheduled, lengths, 0, 40_000))
            val expectedPhase = (sectionTick - startTick) / 9 * 82_687.5
            assertEquals(expectedPhase, 292_162.5 - training.playbackOriginFrame, .001)
            assertEquals(292_163L, scheduled.recordingStartFrame, "Score phase must not lengthen count-in")
        }
    }

    @Test fun missingOrChangedPreparationOutputCannotBecomeATrustedCycleReference() {
        val plan = arirang()
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        val events = scheduled.preparationEvents
        assertNull(selectJangdanEchoTraining(plan,
            scheduled.copy(preparationEvents = events.dropLast(1)), lengths, 0, 40_000))
        assertNull(selectJangdanEchoTraining(plan,
            scheduled.copy(preparationEvents = events.mapIndexed { i, hit ->
                if (i == 7) hit.copy(gain = hit.gain / 2) else hit
            }), lengths, 0, 40_000))
        assertNull(selectJangdanEchoTraining(plan,
            scheduled.copy(cycleStartFrames = scheduled.cycleStartFrames.dropLast(1)), lengths, 0, 40_000))
    }

    @Test fun initialSilenceStillNeedsTheExistingThreeTenthsOfASecond() {
        val plan = arirang()
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        assertNull(selectJangdanEchoTraining(plan, scheduled, lengths, 0, 17_000))
    }

    @Test fun firstCycleDoesNotTrainPhaseThatWouldContainAPreviousCyclesLongTail() {
        val plan = arirang()
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        val longResponse = lengths + (JangdanClip(JangdanSample.Deok) to 22_050)
        val training = assertIs<JangdanEchoTraining.Cycle>(
            selectJangdanEchoTraining(plan, scheduled, longResponse, 0, 40_000))
        assertTrue(training.phaseStartFrame > 44_100 * .2,
            "The last 500 ms deok response extends over 200 ms into the next cycle")
        assertTrue(training.trainingEnd + 441 < scheduled.recordingStartFrame)
    }

    @Test fun actualPreparationSelectionFeedsTheCycleCalibratorAndCorrectsOnlyPerformanceSamples() {
        val plan = arirang()
        val schedule = JangdanFrameSchedule(plan, 44_100, 3, 0, 0)
        val scheduled = schedule.describe(0)
        val geometry = assertIs<JangdanEchoTraining.Cycle>(
            selectJangdanEchoTraining(plan, scheduled, lengths, 0, 40_000))
        val events = buildList {
            repeat(20) { addAll(schedule.takeAt(assertNotNull(schedule.nextFrame))) }
        }
        val input = FloatArray(scheduled.recordingStartFrame.toInt() + 100_000)
        for (event in events) repeat(lengths.getValue(event.clip) + 5_292) { index ->
            val t = index / 44_100.0
            val value = exp(-t / .09) * (.14 * sin(2 * PI * 183 * t) +
                .12 * sin(2 * PI * (1_237 + event.clip.sample.ordinal * 173) * t + 170 * t * t) +
                .06 * sin(2 * PI * 3_927 * t))
            input[event.frame.toInt() + index] += (value * event.gain).toFloat()
        }
        val accepted = assertIs<EchoCalibrationResult.Accepted>(
            calibrateJangdanEchoTraining(input.copyOf(geometry.trainingEnd.toInt()), geometry, plan, lengths))
        val filter = RecordingEchoFilter(accepted.model)
        val actual = events.map { EchoPlaybackEvent(it.clip, it.frame, it.gain) }
        var beforeEnergy = 0.0
        var afterEnergy = 0.0
        var changedBlocks = 0
        val count = 882
        val begin = scheduled.recordingStartFrame.toInt()
        for (first in begin until begin + 82_687 - count step count) {
            val block = ShortArray(count) { (input[first + it] * 32768).roundToInt().toShort() }
            beforeEnergy += block.sumOf { it.toDouble() * it }
            if (filter.process(block, count, first.toLong(), actual)) changedBlocks++
            afterEnergy += block.sumOf { it.toDouble() * it }
        }
        assertTrue(changedBlocks >= 5, "The production geometry must enable actual correction, not just select a non-null plan")
        assertTrue(afterEnergy < beforeEnergy * .7, "A known speaker-only overlapping pattern should lose substantial energy")
    }
}
