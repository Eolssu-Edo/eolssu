package com.ssafy.eolssu.core.ui.component

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrushEnsoTest {
    private val brush = EnsoBrush()
    private val size = 600.0
    private val center = size / 2

    @Test
    fun randomMatchesWebPrototype() {
        // 웹 시안(mulberry32, 시드 11 × 7919)의 첫 세 값.
        val r = Mulberry32(11 * 7919)
        assertEquals(0.3689405038021505, r.next(), 1e-15)
        assertEquals(0.166380793787539, r.next(), 1e-15)
        assertEquals(0.4096525334753096, r.next(), 1e-15)
    }

    @Test
    fun inkMatchesWebPrototype() {
        assertEquals(0.8119899156239168, brush.ink(brush.bristles[0], 0.5), 1e-12)
    }

    @Test
    fun strokeStartsAtSevenAndEndsAtSix() {
        val start = brush.point(0.0, size)
        val startRadius = hypot(start.x - center, start.y - center)
        assertEquals(-0.5, (start.x - center) / startRadius, 0.01)
        assertEquals(0.866, (start.y - center) / startRadius, 0.01)

        val end = brush.point(1.0, size)
        assertEquals(0.0, (end.x - center) / hypot(end.x - center, end.y - center), 0.01)
        assertTrue(end.y > center)
        assertEquals(330.0, brush.sweepDeg)
    }

    @Test
    fun headIsRoundAndTailDriesOut() {
        val edge = brush.bristles.first()
        val middle = brush.bristles.minBy { abs(it.u) }
        assertTrue(brush.headStart(edge, size) > brush.headStart(middle, size) * 5)

        fun inked(s: Double) = brush.bristles.count { brush.ink(it, s) > 0.2 }
        assertEquals(brush.bristles.size, inked(0.5))
        assertTrue(inked(0.99) < brush.bristles.size / 4)
        assertTrue(brush.point(0.99, size).halfWidth < brush.point(0.5, size).halfWidth / 2)
    }

    @Test
    fun traceDrawsEveryBristleOnceItTouchesPaper() {
        val trail = EnsoBrush.Trail(brush.bristles.size)
        var finished = 0
        val sink = object : EnsoBrush.Sink {
            override fun line(x0: Float, y0: Float, x1: Float, y1: Float) = Unit
            override fun finish(alpha: Float, width: Float) {
                assertTrue(alpha in 0.22f..0.9f)
                assertTrue(width >= 0.7f)
                finished++
            }
        }
        brush.trace(0.3, size, trail, sink)
        assertEquals(brush.bristles.size, finished)
    }

    @Test
    fun leadingEdgeIsRaggedBrushNotFlatCut() {
        val count = brush.bristles.size
        val reach = List(count) { brush.reach(it, 0.5, size) }
        // 그리는 도중에는 털마다 뒤처지는 거리가 달라 앞끝이 들쭉날쭉하고, 앞지르는 털은 없다.
        assertTrue(reach.all { it <= 0.5 })
        assertTrue(reach.max() - reach.min() > 0.005)
        // 거친 앞끝은 가장자리에서 가운데로 갈수록 고르게 앞서는 반원이 아니다.
        val byEdge = brush.bristles.indices.sortedBy { abs(brush.bristles[it].u) }.map { reach[it] }
        assertTrue(byEdge.zipWithNext().any { (inner, outer) -> outer > inner })

        val trail = EnsoBrush.Trail(count)
        val noop = object : EnsoBrush.Sink {
            override fun line(x0: Float, y0: Float, x1: Float, y1: Float) = Unit
            override fun finish(alpha: Float, width: Float) = Unit
        }
        brush.trace(0.5, size, trail, noop)
        assertTrue(trail.next.toSet().size > count / 2)

        // 다 그리면 모든 털이 끝까지 닿는다.
        brush.trace(1.0, size, trail, noop)
        assertEquals(1, trail.next.toSet().size)
        assertEquals(1.0, brush.reach(0, 1.0, size))
    }

    @Test
    fun uploadProgressChasesTargetWithoutPassingIt() {
        // 한 프레임(1/60초)에 최대 0.6/초만큼만 따라가고, 목표를 넘지 않는다.
        val dt = 1f / 60
        val first = chaseProgress(0f, 0.5f, dt)
        assertTrue(first > 0f)
        assertTrue(first <= 0.6f * dt + 1e-4f + 1e-6f)

        var shown = 0f
        repeat(600) {
            shown = chaseProgress(shown, 0.5f, dt)
            assertTrue(shown <= 0.5f)
        }
        assertEquals(0.5f, shown)
    }

    @Test
    fun uploadProgressSnapsToFullWhenSent() {
        assertEquals(1f, chaseProgress(0.996f, 1f, 0f))
        // 아직 다 보내지 않았으면 끝 무렵이어도 1로 맞추지 않는다.
        assertTrue(chaseProgress(0.996f, 0.999f, 0f) < 1f)
    }

    @Test
    fun uploadProgressResetsWhenTargetDrops() {
        // 재전송은 0부터 다시 보고하므로 보이는 값도 바로 내려간다.
        assertEquals(0f, chaseProgress(0.7f, 0f, 1f / 60))
        assertEquals(0.3f, chaseProgress(0.7f, 0.3f, 1f / 60))
    }

    private fun hypot(x: Double, y: Double) = kotlin.math.sqrt(x * x + y * y)
}
