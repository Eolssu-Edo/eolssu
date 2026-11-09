package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.runtime.mutableStateOf
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_tour_holes
import eolssu.shared.generated.resources.learn_tour_mouth
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** 다음은 마지막 곳에서 멈추고, 이전은 첫 곳 아래로 가지 않는다. */
    @Test
    fun tourStepsStayInRange() {
        val step = mutableStateOf<Int?>(null)
        val tour = LearnTour(step, mutableStateOf(false))
        tour.prev()
        assertEquals(0, tour.step)
        tour.next(last = 2); tour.next(last = 2); tour.next(last = 2)
        assertEquals(2, tour.step)
        tour.prev(); tour.prev(); tour.prev()
        assertEquals(0, tour.step)
        tour.finish()
        assertTrue(tour.celebrating)
    }
}
