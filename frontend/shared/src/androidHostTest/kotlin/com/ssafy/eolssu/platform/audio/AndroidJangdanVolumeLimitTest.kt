package com.ssafy.eolssu.platform.audio

import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals

/** 시스템 볼륨 단계를 기기 dB 곡선으로 읽어 평가 중 장단 배율을 정한다(S15P31E102-878). */
class AndroidJangdanVolumeLimitTest {
    // 가짜 곡선: 최대 15단계, 한 단계에 3dB. 4단계(40/150)는 −33dB, 6단계(60/150)는 −27dB.
    private val curve = { index: Int -> -3f * (15 - index) }

    private fun heard(index: Int, max: Int = 15, db: (Int) -> Float = curve) =
        db(index) + 20 * log10(androidEvaluationGain(index, max, db))

    @Test fun volumeUpToFortyOfOneFiftyIsUntouched() {
        for (index in 0..4) assertEquals(1f, androidEvaluationGain(index, 15, curve), "index $index")
    }

    @Test fun maximumVolumeSoundsLikeSixtyOfOneFifty() {
        assertEquals(curve(6), heard(15), 0.01f)
    }

    @Test fun fineGrainedVolumeStepsUseTheSameRatio() {
        // 갤럭시 세밀 볼륨처럼 150단계로 알려 주는 기기도 40·60/150 자리를 문턱·상한으로 쓴다.
        val fine = { index: Int -> -0.3f * (150 - index) }
        assertEquals(1f, androidEvaluationGain(40, 150, fine))
        assertEquals(fine(60), heard(150, 150, fine), 0.01f)
    }

    @Test fun galaxyCurveReportedAsAmplitudeIsReadInDecibels() {
        // Galaxy S24+(Android SDK 37) 실측: getStreamVolumeDb가 dB 대신 0~1 진폭을 준다(최대 1.0, 12단계 0.316 = −10dB).
        val galaxy = floatArrayOf(0f, 0.002f, 0.004f, 0.007244359f, 0.013963687f, 0.026915347f, 0.05188002f, 0.08912509f,
            0.10592538f, 0.14962357f, 0.17782794f, 0.25118864f, 0.31622776f, 0.5f, 0.7f, 1f)
        val db = { index: Int -> galaxy[index] }
        val toDb = { amplitude: Float -> 20 * log10(amplitude) }
        assertEquals(1f, androidEvaluationGain(4, 15, db))
        val atMax = toDb(galaxy[15]) + 20 * log10(androidEvaluationGain(15, 15, db))
        assertEquals(toDb(galaxy[6]), atMax, 0.01f) // 최대 볼륨에서 6단계(60/150) 크기
    }

    @Test fun unknownOrBrokenVolumeLeavesTheJangdanAsIs() {
        assertEquals(1f, androidEvaluationGain(10, 0, curve))
        assertEquals(1f, androidEvaluationGain(16, 15, curve))
        assertEquals(1f, androidEvaluationGain(10, 15) { 0f }) // 곡선이 평평하면 줄일 근거가 없다
    }
}
