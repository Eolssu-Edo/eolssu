package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.RecordingEchoCalibrator
import com.ssafy.eolssu.core.audio.EchoCalibrationResult
import com.ssafy.eolssu.core.audio.RecordingEchoStatus
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertContentEquals

class JangdanEchoPreparationTest {
    private val deong = JangdanClip(JangdanSample.Deong)
    private val deok = JangdanClip(JangdanSample.Deok)
    private val kung = JangdanClip(JangdanSample.Kung)
    private val lengths = mapOf(deong to 17_640, deok to 11_466, kung to 17_640)

    private fun airplane(bpm: Int) = List(12) { beat ->
        RenderedJangdanEvent(listOf(deong, deok, kung, deok)[beat % 4], .65f,
            44_100 + (beat * 60.0 * 44_100 / bpm).roundToLong())
    }

    @Test fun excludedFutureDoubleStrikeDoesNotRejectAlreadyRenderedIsolatedResponses() {
        val plan = assertNotNull(planJangdan(JangdanPlayback("1", "검증", 0, 8,
            listOf("덩", "덕", "궁", "덩", "덕", "궁", "궁", "덕덕"), listOf(4, 4)), 77, 1))
        val scheduled = JangdanFrameSchedule(plan, 44_100, 3, 0, 0).describe(0)
        val shift = 2_000L
        val geometry = assertNotNull(jangdanEchoPreparation(scheduled.preparationEvents, lengths,
            shift, 40_000, scheduled.recordingStartFrame + shift))
        val rendered = scheduled.preparationEvents.filter { it.frame + shift < geometry.trainingEnd }
        assertTrue(rendered.size < scheduled.preparationEvents.size, "The final double strike is still in the future")
        assertFalse(scheduled.preparationEvents.all { it in rendered }, "The previous guard rejected this valid training window")
        assertTrue(renderedPreparationComplete(scheduled.preparationEvents, rendered, shift, geometry.trainingEnd))
        assertFalse(renderedPreparationComplete(scheduled.preparationEvents, rendered.dropLast(1), shift, geometry.trainingEnd),
            "Every attack inside the training interval must still be accepted by AudioTrack")
    }

    @Test fun airplaneAt77BpmSuppliesThreeIsolatedResponsesPerClipBeforeTheFirstPerformanceBeat() {
        val start = 44_100 + (12 * 60.0 * 44_100 / 77).roundToLong()
        val plan = assertNotNull(jangdanEchoPreparation(airplane(77), lengths, 0, 40_000, start))
        assertEquals(mapOf(deong to 3, deok to 6, kung to 3), plan.strikes.groupingBy { it.clip }.eachCount())
        assertTrue(start - plan.trainingEnd > 17_000, "Keep time for fitting without moving the start beat")
        assertTrue(plan.quiet.size >= 3, "Check actual gaps as well as the initial silence")
        assertTrue(plan.quiet.all { it.endFrame <= plan.trainingEnd })
    }

    @Test fun fasterOverlappingPatternCannotTrainNeighboringStrikesIntoTheSameTemplate() {
        val start = 44_100 + (12 * 60.0 * 44_100 / 160).roundToLong()
        assertNull(jangdanEchoPreparation(airplane(160), lengths, 0, 40_000, start))
    }

    @Test fun oneBadRepeatedClipDisablesTheWholeCorrectionInsteadOfSilentlyDroppingItsReference() {
        val events = airplane(77).toMutableList()
        events[2] = events[2].copy(frame = events[1].frame + 1_000)
        val start = 44_100 + (12 * 60.0 * 44_100 / 77).roundToLong()
        assertNull(jangdanEchoPreparation(events, lengths, 0, 40_000, start))
    }

    @Test fun shiftIsAppliedOnceToEveryInputPositionAndCannotIncludePerformanceAudio() {
        val outputStart = 44_100 + (12 * 60.0 * 44_100 / 77).roundToLong()
        val first = assertNotNull(jangdanEchoPreparation(airplane(77), lengths, 0, 40_000, outputStart))
        val shifted = assertNotNull(jangdanEchoPreparation(airplane(77), lengths, 2_000, 42_000, outputStart + 2_000))
        assertEquals(first.strikes.map { it.startFrame + 2_000 }, shifted.strikes.map { it.startFrame })
        assertEquals(first.trainingEnd + 2_000, shifted.trainingEnd)
        assertTrue(shifted.trainingEnd < outputStart + 2_000)
    }

    @Test fun captureBlockIsSplitWithoutKeepingPreparationOrDroppingTheFirstPerformanceSample() {
        val pcm = ShortArray(882) { it.toShort() }
        val prefix = preparationPrefixFrames(10_000, pcm.size, 10_327)
        assertEquals(327, prefix)
        val encoded = pcm.copyOfRange(prefix, pcm.size)
        assertEquals(327.toShort(), encoded.first())
        assertEquals(881.toShort(), encoded.last())
        assertEquals(555, encoded.size)
        assertEquals(0, preparationPrefixFrames(10_882, 882, 10_327))
        assertEquals(882, preparationPrefixFrames(9_118, 882, 10_327))
        assertEquals(882, preparationPrefixFrames(10_000, 882, 10_882))
    }

    @Test fun hardwareNanosecondsMapBackToTheScheduledInputFrame() {
        val zero = 9_000_000_000L
        for (frame in listOf(0L, 1L, 44_100L, 456_472L)) {
            assertEquals(frame, inputFrameAtNanos(zero + frame * 1_000_000_000 / 44_100, zero))
        }
    }

    private fun patternedCapture(): FloatArray {
        val capture = FloatArray(44_100 * 12)
        airplane(77).forEach { hit ->
            repeat(lengths.getValue(hit.clip)) { i ->
                val envelope = exp(-i / (44_100 * .07))
                capture[hit.frame.toInt() + i] = (envelope * (.13 * sin(2 * PI * 180 * i / 44_100) +
                    .08 * sin(2 * PI * (1237 + hit.clip.sample.ordinal * 71) * i / 44_100))).toFloat()
            }
        }
        return capture
    }

    @Test fun actualPatternGeometryWorksWithTheUnchangedCalibratorAndExcludesLaterPerformance() {
        val start = 44_100 + (12 * 60.0 * 44_100 / 77).roundToLong()
        val plan = assertNotNull(jangdanEchoPreparation(airplane(77), lengths, 0, 40_000, start))
        val capture = patternedCapture()
        fun fit() = assertIs<EchoCalibrationResult.Accepted>(RecordingEchoCalibrator.calibrate(
            capture, plan.strikes, lengths, plan.quiet)).model
        val before = fit()
        capture.fill(.8f, start.toInt())
        val after = fit()
        lengths.keys.forEach { clip -> assertContentEquals(before.templates.getValue(clip), after.templates.getValue(clip)) }
    }

    @Test fun PlayingDuringTheRealPatternQuietGapStillRejectsTheCalibration() {
        val start = 44_100 + (12 * 60.0 * 44_100 / 77).roundToLong()
        val plan = assertNotNull(jangdanEchoPreparation(airplane(77), lengths, 0, 40_000, start))
        val capture = patternedCapture()
        val gap = plan.quiet.last()
        for (i in gap.startFrame.toInt() until gap.endFrame.toInt()) {
            capture[i] = (.04 * sin(2 * PI * 467 * i / 44_100)).toFloat()
        }
        val rejected = assertIs<EchoCalibrationResult.Rejected>(RecordingEchoCalibrator.calibrate(
            capture, plan.strikes, lengths, plan.quiet))
        assertEquals(RecordingEchoStatus.NotQuiet, rejected.reason)
    }
}
