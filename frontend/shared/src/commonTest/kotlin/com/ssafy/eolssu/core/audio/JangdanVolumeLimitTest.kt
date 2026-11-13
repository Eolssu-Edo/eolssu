package com.ssafy.eolssu.core.audio

import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 평가 녹음 중 장단 크기 압축(S15P31E102-878). 문턱 K 아래는 그대로, K~최대를 K~상한 T로 dB에서 고르게 줄인다. */
class JangdanVolumeLimitTest {
    private val knee = -20f
    private val ceiling = -14f
    private val top = 0f

    private fun heardDb(system: Float) = system + 20 * log10(evaluationJangdanGain(system, knee, ceiling, top))

    @Test fun belowOrAtTheKneeKeepsTheJangdanAsIs() {
        assertEquals(1f, evaluationJangdanGain(-35f, knee, ceiling, top))
        assertEquals(1f, evaluationJangdanGain(knee, knee, ceiling, top))
    }

    @Test fun maximumSystemVolumeSoundsLikeTheCeiling() {
        assertEquals(ceiling, heardDb(top), 0.01f)
    }

    @Test fun volumeBetweenKneeAndTopIsCompressedEvenlyInDecibels() {
        // 20dB 범위가 6dB로 줄어 시스템 10dB 증가는 장단 3dB 증가다.
        assertEquals(-17f, heardDb(-10f), 0.01f)
    }

    @Test fun raisingTheSystemVolumeNeverMakesTheJangdanQuieter() {
        var previous = Float.NEGATIVE_INFINITY
        for (step in 0..60) {
            val heard = heardDb(-30f + step * 0.5f)
            assertTrue(heard > previous, "system ${-30f + step * 0.5f}dB")
            previous = heard
        }
    }

    @Test fun anInvalidCurveLeavesTheJangdanAsIs() {
        assertEquals(1f, evaluationJangdanGain(-5f, -10f, -8f, -10f)) // 최대가 문턱보다 크지 않음
        assertEquals(1f, evaluationJangdanGain(-5f, -10f, -12f, 0f))  // 상한이 문턱보다 작음
        assertEquals(1f, evaluationJangdanGain(-5f, -10f, 2f, 0f))    // 상한이 최대보다 큼
        assertEquals(1f, evaluationJangdanGain(Float.NaN, knee, ceiling, top))
    }
}
