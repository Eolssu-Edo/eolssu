package com.ssafy.eolssu.platform.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EchoPairRecordingBoundaryTest {
    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("echo-boundary-test-").toFile()
        try { block(directory) } finally {
            val temporary = File(checkNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile
            check(directory.canonicalPath.startsWith(temporary.path + File.separator))
            check(directory.name.startsWith("echo-boundary-test-"))
            directory.deleteRecursively()
        }
    }

    @Test fun captureCrossingCountInBoundaryKeepsExactlyTheSamePerformanceSamplesInBothFiles() = withDirectory { directory ->
        val pair = EchoPairCapture(directory, 1)
        val pcm = shortArrayOf(10, 11, 12, 13, 14, 15, 16, 17)
        val prefix = preparationPrefixFrames(1_000, pcm.size, 1_003)
        assertTrue(pair.appendPreparation(pcm, 0, prefix, 1_000))
        pcm.copyInto(pcm, 0, prefix, pcm.size)
        val count = pcm.size - prefix
        assertTrue(processCapturedEcho(pcm, count, 1_003, pair) {
            repeat(count) { pcm[it] = (pcm[it] - 2).toShort() }
        })
        assertContentEquals(shortArrayOf(11, 12, 13, 14, 15), pcm.copyOf(count), "AAC/pitch receives the corrected samples")
        pcm.fill(99) // AudioRecord may reuse its backing array immediately.
        val result = pair.finish().get(5, TimeUnit.SECONDS)
        assertTrue(result.complete)
        assertEquals(3L, result.preparationFrames)
        assertEquals(5L, result.performanceFrames)
        assertContentEquals(shortArrayOf(10, 11, 12), samples(File(result.directory, "preparation.wav")))
        assertContentEquals(shortArrayOf(13, 14, 15, 16, 17), samples(File(result.directory, "original.wav")))
        assertContentEquals(shortArrayOf(11, 12, 13, 14, 15), samples(File(result.directory, "corrected.wav")))
    }

    @Test fun bypassedCorrectionProducesAnExactlyIdenticalPair() = withDirectory { directory ->
        val pair = EchoPairCapture(directory, 2)
        val pcm = shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE)
        assertTrue(processCapturedEcho(pcm, pcm.size, 0, pair) { })
        val result = pair.finish().get(5, TimeUnit.SECONDS)
        assertTrue(result.complete)
        assertContentEquals(samples(File(result.directory, "original.wav")), samples(File(result.directory, "corrected.wav")))
    }

    @Test fun normalRecordingWithoutOptInRunsCorrectionOnceAndDoesNotNeedADiagnosticWriter() {
        val pcm = shortArrayOf(1, 2, 3)
        var calls = 0
        assertTrue(processCapturedEcho(pcm, pcm.size, 0, null) { calls++; pcm[0] = 9 })
        assertEquals(1, calls)
        assertContentEquals(shortArrayOf(9, 2, 3), pcm)
    }

    private fun samples(file: File): ShortArray {
        val bytes = file.readBytes()
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(bytes.size - 44, buffer.getInt(40))
        return ShortArray((bytes.size - 44) / 2) { buffer.getShort(44 + it * 2) }
    }
}
