package com.ssafy.eolssu.feature.guide.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 단계 지도(S15P31E102-658): 7단계 산길의 마지막 단계가 꼭대기다. */
class LearnTrailMapTest {
    @Test fun lastOfSevenStagesStandsOnTheSummit() {
        val points = trailPoints(7)
        assertEquals(7, points.size)
        assertTrue(points.dropLast(1).all { it.y > points.last().y }, "7단계가 가장 높아야 한다")
        assertTrue(points.last().y < 0.25f, "7단계가 산 꼭대기에 선다")
        assertTrue(points.first().y > 0.8f, "1단계는 산기슭에서 시작한다")
    }

    @Test fun fewerStagesKeepTheLowerPath() {
        assertEquals(trailPoints(7).take(3), trailPoints(3))
    }
}
