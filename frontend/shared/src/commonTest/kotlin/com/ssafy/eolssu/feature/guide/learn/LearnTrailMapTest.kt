package com.ssafy.eolssu.feature.guide.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 단계 지도(S15P31E102-688): 호숫가 돌다리. 왼쪽 물가에서 오른쪽 건너편으로 징검돌이 한 줄에 같은 간격으로 놓인다. */
class LearnTrailMapTest {
    @Test fun stagesRunFromLeftStartToRightFinishInOneRow() {
        val points = trailPoints(7)
        assertEquals(7, points.size)
        assertTrue(points.all { it.y == points.first().y }, "한 줄")
        assertTrue(points.zipWithNext().all { (a, b) -> b.x > a.x }, "왼쪽에서 오른쪽으로")
        assertTrue(points.first().x < 0.15f, "왼쪽 물가")
        assertTrue(points.last().x > 0.8f, "오른쪽 건너편")
        val gaps = points.zipWithNext { a, b -> b.x - a.x }
        assertTrue(gaps.all { kotlin.math.abs(it - gaps.first()) < 0.001f }, "같은 간격")
    }

    @Test fun singleStageStandsAtStart() {
        assertEquals(1, trailPoints(1).size)
        assertTrue(trailPoints(1).first().x < 0.15f)
        assertTrue(trailPoints(0).isEmpty())
    }
}
