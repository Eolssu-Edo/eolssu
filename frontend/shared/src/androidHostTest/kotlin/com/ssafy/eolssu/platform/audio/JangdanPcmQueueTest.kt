package com.ssafy.eolssu.platform.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JangdanPcmQueueTest {
    @Test fun anOutputGapDiscardsPastAttacksWithoutShiftingFutureOnes() {
        val queue = JangdanPcmQueue()
        queue.add(shortArrayOf(1000, 1000), 1f, 4)
        queue.add(shortArrayOf(2000, 2000), 1f, 12)
        queue.read(3)
        queue.discardBefore(10)
        assertEquals(10L, queue.nextFrame)
        assertContentEquals(shortArrayOf(0, 0, 2000, 2000), queue.read(4))
        queue.discardBefore(5)
        assertEquals(14L, queue.nextFrame, "A hardware gap cannot rewind the score")
    }

    @Test fun scheduledSamplesKeepTheirPositionsAcrossUnevenWrites() {
        val queue = JangdanPcmQueue()
        queue.add(shortArrayOf(1000, 2000, 3000), 1f, 2)
        queue.add(shortArrayOf(4000, 5000), 1f, 7)
        val output = queue.read(4) + queue.read(1) + queue.read(5)
        assertContentEquals(shortArrayOf(0, 0, 1000, 2000, 3000, 0, 0, 4000, 5000, 0), output)
        assertEquals(10L, queue.nextFrame)
    }

    @Test fun overlappingTailsMixAndSaturateInsteadOfWrapping() {
        val queue = JangdanPcmQueue()
        queue.add(shortArrayOf(30000, -30000, 1000), 1f, 0)
        queue.add(shortArrayOf(20000, -20000, 2000), 0.5f, 0)
        assertContentEquals(shortArrayOf(32767, -32768, 2000), queue.read(3))
    }

    @Test fun stoppingDiscardsQueuedAttacksAndAllowsAStartFromZero() {
        val queue = JangdanPcmQueue()
        queue.add(shortArrayOf(900, 800, 700), 1f, 0)
        queue.add(shortArrayOf(600), 1f, 4)
        queue.read(1)
        queue.clear()
        assertEquals(0L, queue.nextFrame)
        assertContentEquals(ShortArray(5), queue.read(5))
        queue.add(shortArrayOf(500), 1f, 2) // Already-written positions start at the next writable frame.
        assertContentEquals(shortArrayOf(500, 0), queue.read(2))
    }
}
