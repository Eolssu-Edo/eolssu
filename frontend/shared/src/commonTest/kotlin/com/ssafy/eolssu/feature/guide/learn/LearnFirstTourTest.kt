package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_tour_holes
import eolssu.shared.generated.resources.learn_tour_mouth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnFirstTourTest {

    /** 짚는 곳은 모두 악기 그림 안에 있고, 손가락 구멍은 운지 그림의 구멍을 빠짐없이 한 번씩 짚는다. */
    @Test
    fun tourSpotsStayInsideImageAndCoverEveryHole() {
        GuideInstrument.entries.forEach { instrument ->
            val spots = instrument.tourSpots
            assertEquals(Res.string.learn_tour_mouth, spots.first().title, "${instrument.id} 첫 곳은 취구")
            spots.flatMap { it.points }.forEach { p ->
                assertTrue(p.x in 0..instrument.imageWidth && p.y in 0..instrument.imageHeight, "${instrument.id} $p 는 그림 밖")
            }
            val holePoints = spots.filter { it.title == Res.string.learn_tour_holes || it.points.all { p -> p in instrument.holes } }
                .flatMap { it.points }
            assertEquals(instrument.holes, holePoints, "${instrument.id} 구멍 순서")
        }
    }

    /** 할 일 박스(S15P31E102-688): 처음부터 1번 박스가 열려 있고 아무것도 마치지 않았다. */
    @Test
    fun firstBoxIsOpenFromTheStart() {
        val tour = LearnTour()
        assertEquals(0, tour.open)
        assertEquals(0, tour.doneCount)
        assertFalse(tour.allDone(count = 3))
    }

    /** '해 봤어요'는 그 박스를 마침으로 남기고 다음 박스를 연다. 마지막 박스면 모두 접고 다 마쳤다고 알린다. */
    @Test
    fun completingABoxOpensTheNextOne() {
        val tour = LearnTour()
        assertFalse(tour.complete(0, count = 3))
        assertTrue(tour.isDone(0))
        assertEquals(1, tour.open)
        assertFalse(tour.complete(1, count = 3))
        assertTrue(tour.complete(2, count = 3))
        assertNull(tour.open)
        assertTrue(tour.allDone(count = 3))
    }

    /** 아직 열리지 않은 박스는 건너뛰어 마치거나 펼칠 수 없다. */
    @Test
    fun lockedBoxesCannotBeSkipped() {
        val tour = LearnTour()
        assertFalse(tour.complete(2, count = 3))
        assertEquals(0, tour.doneCount)
        tour.toggle(2)
        assertEquals(0, tour.open)
    }

    /** 마친 박스는 다시 펼쳐 볼 수 있고, 거기서 '해 봤어요'를 누르면 아직 안 한 첫 박스로 간다. 펼친 박스를 누르면 접힌다. */
    @Test
    fun doneBoxesCanBeReopened() {
        val tour = LearnTour()
        tour.complete(0, count = 3)
        tour.complete(1, count = 3)
        tour.toggle(0)
        assertEquals(0, tour.open)
        assertFalse(tour.complete(0, count = 3))
        assertEquals(2, tour.open)
        tour.toggle(2)
        assertNull(tour.open)
        assertEquals(2, tour.doneCount)
    }

    @Test
    fun finishShowsCelebration() {
        val tour = LearnTour()
        tour.finish()
        assertTrue(tour.celebrating)
        tour.dismiss()
        assertFalse(tour.celebrating)
    }

    /** 휴대폰처럼 그림이 작으면 최소 크기 때문에 붙은 구멍의 동그라미가 겹치므로, 구멍 사이 절반 안으로 줄인다. */
    @Test
    fun spotRingsNeverOverlapNeighbourHoles() {
        val minRadius = 36f
        val gap = 5f
        GuideInstrument.entries.forEach { instrument ->
            instrument.tourSpots.filter { !it.grouped && it.points.size > 1 }.forEach { spot ->
                listOf(0.3f, 0.46f, 1f).forEach { scale ->
                    val r = spotRingRadius(spot.points, instrument.holeRadius, scale, minRadius, gap)
                    spot.points.zipWithNext().forEach { (a, b) ->
                        val distance = kotlin.math.abs(a.x - b.x) * scale
                        assertTrue(2 * r + 2 * gap <= distance + 0.01f, "${instrument.id} ${spot.titleArg} scale=$scale 겹침")
                    }
                    assertTrue(r > instrument.holeRadius * scale, "${instrument.id} ${spot.titleArg} scale=$scale 구멍보다 작음")
                }
            }
        }
        // 혼자 짚는 곳은 최소 크기를 지킨다.
        assertEquals(minRadius, spotRingRadius(listOf(GuideInstrument.Danso.holes[0]), 30, 0.3f, minRadius, gap))
    }
}
