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

    private fun tone(hz: Double, vararg amplitudes: Double) = FloatArray(2048) { i ->
        amplitudes.withIndex().sumOf { (k, a) -> a * sin(2 * PI * hz * (k + 1) * i / 44100) }.toFloat()
    }

    private fun assertNear(expected: Double, actual: Double) =
        assertTrue(abs(actual - expected) / expected < 0.01, "expected $expected, got $actual")

    @Test fun movesPeriodMultipleReadingsUpToTheSoundingPitch() {
        // 515 실기기 로그: 중(415Hz)을 부는 중에 1/3·1/5 높이(140·84Hz)가 잡혔다(S15P31E102-556 D-2).
        val danso = tone(415.0, 0.3, 0.25, 0.1)
        assertNear(415.0, YinPitchEstimator.correctHarmonic(danso, 44100, 415.0 / 2))
        assertNear(415.0, YinPitchEstimator.correctHarmonic(danso, 44100, 415.0 / 3))
        assertNear(415.0, YinPitchEstimator.correctHarmonic(danso, 44100, 415.0 / 5))
        assertNear(415.0, YinPitchEstimator.correctHarmonic(danso, 44100, 415.0))
    }

    @Test fun keepsOverblownOctaveAsPlayed() {
        // 역취(한 옥타브 위로 분 음)는 830Hz 자리에 소리가 있으므로 고치지 않는다.
        assertNear(830.0, YinPitchEstimator.correctHarmonic(tone(830.0, 0.3, 0.1), 44100, 830.0))
    }

    @Test fun estimateCorrectsCommonSubharmonicAtANoteChange() {
        // 청황(622Hz)에서 중(415Hz)으로 바뀌는 창을 YIN은 두 음의 공통 약수 207Hz로 잡는다.
        // 바뀌는 지점이 창 가운데(1000샘플)면 경계 누설로 207Hz 세기가 0.107배라 고치지 못한다. 알려진 한계다.
        val change = FloatArray(2048) { i ->
            val hz = if (i < 500) 622.3 else 415.0
            (0.3 * sin(2 * PI * hz * i / 44100) + 0.25 * sin(4 * PI * hz * i / 44100) + 0.1 * sin(6 * PI * hz * i / 44100)).toFloat()
        }
        assertNear(415.0, requireNotNull(YinPitchEstimator.estimate(change, 44100)).frequencyHz)
    }

    @Test fun detectsToneAndRejectsSilence() {
        val rate = 44100
        val samples = FloatArray(2048) { (0.4 * sin(2 * PI * 440 * it / rate)).toFloat() }
        val detected = requireNotNull(YinPitchEstimator.detect(samples, rate))
        assertTrue(abs(detected - 440.0) < 3.0, "detected $detected Hz")
        assertNull(YinPitchEstimator.detect(FloatArray(2048), rate))
    }
}
