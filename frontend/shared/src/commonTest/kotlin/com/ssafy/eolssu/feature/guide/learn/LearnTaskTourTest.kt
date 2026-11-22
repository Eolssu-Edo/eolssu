package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearnTaskTourTest {

    /** 2~7단계에 할 일 따라 배우기가 있고, 1단계(악기 짚기)와 숨긴 8·9단계는 없다(S15P31E102-658). */
    @Test
    fun stagesTwoToSevenHaveTaskTour() {
        assertEquals((2..7).toList(), (1..9).filter(::hasTaskTour))
    }

    /** 서버 할 일 순서와 짝: 2단계 앉은 자세·악기 잡기·복식호흡, 3단계 취구에 입술 대기·입김·한 소리 길게 내기. */
    @Test
    fun visualsFollowServerTaskOrder() {
        assertEquals(listOf(TaskVisual.Posture, TaskVisual.Hands, TaskVisual.Breath), (0..2).map { taskVisual(2, it) })
        assertEquals(listOf(TaskVisual.Mouth, TaskVisual.Blow, TaskVisual.FirstNote), (0..2).map { taskVisual(3, it) })
    }

    /**
     * 고정된 단계 머리의 '할 일 n / 전체'(S15P31E102-1244): 연습 박스는 하나로 더하고, 박스 안에서 듣는 연습은 더하지 않는다.
     * 할 일 박스가 없으면 박스 안 듣기도 연습 박스 하나로 센다.
     */
    @Test
    fun stepTotalCountsPracticeBoxOnce() {
        val box = StepPractice(title = "연습") {}
        val inline = StepPractice(title = "연습", segment = { _, _ -> }) {}
        assertEquals(4, stepTotal(4, null))
        assertEquals(4, stepTotal(3, box))
        assertEquals(2, stepTotal(2, inline))
        assertEquals(1, stepTotal(0, inline))
        assertEquals(1, stepTotal(0, box))
        assertEquals(0, stepTotal(0, null))
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
