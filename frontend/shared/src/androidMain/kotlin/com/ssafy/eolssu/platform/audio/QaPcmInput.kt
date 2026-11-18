package com.ssafy.eolssu.platform.audio

import java.io.DataInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Fixed 16kHz mono signed16 input for an explicitly selected loopback QA recording. */
internal class QaPcmInput private constructor(
    private val samples: ShortArray,
    private val outputRate: Int,
    private val nanoTime: () -> Long,
    private val waitNanos: (Long) -> Unit,
) {
    private var position = 0L
    private var segmentSamples = 0L
    private var segmentNanos = 0L

    fun startSegment(): Long {
        segmentSamples = 0
        segmentNanos = nanoTime()
        return segmentNanos
    }

    fun read(destination: ShortArray): Int {
        destination.indices.forEach { index ->
            val numerator = (position + index) * INPUT_RATE
            val source = numerator / outputRate
            val fraction = (numerator % outputRate).toDouble() / outputRate
            val first = samples.getOrNull(source.toInt())?.toInt() ?: 0
            val second = samples.getOrNull((source + 1).toInt())?.toInt() ?: 0
            destination[index] = (first + (second - first) * fraction).roundToInt().toShort()
        }
        position += destination.size
        segmentSamples += destination.size
        val remaining = segmentNanos + segmentSamples * 1_000_000_000 / outputRate - nanoTime()
        if (remaining > 0) waitNanos(remaining)
        return destination.size
    }

    companion object {
        private const val INPUT_RATE = 16_000
        private const val MAX_BYTES = INPUT_RATE * 2 * 60

        fun open(
            file: File,
            outputRate: Int,
            nanoTime: () -> Long = System::nanoTime,
            waitNanos: (Long) -> Unit = { TimeUnit.NANOSECONDS.sleep(it) },
        ): QaPcmInput? {
            if (!file.exists()) return null
            val size = file.length()
            require(file.isFile && size in 2L..MAX_BYTES.toLong() && size % 2 == 0L) {
                "Invalid QA PCM input"
            }
            require(outputRate > 0)
            val bytes = ByteArray(size.toInt())
            DataInputStream(file.inputStream()).use { input ->
                input.readFully(bytes)
                require(input.read() == -1) { "QA PCM input changed while opening" }
            }
            val samples = ShortArray(bytes.size / 2) { index ->
                ((bytes[index * 2].toInt() and 0xff) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
            }
            return QaPcmInput(samples, outputRate, nanoTime, waitNanos)
        }
    }
}
