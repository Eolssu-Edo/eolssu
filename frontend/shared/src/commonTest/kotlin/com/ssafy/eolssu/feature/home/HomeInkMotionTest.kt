package com.ssafy.eolssu.feature.home

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.ssafy.eolssu.feature.home.component.homeInkRevealSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeInkMotionTest {
    @Test fun carriesInkVelocityFromDragIntoAutomaticSnap() {
        val spec = homeInkRevealSpec(reduceMotion = false)
        val drag = TargetBasedAnimation(spec, Float.VectorConverter, 0f, .3f)
        val handoffTime = 160_000_000L
        val position = drag.getValueFromNanos(handoffTime)
        val velocity = drag.getVelocityVectorFromNanos(handoffTime).value
        assertTrue(velocity > .1f, "회귀 검사는 이미 움직이는 붓에서 시작한다")

        val snap = TargetBasedAnimation(
            spec, Float.VectorConverter, position, 1f, AnimationVector1D(velocity),
        )
        assertEquals(position, snap.getValueFromNanos(0L), .0001f)
        val resumedVelocity = snap.getVelocityVectorFromNanos(1_000_000L).value
        assertTrue(resumedVelocity >= velocity * .9f,
            "새 목표를 받은 직후에도 붓의 속도를 유지한다: $velocity -> $resumedVelocity")
    }

    @Test fun paintsAndRetractsWithoutBouncingAtTheEnds() {
        val spec = homeInkRevealSpec(reduceMotion = false)
        for ((start, end) in listOf(0f to 1f, 1f to 0f)) {
            val animation = TargetBasedAnimation(spec, Float.VectorConverter, start, end)
            var previous = start
            for (millis in 0L..1200L step 8L) {
                val value = animation.getValueFromNanos(millis * 1_000_000L)
                assertTrue(value in 0f..1f, "먹이 양 끝을 넘어 튕기지 않는다")
                assertTrue(if (end > start) value >= previous else value <= previous)
                previous = value
            }
            assertEquals(end, previous, .001f)
        }
    }

    @Test fun reducedMotionStillChangesImmediately() {
        val animation = TargetBasedAnimation(homeInkRevealSpec(true), Float.VectorConverter, 0f, 1f)
        assertEquals(1f, animation.getValueFromNanos(0L))
    }
}
