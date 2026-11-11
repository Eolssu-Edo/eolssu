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
import kotlin.test.assertNotNull

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

    @Test fun diagnosticsDistinguishPeriodicInputFromLoudNoiseWithoutChangingStatus() {
        val random = Random(73)
        val periodic = cleanCapture()
        val noisy = cleanCapture()
        for (i in quiet[0].startFrame.toInt() until quiet[0].endFrame.toInt()) {
            periodic[i] = (.04 * sin(2 * PI * 467 * i / rate)).toFloat()
            noisy[i] = random.nextFloat() * .2f - .1f
        }
        val toneResult = assertIs<EchoCalibrationResult.Rejected>(calibrate(periodic))
        val noiseResult = assertIs<EchoCalibrationResult.Rejected>(calibrate(noisy))
        assertEquals(RecordingEchoStatus.NotQuiet, toneResult.reason)
        assertEquals(RecordingEchoStatus.NotQuiet, noiseResult.reason)
        val tone = assertNotNull(toneResult.diagnostic)
        val noise = assertNotNull(noiseResult.diagnostic)
        assertEquals(EchoCalibrationGate.QuietPeriodicInput, tone.gate)
        assertEquals(EchoCalibrationGate.QuietLoudInput, noise.gate)
        assertTrue(assertNotNull(tone.value) > assertNotNull(tone.limit))
        assertTrue(assertNotNull(tone.rms) > .006)
        assertEquals(3, tone.count)
        assertTrue(assertNotNull(tone.startFrame) >= quiet[0].startFrame)
        assertTrue(assertNotNull(tone.endFrame) <= quiet[0].endFrame)
        assertTrue(assertNotNull(noise.value) > assertNotNull(noise.limit))
    }

    @Test fun diagnosticsDistinguishGeometryClippingAndRepeatFailures() {
        val capture = cleanCapture()
        val incomplete = assertIs<EchoCalibrationResult.Rejected>(calibrate(capture.copyOf(rate * 4)))
        val tooFew = assertIs<EchoCalibrationResult.Rejected>(RecordingEchoCalibrator.calibrate(
            capture, strikes.take(2), mapOf(clip to clipLength), quiet))
        capture.fill(1f, starts.first(), starts.first() + 1000)
        val clipped = assertIs<EchoCalibrationResult.Rejected>(calibrate(capture))
        for (result in listOf(incomplete, tooFew, clipped)) {
            assertEquals(RecordingEchoStatus.Unreliable, result.reason)
        }
        assertEquals(EchoCalibrationGate.InvalidStrikeWindow, assertNotNull(incomplete.diagnostic).gate)
        val repeatDiagnostic = assertNotNull(tooFew.diagnostic)
        assertEquals(EchoCalibrationGate.InsufficientRepeats, repeatDiagnostic.gate)
        assertEquals(EchoCalibrationGate.Clipping, assertNotNull(clipped.diagnostic).gate)
        assertEquals(clip, repeatDiagnostic.clip)
        assertEquals(2, repeatDiagnostic.count)
    }

    @Test fun diagnosticLocatesStrikeResponseThatOverlapsThePreviousQuietWindow() {
        val precedingQuiet = EchoQuietWindow(rate * 2L - 4000, rate * 2L)
        val result = assertIs<EchoCalibrationResult.Rejected>(RecordingEchoCalibrator.calibrate(
            cleanCapture(), strikes, mapOf(clip to clipLength), quiet + precedingQuiet))
        assertEquals(RecordingEchoStatus.Unreliable, result.reason)
        val diagnostic = assertNotNull(result.diagnostic)
        assertEquals(EchoCalibrationGate.QuietWindowOverlap, diagnostic.gate)
        assertEquals(clip, diagnostic.clip)
        assertEquals(precedingQuiet, diagnostic.quietWindow)
        assertTrue(assertNotNull(diagnostic.startFrame) < precedingQuiet.endFrame)
        assertTrue(assertNotNull(diagnostic.endFrame) > precedingQuiet.startFrame)
    }

    @Test fun diagnosticSeparatesWeakTemplateFromInconsistentRepeats() {
        val weak = cleanCapture().map { it * .001f }.toFloatArray()
        val result = assertIs<EchoCalibrationResult.Rejected>(calibrate(weak))
        assertEquals(RecordingEchoStatus.Unreliable, result.reason)
        val diagnostic = assertNotNull(result.diagnostic)
        assertEquals(EchoCalibrationGate.TemplatePower, diagnostic.gate)
        assertEquals(clip, diagnostic.clip)
        assertTrue(assertNotNull(diagnostic.value) < assertNotNull(diagnostic.limit))
    }

    @Test fun rejectsInstrumentPresentOnlyDuringCalibrationStrikes() {
        // Quiet gaps and matching high-frequency attacks do not prove that the bass
        // response is clean: this previously learned a nonexistent 467 Hz held note.
        val capture = FloatArray(rate * 5)
        starts.forEachIndexed { repeat, start ->
            for (i in 0 until clipLength) {
                val envelope = exp(-i.toDouble() / (rate * .07))
                val strike = envelope * (.13 * sin(2 * PI * 180 * i / rate) +
                    .08 * sin(2 * PI * 1237 * i / rate))
                capture[start + i] = (strike + .08 * sin(2 * PI * 467 * i / rate + repeat * .7)).toFloat()
            }
        }
        val result = assertIs<EchoCalibrationResult.Rejected>(calibrate(capture))
        assertEquals(RecordingEchoStatus.Unreliable, result.reason)
        val diagnostic = assertNotNull(result.diagnostic)
        assertEquals(EchoCalibrationGate.ResponseLowResidual, diagnostic.gate)
        assertTrue(assertNotNull(diagnostic.value) > assertNotNull(diagnostic.limit))
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
        val result = assertIs<EchoCalibrationResult.Rejected>(calibrate(inconsistent))
        assertEquals(RecordingEchoStatus.Unreliable, result.reason)
        assertEquals(EchoCalibrationGate.RepeatMatchCount, assertNotNull(result.diagnostic).gate)
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
