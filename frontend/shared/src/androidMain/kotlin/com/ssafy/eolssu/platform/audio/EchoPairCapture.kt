package com.ssafy.eolssu.platform.audio

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Opt-in local diagnostic output. Callers gate construction on a debug flag.
 * Append/finish never perform file I/O or wait for the writer. Only a published
 * metadata.json with complete=true represents a usable before/after comparison.
 * An absent/partial manifest, cancellation or dropped block is not a valid pair.
 * The IO worker limits the entire trace directory to 128 MiB and leaves 64 MiB
 * free by default. Old traces are never removed; a full cache disables capture.
 */
internal class EchoPairCapture(
    private val parentDirectory: File,
    private val sessionId: Long,
    private val sampleRate: Int = 44_100,
    private val maxFrames: Long = 44_100 * 180L,
    private val queueCapacity: Int = 64,
    workerExecutor: Executor = Executor { task -> Thread(task, "echo-pair-$sessionId").apply { isDaemon = true }.start() },
    private val maxCacheBytes: Long = 128L * 1024 * 1024,
    private val minFreeBytes: Long = 64L * 1024 * 1024,
    private val availableBytes: (File) -> Long = { it.usableSpace },
) : Closeable {
    data class Result(
        val directory: File,
        val complete: Boolean,
        val failure: String?,
        val preparationFrames: Long,
        val performanceFrames: Long,
    )

    private sealed interface Block {
        data class Preparation(val samples: ShortArray) : Block
        data class Pair(val original: ShortArray, val corrected: ShortArray) : Block
    }

    val directory = File(parentDirectory, "session-$sessionId-${UUID.randomUUID()}")
    private val lock = Any()
    private val queue = ArrayBlockingQueue<Block>(queueCapacity)
    private val wake = Semaphore(0)
    private val completion = CompletableFuture<Result>()
    @Volatile private var ending = false
    private var failure: String? = null
    private var cancelled = false
    private var summary = emptyMap<String, String>()
    private var preparationFrames = 0L
    private var performanceFrames = 0L
    private var preparationFirstFrame: Long? = null
    private var preparationEndFrame: Long? = null
    private var performanceFirstFrame: Long? = null
    private var performanceEndFrame: Long? = null

    init {
        require(sampleRate == 44_100)
        require(maxFrames in 1..44_100 * 180L)
        require(queueCapacity in 1..64)
        require(maxCacheBytes > 0 && minFreeBytes >= 0)
        try {
            workerExecutor.execute { drain() }
        } catch (_: Exception) {
            synchronized(lock) { failure = "worker_start"; ending = true }
            completion.complete(Result(directory, false, "worker_start", 0, 0))
        }
    }

    /** physicalFirstFrame is the first included sample, not the start of pcm's backing array. */
    fun appendPreparation(pcm: ShortArray, offset: Int, count: Int, physicalFirstFrame: Long): Boolean = synchronized(lock) {
        if (ending) return false
        if (offset < 0 || offset > pcm.size || count < 0 || count > pcm.size - offset ||
            !validBlock(count, physicalFirstFrame)) return reject("invalid_input")
        if (count == 0) return true
        if (performanceFirstFrame != null) return reject("preparation_after_performance")
        if (preparationEndFrame != null && physicalFirstFrame != preparationEndFrame) return reject("non_contiguous_preparation")
        if (!hasCapacity(count)) return false
        val owned = pcm.copyOfRange(offset, offset + count)
        if (!queue.offer(Block.Preparation(owned))) return reject("queue_overflow")
        if (preparationFirstFrame == null) preparationFirstFrame = physicalFirstFrame
        preparationFrames += count
        preparationEndFrame = physicalFirstFrame + count
        wake.release()
        true
    }

    /** Both arrays must describe the same samples immediately before and after correction. */
    fun appendPair(original: ShortArray, corrected: ShortArray, count: Int, physicalFirstFrame: Long): Boolean = synchronized(lock) {
        if (ending) return false
        if (count < 0 || count > original.size || count > corrected.size ||
            !validBlock(count, physicalFirstFrame)) return reject("invalid_input")
        if (count == 0) return true
        val expected = performanceEndFrame ?: preparationEndFrame
        if (expected != null && physicalFirstFrame != expected) return reject("non_contiguous_performance")
        if (!hasCapacity(count)) return false
        if (!queue.offer(Block.Pair(original.copyOf(count), corrected.copyOf(count)))) return reject("queue_overflow")
        if (performanceFirstFrame == null) performanceFirstFrame = physicalFirstFrame
        performanceFrames += count
        performanceEndFrame = physicalFirstFrame + count
        wake.release()
        true
    }

    /** Nonblocking and idempotent. Observe the future off the AudioRecord thread if needed. */
    fun finish(cancelled: Boolean = false, summary: Map<String, String> = emptyMap()): CompletableFuture<Result> = synchronized(lock) {
        if (!ending) {
            this.cancelled = cancelled
            this.summary = summary.toMap()
            if (cancelled && failure == null) failure = "cancelled"
            ending = true
            wake.release()
        }
        completion
    }

    override fun close() { finish(cancelled = true) }

    private fun validBlock(count: Int, first: Long) =
        count <= sampleRate && first >= 0 && first <= Long.MAX_VALUE - count

    /** Called only under lock. Limits include preparation and count each paired frame once. */
    private fun hasCapacity(count: Int): Boolean {
        if (preparationFrames + performanceFrames + count > maxFrames) return reject("frame_limit")
        if (queue.size >= queueCapacity) return reject("queue_overflow")
        return true
    }

    private fun reject(reason: String): Boolean {
        if (failure == null) failure = reason
        ending = true
        wake.release()
        return false
    }

    private fun drain() {
        var preparation: WaveWriter? = null
        var original: WaveWriter? = null
        var corrected: WaveWriter? = null
        var budget: CacheBudget? = null
        try {
            if (!parentDirectory.isDirectory && !parentDirectory.mkdirs() && !parentDirectory.isDirectory) {
                throw IOException("Diagnostic parent directory unavailable")
            }
            val storage = CacheBudgets.acquire(parentDirectory, maxCacheBytes)
            budget = storage
            storage.write(3 * 44L, maxCacheBytes, minFreeBytes, availableBytes) {
                // Never reuse an existing directory or truncate another recording.
                if (!directory.mkdir()) throw IOException("Diagnostic session directory unavailable")
                preparation = WaveWriter(File(directory, "preparation.wav"), sampleRate)
                original = WaveWriter(File(directory, "original.wav"), sampleRate)
                corrected = WaveWriter(File(directory, "corrected.wav"), sampleRate)
            }
            while (true) {
                wake.acquire()
                while (true) {
                    when (val block = queue.poll() ?: break) {
                        is Block.Preparation -> storage.write(block.samples.size * 2L,
                            maxCacheBytes, minFreeBytes, availableBytes) {
                            checkNotNull(preparation).write(block.samples)
                        }
                        is Block.Pair -> {
                            // Reserve the whole pair before either stream is changed.
                            storage.write(block.original.size * 4L, maxCacheBytes, minFreeBytes, availableBytes) {
                                checkNotNull(original).write(block.original)
                                checkNotNull(corrected).write(block.corrected)
                            }
                        }
                    }
                }
                if (ending && queue.isEmpty()) break
            }
        } catch (error: Exception) {
            synchronized(lock) {
                if (failure == null) failure = when (error) {
                    is StorageLimit -> error.reason
                    is IOException -> "io"
                    is InterruptedException -> "worker_interrupted"
                    else -> "worker"
                }
                ending = true
            }
        } finally {
            queue.clear()
            for (writer in listOfNotNull(preparation, original, corrected)) {
                try { writer.close() } catch (_: Exception) {
                    synchronized(lock) { if (failure == null) failure = "io"; ending = true }
                }
            }
            val preparationWritten = preparation?.frames ?: 0
            val originalWritten = original?.frames ?: 0
            val correctedWritten = corrected?.frames ?: 0
            var result: Result
            val manifest = synchronized(lock) {
                ending = true
                if (failure == null && (preparationWritten != preparationFrames ||
                        originalWritten != performanceFrames || correctedWritten != performanceFrames)) failure = "incomplete_write"
                if (failure == null && performanceFrames == 0L) failure = "no_performance"
                result = Result(directory, failure == null && !cancelled, failure,
                    preparationWritten, minOf(originalWritten, correctedWritten))
                buildJsonObject {
                    put("schemaVersion", 1)
                    put("sessionId", sessionId)
                    put("sampleRate", sampleRate)
                    put("channels", 1)
                    put("encoding", "PCM_S16LE")
                    put("complete", result.complete)
                    put("cancelled", cancelled)
                    put("failure", failure?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("preparationFrames", preparationWritten)
                    put("originalFrames", originalWritten)
                    put("correctedFrames", correctedWritten)
                    put("preparationPcmSha256", preparation?.pcmSha256?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("originalPcmSha256", original?.pcmSha256?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("correctedPcmSha256", corrected?.pcmSha256?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("offeredPreparationFrames", preparationFrames)
                    put("offeredPerformanceFrames", performanceFrames)
                    put("preparationFirstPhysicalFrame", preparationFirstFrame?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("preparationEndPhysicalFrame", preparationEndFrame?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("performanceFirstPhysicalFrame", performanceFirstFrame?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("performanceEndPhysicalFrame", performanceEndFrame?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("summary", buildJsonObject { summary.forEach { (key, value) -> put(key, value) } })
                }.toString()
            }
            try {
                if (directory.isDirectory && budget != null) {
                    // Publish only once all three WAV headers and lengths have been finalized.
                    val bytes = manifest.toByteArray(Charsets.UTF_8)
                    if (bytes.size > 64 * 1024) throw StorageLimit("metadata_limit")
                    budget.write(bytes.size.toLong(), maxCacheBytes, minFreeBytes, availableBytes) {
                        val temporary = File(directory, "metadata.json.tmp")
                        temporary.writeBytes(bytes)
                        if (!temporary.renameTo(File(directory, "metadata.json"))) throw IOException("Cannot publish diagnostic manifest")
                    }
                }
            } catch (error: Exception) {
                result = result.copy(complete = false,
                    failure = result.failure ?: (error as? StorageLimit)?.reason ?: "io_metadata")
            } finally {
                budget?.let { CacheBudgets.release(it) }
            }
            completion.complete(result)
        }
    }

    private class WaveWriter(private val file: File, private val rate: Int) : Closeable {
        // No pending user-space buffer may escape the shared check-and-write scope.
        // An audio block is at most one second: 88,200 bytes per stream.
        private val output = FileOutputStream(file)
        private val digest = MessageDigest.getInstance("SHA-256")
        var frames = 0L
            private set
        var pcmSha256: String? = null
            private set
        private var closed = false

        init {
            try { output.write(header(0)) } catch (error: Exception) {
                output.close()
                throw error
            }
        }

        fun write(samples: ShortArray) {
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val sample = samples[i].toInt()
                bytes[i * 2] = sample.toByte()
                bytes[i * 2 + 1] = (sample ushr 8).toByte()
            }
            output.write(bytes)
            digest.update(bytes)
            frames += samples.size
        }

        override fun close() {
            if (closed) return
            closed = true
            output.close()
            RandomAccessFile(file, "rw").use { it.write(header((frames * 2).toInt())) }
            pcmSha256 = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }

        private fun header(dataBytes: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataBytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16); put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes)
        }.array()
    }

    private class StorageLimit(val reason: String) : IOException(reason)

    /** Only IO workers enter these locks; recording/finish never wait for disk or another writer. */
    private class CacheBudget(val parent: File, var users: Int, private var usedBytes: Long) {
        @Synchronized fun write(bytes: Long, limit: Long, reserve: Long, available: (File) -> Long, action: () -> Unit) {
            if (usedBytes > limit || bytes > limit - usedBytes) throw StorageLimit("cache_budget")
            val free = available(parent)
            if (free < reserve || bytes > free - reserve) throw StorageLimit("storage_reserve")
            // Keep the reservation even after a partial IO failure. The next idle-to-active
            // transition rescans actual sizes, so uncertain writes can never undercount.
            usedBytes += bytes
            action()
        }
    }

    private object CacheBudgets {
        private val active = mutableMapOf<String, CacheBudget>()

        @Synchronized fun acquire(parent: File, limit: Long): CacheBudget {
            val canonical = parent.canonicalFile
            active[canonical.path]?.let { it.users++; return it }
            val budget = CacheBudget(canonical, 1, footprint(canonical, limit))
            active[canonical.path] = budget
            return budget
        }

        @Synchronized fun release(budget: CacheBudget) {
            if (--budget.users == 0) active.remove(budget.parent.path)
        }

        /** Scan once per group of overlapping sessions, stopping early at the byte/entry/depth cap. */
        private fun footprint(parent: File, limit: Long): Long {
            var bytes = 0L
            var entries = 0
            fun visit(directory: File, depth: Int) {
                if (depth > 8) throw StorageLimit("cache_scan_limit")
                val children = directory.listFiles() ?: throw IOException("Cannot inspect diagnostic cache")
                if (children.size > 4096 - entries) throw StorageLimit("cache_scan_limit")
                entries += children.size
                for (child in children) {
                    if (child.isDirectory) {
                        if (child.canonicalFile != child.absoluteFile) throw StorageLimit("cache_scan_limit")
                        visit(child, depth + 1)
                    } else {
                        val size = child.length()
                        if (size >= limit - bytes) throw StorageLimit("cache_budget")
                        bytes += size
                    }
                }
            }
            visit(parent, 0)
            return bytes
        }
    }
}
