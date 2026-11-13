package com.ssafy.eolssu.feature.home

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeStageFlingTest {
    @Test fun flingTargetFollowsWhereTheFlingWouldLand() {
        assertEquals(0f, homeFlingTarget(10f, 30f, wasOpen = false))
        assertEquals(HomeScrollRange, homeFlingTarget(38f, 205f, wasOpen = false))
        assertEquals(0f, homeFlingTarget(HomeScrollRange, -80f, wasOpen = true))
        assertEquals(HomeScrollRange, homeFlingTarget(HomeScrollRange, 360f, wasOpen = true))
        // 무대 안에서 튕겨 관성이 카드 구간까지 가도 펼친 화면에서 멈춘다.
        assertEquals(HomeScrollRange, homeFlingTarget(40f, 900f, wasOpen = false))
        assertEquals(HomeScrollRange, homeFlingTarget(0f, HomeScrollRange, wasOpen = false))
        assertNull(homeFlingTarget(HomeScrollRange, 700f, wasOpen = true))
        assertNull(homeFlingTarget(600f, 500f, wasOpen = true))
    }

    @Test fun stageEasingStartsAtFingerSlopeAndNeverOvershoots() {
        for (slope in listOf(0f, 1f, 2f, 3f)) {
            val easing = homeStageEasing(slope)
            assertEquals(0f, easing.transform(0f))
            assertEquals(1f, easing.transform(1f))
            assertEquals(slope, easing.transform(.001f) / .001f, .01f)
            val values = (0..100).map { easing.transform(it / 100f) }
            assertTrue(values.zipWithNext().all { (a, b) -> b >= a }, "기울기 $slope: 한 방향으로만 간다")
        }
    }

    @Test fun stageMotionIsNeverShorterThanTheMinimum() {
        assertEquals(.55f, homeStageSeconds(336f, 0f))
        assertEquals(.45f, homeStageSeconds(360f, 6000f))
        assertEquals(3f * 362f / 2200f, homeStageSeconds(362f, 2200f), .0001f)
    }

    @Test fun smallSlowSlideOpensInOneContinuousMotion() = runTest {
        val run = fling(startDp = 64f, velocityDp = 0f)
        assertEquals(px(HomeScrollRange), run.position)
        assertTrue(run.steps.all { it >= 0f }, "되돌아가지 않고 한 방향으로 간다")
        assertTrue(run.steps.first() < px(10f), "느리게 밀던 손가락 속도에서 시작해 첫 프레임이 튀지 않는다: ${run.steps.first() / D}dp")
        assertTrue(run.steps.max() < px(40f), "한 프레임에 40dp 넘게 건너뛰지 않는다: ${run.steps.max() / D}dp")
        assertEquals(0, run.freeVelocity.size)
    }

    @Test fun smallFlickKeepsFingerSpeedAndEndsOpenWithoutStoppingMidway() = runTest {
        val velocity = 1500f
        assertTrue(landingDp(38f, velocity) < HomeScrollRange, "관성만으로는 무대 중간에서 멈추는 속도")
        val run = fling(startDp = 38f, velocityDp = velocity)
        assertEquals(px(HomeScrollRange), run.position)
        // 첫 프레임은 손가락 속도(16ms × 1500dp/s ≈ 24dp)를 이어받는다. 정지 상태에서 다시 출발하지 않는다.
        assertTrue(run.steps.first() > px(15f), "첫 프레임 ${run.steps.first() / D}dp")
        assertTrue(run.maxPosition <= px(HomeScrollRange), "목표를 지나쳐 카드 구간으로 넘어가지 않는다")
        assertEquals(0, run.freeVelocity.size)
    }

    @Test fun shortSlideBelowTheThresholdReturnsToTheFirstScreen() = runTest {
        val run = fling(startDp = 30f, velocityDp = 0f)
        assertEquals(0f, run.position)
    }

    @Test fun flickBackFromTheOpenStageClosesIt() = runTest {
        val run = fling(startDp = HomeScrollRange, velocityDp = -1500f, wasOpen = true)
        assertEquals(0f, run.position)
        assertTrue(run.steps.all { it <= 0f })
    }

    @Test fun shortStrongFlickFromTheFirstScreenShowsTheWholeExpandAndStopsOpen() = runTest {
        val velocity = 6000f
        assertTrue(landingDp(40f, velocity) >= HomeScrollRange, "관성만으로는 무대를 지나 카드 구간까지 가는 속도")
        val run = fling(startDp = 40f, velocityDp = velocity)
        assertEquals(px(HomeScrollRange), run.position)
        assertTrue(run.maxPosition <= px(HomeScrollRange), "카드 구간으로 넘어가지 않는다")
        assertEquals(0, run.freeVelocity.size)
        assertTrue(run.steps.all { it >= 0f })
        // 0.45초(약 28프레임) 넘게 펼쳐 과정이 보인다. 이전에는 기본 관성이 무대를 몇 프레임 만에 지나갔다.
        assertTrue(run.steps.size >= 26, "펼치는 프레임 수 ${run.steps.size}")
        assertTrue(run.steps.max() < px(45f), "한 프레임에 45dp 넘게 건너뛰지 않는다: ${run.steps.max() / D}dp")
    }

    @Test fun flickFromTheOpenStageIntoTheCardsKeepsTheFreeFling() = runTest {
        val velocity = 3000f
        val run = fling(startDp = HomeScrollRange, velocityDp = velocity, wasOpen = true)
        assertEquals(listOf(px(velocity)), run.freeVelocity)
    }

    @Test fun reducedMotionMovesToTheTargetAtOnce() = runTest {
        val run = fling(startDp = 64f, velocityDp = 0f, reduceMotion = true)
        assertEquals(listOf(px(HomeScrollRange - 64f)), run.steps)
    }

    private class Run(val position: Float, val maxPosition: Float, val steps: List<Float>, val freeVelocity: List<Float>)

    private suspend fun fling(startDp: Float, velocityDp: Float, wasOpen: Boolean = false, reduceMotion: Boolean = false): Run {
        val scroll = FakeScroll(px(startDp), max = px(HomeScrollRange + 300f))
        val free = RecordingFling()
        val behavior = HomeStageFlingBehavior({ scroll.position }, D, { wasOpen }, reduceMotion, Decay, free)
        withContext(FrameClock()) { with(behavior) { scroll.performFling(px(velocityDp)) } }
        return Run(scroll.position, scroll.maxPosition, scroll.steps, free.velocities)
    }

    private fun landingDp(startDp: Float, velocityDp: Float) = Decay.calculateTargetValue(px(startDp), px(velocityDp)) / D

    private class FakeScroll(var position: Float, private val max: Float) : ScrollScope {
        val steps = mutableListOf<Float>()
        var maxPosition = position
        override fun scrollBy(pixels: Float): Float {
            val next = (position + pixels).coerceIn(0f, max)
            val used = next - position
            position = next
            maxPosition = maxOf(maxPosition, position)
            if (abs(used) > 0f) steps += used
            return used
        }
    }

    private class RecordingFling : FlingBehavior {
        val velocities = mutableListOf<Float>()
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            velocities += initialVelocity
            return 0f
        }
    }

    /** 60Hz 화면처럼 16ms마다 프레임을 넘긴다. */
    private class FrameClock : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private companion object {
        /** 에뮬레이터(Medium Phone, 420dpi)의 밀도. */
        const val D = 2.625f
        /** 플랫폼 스플라인 감쇠는 호스트 테스트에서 쓸 수 없어 순수 지수 감쇠로 대신한다. */
        val Decay = exponentialDecay<Float>()
        fun px(dp: Float) = dp * D
    }
}
