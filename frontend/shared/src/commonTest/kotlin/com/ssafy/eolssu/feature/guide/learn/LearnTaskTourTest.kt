package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearnTaskTourTest {

    /** 2~8단계에 할 일 따라 배우기가 있고, 1단계(악기 짚기)와 선택 단계 9단계는 없다. */
    @Test
    fun stagesTwoToEightHaveTaskTour() {
        assertEquals((2..8).toList(), (1..9).filter(::hasTaskTour))
    }

    /** 서버 할 일 순서와 짝: 2단계 앉은 자세·악기 잡기·복식호흡, 3단계 취구에 입술 대기·입김·한 소리 길게 내기. */
    @Test
    fun visualsFollowServerTaskOrder() {
        assertEquals(listOf(TaskVisual.Posture, TaskVisual.Hands, TaskVisual.Breath), (0..2).map { taskVisual(2, it) })
        assertEquals(listOf(TaskVisual.Mouth, TaskVisual.Blow, TaskVisual.FirstNote), (0..2).map { taskVisual(3, it) })
    }

    /** 서버가 할 일을 더 주면 남는 칸은 그 단계 그림으로 보인다. */
    @Test
    fun extraTasksFallBackToStageArt() {
        assertEquals(TaskVisual.Posture, taskVisual(2, 5))
        assertEquals(TaskVisual.Blow, taskVisual(3, 5))
    }

    /** 왼손·오른손이 막는 구멍을 합치면 운지 그림의 구멍을 빠짐없이 한 번씩 덮고, 단소 뒷구멍은 왼손(엄지)이다. */
    @Test
    fun handsCoverEveryHoleOnce() {
        GuideInstrument.entries.forEach { instrument ->
            val left = instrument.leftHandHoles
            val right = instrument.rightHandHoles
            assertEquals(instrument.holes, left + right, instrument.id)
            assertFalse(left.any { it in right }, instrument.id)
        }
        assertTrue(GuideInstrument.Danso.holes[0] in GuideInstrument.Danso.leftHandHoles)
    }

    /** 숨쉬기 안내는 앞 절반 들이쉬기, 뒤 절반 내쉬기이고, 크기는 들이쉬며 커졌다가 내쉬며 작아진다. */
    @Test
    fun breathCycleGrowsThenShrinks() {
        assertTrue(breathInhaling(0.1f))
        assertFalse(breathInhaling(0.6f))
        assertEquals(0f, breathSize(0f), 0.001f)
        assertEquals(1f, breathSize(0.5f), 0.001f)
        assertEquals(0f, breathSize(1f), 0.001f)
        assertTrue(breathSize(0.25f) in 0.1f..0.9f)
    }
}
