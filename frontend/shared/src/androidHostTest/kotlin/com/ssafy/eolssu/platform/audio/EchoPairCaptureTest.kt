package com.ssafy.eolssu.platform.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EchoPairCaptureTest {
    private class DeferredWorker : Executor {
        private var task: Runnable? = null
        override fun execute(command: Runnable) { check(task == null); task = command }
        fun run() { checkNotNull(task).run() }
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("echo-pair-test-").toFile()
        try { block(directory) } finally {
            val tempRoot = File(checkNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile
            check(directory.canonicalPath.startsWith(tempRoot.path + File.separator))
            check(directory.name.startsWith("echo-pair-test-"))
            directory.deleteRecursively()
        }
    }

    @Test fun exactPairedSamplesHaveOneTimelineAndPreparationIsSeparate() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 7, workerExecutor = worker)
        val preparation = shortArrayOf(99, 1, 2, 3, 4, 99)
        val before = shortArrayOf(Short.MIN_VALUE, -1, 0, Short.MAX_VALUE, 77, 77)
        val after = shortArrayOf(-123, 0, 1, 999, 88, 88)
        assertTrue(capture.appendPreparation(preparation, 1, 4, 10))
        assertTrue(capture.appendPair(before, after, 4, 14))
        preparation.fill(555); before.fill(555); after.fill(555)
        val completion = capture.finish(summary = mapOf("state" to "Active", "quoted" to "a\"b\nc"))
        assertFalse(completion.isDone, "Finishing must not write on the capture thread")
        assertFalse(capture.directory.exists(), "Construction, append and finish must leave I/O to the worker")
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertTrue(result.complete)
        assertEquals(null, result.failure)
        assertEquals(4, result.preparationFrames)
        assertEquals(4, result.performanceFrames)
        assertContentEquals(shortArrayOf(1, 2, 3, 4), readWave(File(result.directory, "preparation.wav")))
        assertContentEquals(shortArrayOf(Short.MIN_VALUE, -1, 0, Short.MAX_VALUE), readWave(File(result.directory, "original.wav")))
        assertContentEquals(shortArrayOf(-123, 0, 1, 999), readWave(File(result.directory, "corrected.wav")))
        val metadata = Json.parseToJsonElement(File(result.directory, "metadata.json").readText()).jsonObject
        assertEquals("true", metadata.getValue("complete").jsonPrimitive.content)
        assertEquals("14", metadata.getValue("performanceFirstPhysicalFrame").jsonPrimitive.content)
        assertEquals("18", metadata.getValue("performanceEndPhysicalFrame").jsonPrimitive.content)
        for (name in listOf("preparation", "original", "corrected")) {
            val payload = File(result.directory, "$name.wav").readBytes().drop(44).toByteArray()
            val expected = MessageDigest.getInstance("SHA-256").digest(payload)
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            assertEquals(expected, metadata.getValue("${name}PcmSha256").jsonPrimitive.content)
        }
        assertEquals("a\"b\nc", metadata.getValue("summary").jsonObject.getValue("quoted").jsonPrimitive.content)
    }

    @Test fun defaultWorkerPublishesACompletePair() = withDirectory { directory ->
        val capture = EchoPairCapture(directory, 8)
        assertTrue(capture.appendPair(shortArrayOf(100, 200), shortArrayOf(80, 170), 2, 0))
        val result = capture.finish().get(5, TimeUnit.SECONDS)
        assertTrue(result.complete)
        assertContentEquals(shortArrayOf(100, 200), readWave(File(result.directory, "original.wav")))
    }

    @Test fun missingPhysicalSamplesInvalidateTheWholeComparison() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 9, workerExecutor = worker)
        assertTrue(capture.appendPair(shortArrayOf(1, 2), shortArrayOf(1, 2), 2, 20))
        assertFalse(capture.appendPair(shortArrayOf(3), shortArrayOf(3), 1, 23))
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("non_contiguous_performance", result.failure)
    }

    @Test fun queueOverflowIsNonblockingAndCannotPublishATruncatedPairAsComplete() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 10, queueCapacity = 1, workerExecutor = worker)
        assertTrue(capture.appendPair(shortArrayOf(1), shortArrayOf(2), 1, 0))
        assertFalse(capture.appendPair(shortArrayOf(3), shortArrayOf(4), 1, 1))
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("queue_overflow", result.failure)
        assertEquals(1, result.performanceFrames)
        assertContentEquals(shortArrayOf(1), readWave(File(result.directory, "original.wav")))
        assertContentEquals(shortArrayOf(2), readWave(File(result.directory, "corrected.wav")))
    }

    @Test fun frameLimitBoundsPreparationAndPerformanceTogether() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 11, maxFrames = 3, workerExecutor = worker)
        assertTrue(capture.appendPreparation(shortArrayOf(1, 2), 0, 2, 0))
        assertTrue(capture.appendPair(shortArrayOf(3), shortArrayOf(4), 1, 2))
        assertFalse(capture.appendPair(shortArrayOf(5), shortArrayOf(6), 1, 3))
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("frame_limit", result.failure)
        assertEquals(2, result.preparationFrames)
        assertEquals(1, result.performanceFrames)
    }

    @Test fun storageFailureDoesNotEscapeIntoRecordingCaller() = withDirectory { directory ->
        val parentIsAFile = File(directory, "not-a-directory").apply { writeText("keep") }
        val capture = EchoPairCapture(parentIsAFile, 12)
        capture.appendPair(shortArrayOf(1), shortArrayOf(1), 1, 0)
        val result = capture.finish().get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("io", result.failure)
        assertEquals("keep", parentIsAFile.readText())
    }

    @Test fun cancellationClosesFilesButNeverMarksAComparisonComplete() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 13, workerExecutor = worker)
        assertTrue(capture.appendPair(shortArrayOf(1), shortArrayOf(2), 1, 0))
        capture.close()
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("cancelled", result.failure)
        assertFalse(capture.appendPair(shortArrayOf(3), shortArrayOf(4), 1, 1))
    }

    @Test fun twoCapturesWithTheSameSessionIdNeverOverwriteEachOther() = withDirectory { directory ->
        val first = EchoPairCapture(directory, 14)
        first.appendPair(shortArrayOf(1), shortArrayOf(1), 1, 0)
        val firstResult = first.finish().get(5, TimeUnit.SECONDS)
        val second = EchoPairCapture(directory, 14)
        second.appendPair(shortArrayOf(2), shortArrayOf(2), 1, 0)
        val secondResult = second.finish().get(5, TimeUnit.SECONDS)
        assertTrue(firstResult.directory != secondResult.directory)
        assertContentEquals(shortArrayOf(1), readWave(File(firstResult.directory, "original.wav")))
        assertContentEquals(shortArrayOf(2), readWave(File(secondResult.directory, "original.wav")))
    }

    @Test fun insufficientSpaceAtStartRefusesOnlyDiagnosticFilesOnTheWorker() = withDirectory { directory ->
        val previous = File(directory, "previous.wav").apply { writeText("keep") }
        val worker = DeferredWorker()
        var probes = 0
        val capture = EchoPairCapture(directory, 20, workerExecutor = worker,
            minFreeBytes = 1024, availableBytes = { probes++; 1024 })
        assertTrue(capture.appendPair(shortArrayOf(1), shortArrayOf(2), 1, 0))
        val completion = capture.finish()
        assertEquals(0, probes)
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("storage_reserve", result.failure)
        assertFalse(capture.directory.exists())
        assertEquals("keep", previous.readText())
    }

    @Test fun spaceLostDuringCaptureStopsBeforeTheNextPairAndCannotPublishComplete() = withDirectory { directory ->
        val worker = DeferredWorker()
        lateinit var capture: EchoPairCapture
        capture = EchoPairCapture(directory, 21, workerExecutor = worker,
            minFreeBytes = 1024, availableBytes = {
                // Simulate other app data consuming disk after the first paired block.
                if (File(capture.directory, "original.wav").length() > 44) 1024 else Long.MAX_VALUE
            })
        assertTrue(capture.appendPair(shortArrayOf(1, 2), shortArrayOf(3, 4), 2, 0))
        assertTrue(capture.appendPair(shortArrayOf(5, 6), shortArrayOf(7, 8), 2, 2))
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("storage_reserve", result.failure)
        assertEquals(2, result.performanceFrames)
        assertContentEquals(shortArrayOf(1, 2), readWave(File(capture.directory, "original.wav")))
        assertContentEquals(shortArrayOf(3, 4), readWave(File(capture.directory, "corrected.wav")))
        assertFalse(File(capture.directory, "metadata.json").exists())
    }

    @Test fun accumulatedTraceBytesAreCountedWithoutDeletingPreviousSessions() = withDirectory { directory ->
        val previous = File(directory, "old-session").apply { mkdir() }
        val oldWave = File(previous, "original.wav").apply { writeBytes(ByteArray(1000) { 7 }) }
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 22, workerExecutor = worker,
            maxCacheBytes = 1100, minFreeBytes = 0, availableBytes = { Long.MAX_VALUE })
        capture.appendPair(shortArrayOf(1), shortArrayOf(2), 1, 0)
        val completion = capture.finish()
        worker.run()
        assertEquals("cache_budget", completion.get(5, TimeUnit.SECONDS).failure)
        assertFalse(capture.directory.exists())
        assertContentEquals(ByteArray(1000) { 7 }, oldWave.readBytes())
    }

    @Test fun pairedBlockCannotCrossCacheBudgetEvenWhenEachWaveWouldFit() = withDirectory { directory ->
        val worker = DeferredWorker()
        val capture = EchoPairCapture(directory, 23, workerExecutor = worker,
            maxCacheBytes = 140, minFreeBytes = 0, availableBytes = { Long.MAX_VALUE })
        capture.appendPair(shortArrayOf(1, 2), shortArrayOf(3, 4), 2, 0)
        capture.appendPair(shortArrayOf(5), shortArrayOf(6), 1, 2)
        val completion = capture.finish()
        worker.run()
        val result = completion.get(5, TimeUnit.SECONDS)
        assertFalse(result.complete)
        assertEquals("cache_budget", result.failure)
        assertEquals(2, result.performanceFrames)
        assertEquals(140, capture.directory.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    @Test fun simultaneousSessionsShareAByteBudgetWhileTheFirstWaitsForInput() = withDirectory { directory ->
        val executor = Executors.newFixedThreadPool(2)
        val firstWrotePair = CountDownLatch(1)
        val permitFirstToFinishWrite = CountDownLatch(1)
        lateinit var first: EchoPairCapture
        val firstWorker = DeferredWorker()
        first = EchoPairCapture(directory, 24, workerExecutor = firstWorker,
            maxCacheBytes = 300, minFreeBytes = 0, availableBytes = {
                if (File(first.directory, "original.wav").length() > 44) {
                    firstWrotePair.countDown()
                    check(permitFirstToFinishWrite.await(5, TimeUnit.SECONDS))
                }
                Long.MAX_VALUE
            })
        try {
            first.appendPair(ShortArray(5) { 1 }, ShortArray(5) { 2 }, 5, 0)
            first.appendPair(shortArrayOf(3), shortArrayOf(4), 1, 5)
            executor.execute { firstWorker.run() }
            assertTrue(firstWrotePair.await(5, TimeUnit.SECONDS))
            val second = EchoPairCapture(directory, 25, workerExecutor = executor,
                maxCacheBytes = 300, minFreeBytes = 0, availableBytes = { Long.MAX_VALUE })
            second.appendPair(ShortArray(5) { 5 }, ShortArray(5) { 6 }, 5, 0)
            val secondCompletion = second.finish()
            permitFirstToFinishWrite.countDown()
            val result = secondCompletion.get(5, TimeUnit.SECONDS)
            assertFalse(result.complete)
            assertEquals("cache_budget", result.failure)
            assertTrue(directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= 300)
        } finally {
            permitFirstToFinishWrite.countDown()
            first.close()
            first.finish().get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }

    private fun readWave(file: File): ShortArray {
        val bytes = file.readBytes()
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(bytes.size - 8, input.getInt(4))
        assertEquals(1, input.getShort(20).toInt())
        assertEquals(1, input.getShort(22).toInt())
        assertEquals(44_100, input.getInt(24))
        assertEquals(16, input.getShort(34).toInt())
        assertEquals(bytes.size - 44, input.getInt(40))
        return ShortArray((bytes.size - 44) / 2) { input.getShort(44 + it * 2) }
    }
}
