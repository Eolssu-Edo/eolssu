package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YinPitchEstimatorTest {
    @Test fun detectsRangeHarmonicsAndRejectsQuietAndAperiodicInputs() {
        for (hz in listOf(220, 440, 880, 1760, 2500)) {
            val input = FloatArray(2048) { i ->
                (0.3 * sin(2 * PI * hz * i / 44100) + 0.1 * sin(4 * PI * hz * i / 44100)).toFloat()
            }
            val result = requireNotNull(YinPitchEstimator.estimate(input, 44100))
            assertTrue(abs(result.frequencyHz - hz) / hz < 0.01, "$hz: $result")
            assertTrue(result.normalizedDifference in 0.0..0.15)
        }
        assertNull(YinPitchEstimator.detect(FloatArray(2048) { 0.5f }, 44100))
        assertNull(YinPitchEstimator.detect(FloatArray(2048) { (0.001 * sin(2 * PI * 440 * it / 44100)).toFloat() }, 44100))
        val random = kotlin.random.Random(515)
        assertNull(YinPitchEstimator.detect(FloatArray(2048) { random.nextFloat() * 0.8f - 0.4f }, 44100))
    }

    @Test fun shortStableNotesHaveACompleteWindowAtDifferentHopOffsets() {
        for (length in listOf(4410, 7350)) {
            for (offset in 0..882 step 147) {
                val input = FloatArray(length + offset + 2048) { i ->
                    if (i in offset until offset + length) (0.4 * sin(2 * PI * 880 * i / 44100)).toFloat() else 0f
                }
                val complete = (0..input.size - 2048 step 882).filter { it >= offset && it + 2048 <= offset + length }
                assertTrue(complete.isNotEmpty())
                assertTrue(complete.any { start ->
                    val hz = YinPitchEstimator.detect(input.copyOfRange(start, start + 2048), 44100)
                    hz != null && abs(hz - 880) < 8.8
                }, "length=$length offset=$offset")
            }
        }
    }

    @Test fun detectsToneAndRejectsSilence() {
        val rate = 44100
        val samples = FloatArray(2048) { (0.4 * sin(2 * PI * 440 * it / rate)).toFloat() }
        val detected = requireNotNull(YinPitchEstimator.detect(samples, rate))
        assertTrue(abs(detected - 440.0) < 3.0, "detected $detected Hz")
        assertNull(YinPitchEstimator.detect(FloatArray(2048), rate))
    }
}
