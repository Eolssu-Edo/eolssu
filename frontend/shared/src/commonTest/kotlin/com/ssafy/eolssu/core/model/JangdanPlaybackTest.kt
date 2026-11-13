package com.ssafy.eolssu.core.model

import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.planJangdan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.math.roundToInt

class JangdanPlaybackTest {
    private val gutgeori = JangdanPlayback("2", "굿거리", 0, 24,
        listOf("떵", "", "기덕", "쿵", "더러러러", "", "쿵", "", "기덕", "쿵", "더러러러", ""), listOf(3, 3, 3, 3))

    @Test fun danmoriSchedulesEightSubbeatsInFourQuarterBeatsAt77Bpm() {
        val mapping = JangdanPlayback("7", "단모리", 0, 4,
            listOf("덩", "", "따", "따", "궁", "따", "궁", ""), listOf(2, 2, 2, 2))
        val plan = assertNotNull(planJangdan(mapping, 77, 1))
        assertEquals(0.5, mapping.cellTicks)
        assertEquals(listOf(0.0, 1.0, 1.5, 2.0, 2.5, 3.0), plan.strikes.map { it.tick })
        assertEquals(listOf(JangdanSample.Deong, JangdanSample.Deok, JangdanSample.Deok,
            JangdanSample.Kung, JangdanSample.Deok, JangdanSample.Kung), plan.strikes.map { it.clip.sample })
        assertEquals(3116.883117, plan.msPerTick * mapping.cycleTicks, 0.000001)
        val cursor = JangdanCursor(plan)
        val strikes = (0..63).flatMap { cursor.due(it / 2.0) }
        assertEquals(48, strikes.size)
        assertEquals(listOf(0.0, 1.0, 1.5, 2.0, 2.5, 3.0), strikes.take(6).map { it.tick })
        assertEquals(listOf(28.0, 29.0, 29.5, 30.0, 30.5, 31.0), strikes.takeLast(6).map { it.tick })
        assertEquals(3, mapping.cellAt(1.5))
        val jeongak = assertNotNull(planJangdan(mapping.copy(cycleTicks = 24), 77, 6))
        assertEquals(listOf(0.0, 6.0, 9.0, 12.0, 15.0, 18.0), jeongak.strikes.map { it.tick })
        for ((beginnerStrike, jeongakStrike) in plan.strikes.zip(jeongak.strikes)) {
            assertEquals(beginnerStrike.tick * plan.msPerTick, jeongakStrike.tick * jeongak.msPerTick, 0.000001)
        }
    }

    @Test fun jajinTaryeongLeavesTheFourthBeatEmpty() {
        val mapping = JangdanPlayback("11", "자진타령", 0, 12,
            listOf("덩", "", "", "덩", "", "", "덩", "", "덕", "", "", ""), listOf(3, 3, 3, 3))
        val plan = assertNotNull(planJangdan(mapping, 90, 2))
        assertEquals(listOf(0.0, 3.0, 6.0, 8.0), plan.strikes.map { it.tick })
        assertEquals(listOf(JangdanSample.Deong, JangdanSample.Deong, JangdanSample.Deong, JangdanSample.Deok),
            plan.strikes.map { it.clip.sample })
        assertEquals(4000, (plan.msPerTick * mapping.cycleTicks).roundToInt())
    }

    @Test fun resumedCursorIgnoresSmallBackwardDecoderJitterUntilTheNextAttack() {
        val mapping = JangdanPlayback("3", "세마치", 0, 4, listOf("덩", "쿵"), listOf(1, 1))
        val cursor = JangdanCursor(planJangdan(mapping, 60, 4)!!, resumeAfterTick = 2.0)
        assertEquals(emptyList(), cursor.due(1.99))
        assertEquals(emptyList(), cursor.due(2.0))
        assertEquals(emptyList(), cursor.due(2.01))
        assertEquals(listOf(JangdanSample.Deong), cursor.due(4.0).map { it.clip.sample })
        assertEquals(listOf(JangdanSample.Kung), cursor.due(2.0).map { it.clip.sample })
    }

    @Test fun roundedPerformedTimeDoesNotStrikeTheSameBoundaryTwice() {
        val mapping = JangdanPlayback("12", "4박 기본 반주", 0, 4,
            listOf("덩", "덕", "궁", "덕"), listOf(1, 1, 1, 1))
        val cursor = JangdanCursor(planJangdan(mapping, 60, 1)!!)
        assertEquals(listOf(0.0), cursor.due(0.0).map { it.tick })
        assertEquals(listOf(1.0), cursor.due(0.9999995).map { it.tick })
        assertEquals(emptyList(), cursor.due(1.001))
        assertEquals(listOf(2.0), cursor.due(2.0).map { it.tick })
        assertEquals(listOf(0.0), cursor.due(0.0).map { it.tick })
    }

    @Test fun fiveActualIntegerTemposSelectDistinctRecordedRollsWithoutPitchChange() {
        for ((bpm, roll) in listOf(59 to JangdanSample.Gutgeori59, 79 to JangdanSample.Gutgeori79,
            99 to JangdanSample.Gutgeori99, 109 to JangdanSample.Gutgeori109, 119 to JangdanSample.Gutgeori119)) {
            val plan = assertNotNull(planJangdan(gutgeori, bpm, 4))
            val rolls = plan.strikes.filter { it.clip.sample.file.startsWith("gutgeori_roll") }
            assertEquals(listOf(8.0, 20.0), rolls.map { it.tick })
            assertEquals(listOf(roll, roll), rolls.map { it.clip.sample })
            assertEquals(listOf(null, null), rolls.map { it.clip.targetFrames })
            assertEquals(360000.0 / bpm, plan.cycleTicks * plan.msPerTick, 0.0001)
        }
    }

    @Test fun equivalentTickScalesAndBpmProduceTheSameAudibleTime() {
        val first = JangdanPlayback("3", "중모리", 0, 48, List(12) { "쿵" }, listOf(6, 6))
        val second = first.copy(cycleTicks = 24)
        val a = assertNotNull(planJangdan(first, 88, 4))
        val b = assertNotNull(planJangdan(second, 44, 4))
        assertEquals(a.strikes.map { it.tick * a.msPerTick }, b.strikes.map { it.tick * b.msPerTick })
    }

    @Test fun changedTempoFitsRealRollsToTheirSlotsWithoutMovingTheAttacks() {
        for (bpm in listOf(18, 95, 100, 120, 480)) {
            val plan = assertNotNull(planJangdan(gutgeori, bpm, 4))
            val rolls = plan.strikes.filter { it.clip.sample.file.startsWith("gutgeori_roll") }
            assertEquals(listOf(8.0, 20.0), rolls.map { it.tick })
            assertEquals(List(2) { (60.0 * 44_100 / bpm).roundToInt() }, rolls.map { it.clip.targetFrames })
            assertNull(plan.strikes.first().clip.targetFrames)
        }
    }

    @Test fun unpreparedRollOrUnknownPatternDoesNotPretendToPlay() {
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
