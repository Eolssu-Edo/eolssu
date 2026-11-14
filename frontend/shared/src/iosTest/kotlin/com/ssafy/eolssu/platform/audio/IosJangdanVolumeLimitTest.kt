package com.ssafy.eolssu.platform.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosJangdanVolumeLimitTest {
    // 시험용 곡선: 0.0625(-48dB)부터 1(0dB)까지 dB 선형.
    private val curve = IosVolumeCurve(listOf(0.0625f to -48f, 1f to 0f))

    @Test
    fun curveInterpolatesAndClamps() {
        assertEquals(-24f, curve.db(0.53125f), 0.01f)
        assertEquals(-48f, curve.db(0f))
        assertEquals(0f, curve.db(1f))
    }

    @Test
    fun belowKneeIsUnchanged() {
        assertEquals(1f, iosEvaluationGain(40f / 150, curve))
        assertEquals(1f, iosEvaluationGain(0.1f, curve))
    }

    @Test
    fun maxVolumeIsHeardAtCeiling() {
        val gain = iosEvaluationGain(1f, curve)
        // 최대(0dB)에서 들리는 크기가 60/150 위치의 dB가 된다.
        assertEquals(curve.db(60f / 150), 20 * log10(gain), 0.01f)
    }

    @Test
    fun heardLevelRisesWithVolume() {
        var previous = Float.NEGATIVE_INFINITY
        for (step in 1..16) {
            val volume = step / 16f
            val heard = curve.db(volume) + 20 * log10(iosEvaluationGain(volume, curve))
            assertTrue(heard >= previous - 0.001f, "step $step: $heard < $previous")
            previous = heard
        }
    }

    @Test
    fun mutedOrInvalidVolumeIsNotLimited() {
        assertEquals(1f, iosEvaluationGain(0f, curve))
        assertEquals(1f, iosEvaluationGain(Float.NaN, curve))
        assertTrue(abs(iosEvaluationGain(1.5f, curve) - 1f) < 1e-6f)
    }

    @Test
    fun iphoneCurveHearsMaxVolumeAsVolume04() {
        // 실측 곡선(60dB 직선)에서 최대 볼륨은 볼륨 0.4(-36dB) 크기로 들린다.
        assertEquals(-36f, 20 * log10(iosEvaluationGain(1f, IphoneSpeakerCurve)), 0.01f)
        assertEquals(1f, iosEvaluationGain(0.25f, IphoneSpeakerCurve))
    }
}
