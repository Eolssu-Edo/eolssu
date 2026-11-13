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

    @Test fun unknownOrBrokenVolumeLeavesTheJangdanAsIs() {
        assertEquals(1f, androidEvaluationGain(10, 0, curve))
        assertEquals(1f, androidEvaluationGain(16, 15, curve))
        assertEquals(1f, androidEvaluationGain(10, 15) { 0f }) // 곡선이 평평하면 줄일 근거가 없다
    }
}
