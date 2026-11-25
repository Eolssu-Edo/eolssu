package com.ssafy.eolssu.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PitchWindowsTest {
    @Test
    fun windowsFollowHopRegardlessOfChunkSize() {
        val whole = PitchWindows(size = 4, hop = 2)
        val chunked = PitchWindows(size = 4, hop = 2)
        val expected = mutableListOf<Pair<Long, List<Float>>>()
        val actual = mutableListOf<Pair<Long, List<Float>>>()
        whole.push(9, { it.toFloat() }) { end, samples -> expected += end to samples.toList() }
        var offset = 0
        for (size in listOf(1, 3, 5)) {
            val start = offset
            chunked.push(size, { (start + it).toFloat() }) { end, samples -> actual += end to samples.toList() }
            offset += size
        }
        assertEquals(listOf(4L to listOf(0f, 1f, 2f, 3f), 6L to listOf(2f, 3f, 4f, 5f), 8L to listOf(4f, 5f, 6f, 7f)), expected)
        assertEquals(expected, actual)
        assertEquals(9L, chunked.fileSamples)
    }

    @Test
    fun resetDropsPartialWindowButKeepsFilePosition() {
        val windows = PitchWindows(size = 4, hop = 2)
        val ends = mutableListOf<Long>()
        windows.push(3, { 1f }) { end, _ -> ends += end }
        windows.reset()
        windows.push(4, { 2f }) { end, samples ->
            ends += end
            assertContentEquals(floatArrayOf(2f, 2f, 2f, 2f), samples)
        }
        assertEquals(listOf(7L), ends)
        assertEquals(7L, windows.fileSamples)
    }
}
