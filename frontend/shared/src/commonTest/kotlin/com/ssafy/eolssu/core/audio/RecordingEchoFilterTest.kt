package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals

class RecordingEchoFilterTest {
    private val rate = 44100
    private val clip = JangdanClip(JangdanSample.Deong)
    private val clipLength = rate / 5
    private val starts = listOf(rate, rate * 2, rate * 3, rate * 4)
    private val strikes = starts.map { EchoCalibrationStrike(clip, it.toLong(), 0.5f) }
    private val quiet = listOf(EchoQuietWindow(rate / 5L, rate * 4L / 5))

    private fun cleanCapture(): FloatArray {
        val random = Random(695)
        val waveform = FloatArray(clipLength) { i ->
            val envelope = exp(-i.toDouble() / (rate * .07))
            (envelope * (.13 * sin(2 * PI * 180 * i / rate) +
                .08 * sin(2 * PI * 1237 * i / rate) + .025 * (random.nextDouble() - .5))).toFloat()
        }
        return FloatArray(rate * 5).also { capture ->
            starts.forEach { start -> waveform.forEachIndexed { i, value -> capture[start + i] = value } }
        }
    }

    private fun calibrate(capture: FloatArray) = RecordingEchoCalibrator.calibrate(
        capture, strikes, mapOf(clip to clipLength), quiet,
    )

    @Test fun rejectsPersistentInstrumentInQuietCalibrationWindow() {
        val capture = cleanCapture()
        for (i in quiet[0].startFrame.toInt() until quiet[0].endFrame.toInt()) {
            capture[i] += (.04 * sin(2 * PI * 467 * i / rate)).toFloat()
        }
        assertEquals(RecordingEchoStatus.NotQuiet,
            assertIs<EchoCalibrationResult.Rejected>(calibrate(capture)).reason)
    }

    @Test fun acceptsSeparateConsistentStrikesAfterSilence() {
        assertIs<EchoCalibrationResult.Accepted>(calibrate(cleanCapture()))
    }

    @Test fun rejectsLoudAperiodicInputAndNonRepeatingStrikes() {
        val random = Random(73)
        val loud = cleanCapture()
        for (i in quiet[0].startFrame.toInt() until quiet[0].endFrame.toInt()) {
            loud[i] = random.nextFloat() * .2f - .1f
        }
        assertEquals(RecordingEchoStatus.NotQuiet,
            assertIs<EchoCalibrationResult.Rejected>(calibrate(loud)).reason)
        val inconsistent = cleanCapture()
        for (start in starts) {
            for (i in start until start + clipLength) inconsistent[i] = random.nextFloat() * .2f - .1f
        }
        assertEquals(RecordingEchoStatus.Unreliable,
            assertIs<EchoCalibrationResult.Rejected>(calibrate(inconsistent)).reason)
    }

    @Test fun incompleteOrSilentCalibrationNeverCreatesAModel() {
        val capture = cleanCapture()
        assertIs<EchoCalibrationResult.Rejected>(RecordingEchoCalibrator.calibrate(
            capture, strikes.take(2), mapOf(clip to clipLength), quiet))
        assertIs<EchoCalibrationResult.Rejected>(RecordingEchoCalibrator.calibrate(
            capture, strikes, mapOf(clip to clipLength), emptyList()))
        assertIs<EchoCalibrationResult.Rejected>(calibrate(FloatArray(capture.size)))
        assertIs<EchoCalibrationResult.Rejected>(calibrate(capture.copyOf(rate * 4)))
    }

    @Test fun laterPerformanceCannotTrainOrChangeTheModel() {
        val capture = cleanCapture()
        val model = assertIs<EchoCalibrationResult.Accepted>(calibrate(capture)).model
        for (i in rate * 9 / 2 until capture.size) capture[i] = .8f
        val changed = assertIs<EchoCalibrationResult.Accepted>(calibrate(capture)).model
        assertContentEquals(model.templates.getValue(clip), changed.templates.getValue(clip))
    }

    @Test fun subtractsMatchedPlaybackWithoutChangingCountOrUntouchedBufferTail() {
        val capture = cleanCapture()
        val model = assertIs<EchoCalibrationResult.Accepted>(calibrate(capture)).model
        val firstFrame = rate * 6L + 882
        val pcm = ShortArray(900) { i -> (capture[rate + 882 + i] * 32768).roundToInt().toShort() }
        val before = pcm.copyOf()
        assertTrue(RecordingEchoFilter(model).process(pcm, 882, firstFrame,
            listOf(EchoPlaybackEvent(clip, rate * 6L, .5f))))
        val oldEnergy = before.take(882).sumOf { it.toDouble() * it }
        val newEnergy = pcm.take(882).sumOf { it.toDouble() * it }
        assertTrue(newEnergy < oldEnergy * .25, "$newEnergy / $oldEnergy")
        assertContentEquals(before.copyOfRange(882, 900), pcm.copyOfRange(882, 900))
    }

    @Test fun silenceUnrelatedInstrumentAndMissingPlaybackStayExactlyRaw() {
        val model = assertIs<EchoCalibrationResult.Accepted>(calibrate(cleanCapture())).model
        val events = listOf(EchoPlaybackEvent(clip, rate * 6L, .5f))
        val filter = RecordingEchoFilter(model)
        val silent = ShortArray(882)
        assertFalse(filter.process(silent, silent.size, rate * 6L + 882, events))
        assertContentEquals(ShortArray(882), silent)
        val tone = ShortArray(882) { (.3 * sin(2 * PI * 467 * it / rate) * 32768).roundToInt().toShort() }
        val before = tone.copyOf()
        assertFalse(filter.process(tone, tone.size, rate * 6L + 882, events))
        assertContentEquals(before, tone)
        assertFalse(filter.process(tone, tone.size, rate * 6L + 882, emptyList()))
        assertContentEquals(before, tone)
    }

    @Test fun acceptsFullFourSecondRollAndPreservesItsLatePlayback() {
        val roll = JangdanClip(JangdanSample.Gutgeori59, rate * 4)
        val waveform = FloatArray(rate * 4) { i ->
            val modulation = .4 + .3 * sin(2 * PI * 7 * i / rate)
            (modulation * (.13 * sin(2 * PI * 180 * i / rate) + .08 * sin(2 * PI * 1237 * i / rate))).toFloat()
        }
        val rollStarts = listOf(1, 6, 11).map { it * rate }
        val capture = FloatArray(rate * 16)
        rollStarts.forEach { start -> waveform.copyInto(capture, start) }
        val result = RecordingEchoCalibrator.calibrate(capture,
            rollStarts.map { EchoCalibrationStrike(roll, it.toLong(), .5f) }, mapOf(roll to waveform.size), quiet)
        val model = assertIs<EchoCalibrationResult.Accepted>(result).model
        val pcm = ShortArray(882) { (waveform[rate * 3 + it] * 32768).roundToInt().toShort() }
        assertTrue(RecordingEchoFilter(model).process(pcm, pcm.size, rate * 23L,
            listOf(EchoPlaybackEvent(roll, rate * 20L, .5f))))
    }
}
