package com.ssafy.eolssu.platform.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class QaPcmInputTest {
    @Test
    fun readsSignedLittleEndianAndPadsEofWithSilenceAtRealTimeRate() {
        val file = fixture(shortArrayOf(-32768, -16384, 0, 16384, 32767))
        try {
            var now = 1_000_000_000L
            val waits = mutableListOf<Long>()
            val input = requireNotNull(QaPcmInput.open(file, 16000, { now }, { waits += it; now += it }))
            assertEquals(now, input.startSegment())
            val result = ShortArray(8)
            assertEquals(8, input.read(result))
            assertContentEquals(shortArrayOf(-32768, -16384, 0, 16384, 32767, 0, 0, 0), result)
            assertEquals(listOf(500_000L), waits)
        } finally { file.delete() }
    }

    @Test
    fun resamplesInputAndResumePreservesPositionWithoutIncludingPausedTime() {
        val file = fixture(shortArrayOf(0, 1000, 2000, 3000))
        try {
            var now = 0L
            val waits = mutableListOf<Long>()
            val input = requireNotNull(QaPcmInput.open(file, 32000, { now }, { waits += it; now += it }))
            input.startSegment()
            val first = ShortArray(4)
            input.read(first)
            assertContentEquals(shortArrayOf(0, 500, 1000, 1500), first)
            now += 5_000_000_000L
            assertEquals(now, input.startSegment())
            val second = ShortArray(4)
            input.read(second)
            assertContentEquals(shortArrayOf(2000, 2500, 3000, 1500), second)
            assertEquals(listOf(125_000L, 125_000L), waits)
        } finally { file.delete() }
    }

    @Test
    fun doesNotAddDelayWhenProcessingAlreadyConsumedTheSamplePeriod() {
        val file = fixture(shortArrayOf(1))
        try {
            var now = 0L
            var waits = 0
            val input = requireNotNull(QaPcmInput.open(file, 16000, { now }, { waits++ }))
            input.startSegment()
            now = 1_000_000_000L
            input.read(ShortArray(320))
            assertEquals(0, waits)
        } finally { file.delete() }
    }

    @Test
    fun absentFileUsesMicrophoneAndInvalidOrOversizedInputIsRejected() {
        val file = fixture(shortArrayOf(1))
        try {
            file.delete()
            assertNull(QaPcmInput.open(file, 44100))
            file.writeBytes(byteArrayOf(1))
            assertFailsWith<IllegalArgumentException> { QaPcmInput.open(file, 44100) }
            RandomAccessFile(file, "rw").use { it.setLength(1_920_002) }
            assertFailsWith<IllegalArgumentException> { QaPcmInput.open(file, 44100) }
        } finally { file.delete() }
    }

    private fun fixture(samples: ShortArray): File =
        File.createTempFile("qa-pcm-", ".pcm").also { file ->
            file.writeBytes(ByteArray(samples.size * 2) { index ->
                (samples[index / 2].toInt() shr (if (index % 2 == 0) 0 else 8)).toByte()
            })
        }
}
