package com.ssafy.eolssu.core.model

import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.planJangdan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JangdanPlaybackTest {
    private val gutgeori = JangdanPlayback("2", "굿거리", 0, 24,
        listOf("떵", "", "기덕", "쿵", "더러러러", "", "쿵", "", "기덕", "쿵", "더러러러", ""), listOf(3, 3, 3, 3))

    @Test fun resumedCursorIgnoresSmallBackwardDecoderJitterUntilTheNextAttack() {
        val mapping = JangdanPlayback("3", "세마치", 0, 4, listOf("덩", "쿵"), listOf(1, 1))
        val cursor = JangdanCursor(planJangdan(mapping, 60, 4)!!, resumeAfterTick = 2.0)
        assertEquals(emptyList(), cursor.due(1.99))
        assertEquals(emptyList(), cursor.due(2.0))
        assertEquals(emptyList(), cursor.due(2.01))
        assertEquals(listOf(JangdanSample.Deong), cursor.due(4.0).map { it.sample })
        assertEquals(listOf(JangdanSample.Kung), cursor.due(2.0).map { it.sample })
    }

    @Test fun fiveActualIntegerTemposSelectDistinctRecordedRollsWithoutPitchChange() {
        for ((bpm, roll) in listOf(59 to JangdanSample.Gutgeori59, 79 to JangdanSample.Gutgeori79,
            99 to JangdanSample.Gutgeori99, 109 to JangdanSample.Gutgeori109, 119 to JangdanSample.Gutgeori119)) {
            val plan = assertNotNull(planJangdan(gutgeori, bpm, 4))
            val rolls = plan.strikes.filter { it.sample.file.startsWith("gutgeori_roll") }
            assertEquals(listOf(8.0, 20.0), rolls.map { it.tick })
            assertEquals(listOf(roll, roll), rolls.map { it.sample })
            assertEquals(360000.0 / bpm, plan.mapping.cycleTicks * plan.msPerTick, 0.0001)
        }
    }

    @Test fun equivalentTickScalesAndBpmProduceTheSameAudibleTime() {
        val first = JangdanPlayback("3", "중모리", 0, 48, List(12) { "쿵" }, listOf(6, 6))
        val second = first.copy(cycleTicks = 24)
        val a = assertNotNull(planJangdan(first, 88, 4))
        val b = assertNotNull(planJangdan(second, 44, 4))
        assertEquals(a.strikes.map { it.tick * a.msPerTick }, b.strikes.map { it.tick * b.msPerTick })
    }

    @Test fun unpreparedRollOrNonFiveStepTempoDoesNotPretendToPlay() {
        assertNull(planJangdan(gutgeori, 100, 4))
        assertNull(planJangdan(gutgeori.copy(jangdanId = "10", name = "세령산"), 63, 4))
        assertNull(planJangdan(gutgeori.copy(beats = listOf("unknown"), groups = listOf(1)), 99, 4))
    }

    @Test fun absoluteCursorDoesNotAccumulateDriftOrBurstAfterAStallAndResetsOnRepeat() {
        val plan = assertNotNull(planJangdan(JangdanPlayback("3", "중모리", 4, 8,
            listOf("쿵", "덕"), listOf(1, 1)), 60, 4))
        val cursor = JangdanCursor(plan)
        assertEquals(emptyList(), cursor.due(0.0))
        assertEquals(listOf(4.0), cursor.due(4.0).map { it.tick })
        assertEquals(emptyList(), cursor.due(4.0))
        assertEquals(listOf(12.0), cursor.due(12.01).map { it.tick }, "지연 중 놓친 8tick을 몰아서 치지 않는다")
        assertEquals(listOf(4.0), cursor.due(4.0).map { it.tick }, "반복 구간 첫 박을 다시 친다")
        assertEquals(emptyList(), cursor.due(5.0), "탐색한 중간 위치의 이전 타격은 재생하지 않는다")
    }
}
