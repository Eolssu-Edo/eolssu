package com.ssafy.eolssu.platform.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val PCM_FRAME_BYTES = 2

@OptIn(UnstableApi::class)
internal fun stretchJangdanRoll(pcm: ShortArray, targetFrames: Int): ShortArray {
    require(pcm.isNotEmpty() && targetFrames in 1..JANGDAN_SAMPLE_RATE * 4)
    val sonic = SonicAudioProcessor().apply {
        setSpeed(pcm.size.toFloat() / targetFrames)
        configure(AudioProcessor.AudioFormat(JANGDAN_SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        flush(AudioProcessor.StreamMetadata.DEFAULT)
    }
    val input = ByteBuffer.allocateDirect(pcm.size * PCM_FRAME_BYTES).order(ByteOrder.nativeOrder()).apply {
        asShortBuffer().put(pcm)
    }
    val output = if (sonic.isActive) {
        sonic.queueInput(input)
        sonic.queueEndOfStream()
        sonic.output
    } else {
        input
    }
    // Sonic may round its output length. Keep the score's frame count, trimming or zero-padding the tail.
    check(output.hasRemaining()) { "Empty jangdan roll after time stretch" }
    val result = ShortArray(targetFrames)
    output.order(ByteOrder.nativeOrder()).asShortBuffer().get(result, 0, minOf(targetFrames, output.remaining() / PCM_FRAME_BYTES))
    sonic.reset()
    return result
}

internal suspend fun decodeJangdanPcm(source: CachedJangdanSample): ShortArray {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(source.file.absolutePath)
        val format = extractor.getTrackFormat(0)
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        check(format.getString(MediaFormat.KEY_MIME) == mime)
        extractor.selectTrack(0)
        val firstTimeUs = extractor.sampleTime
        val delay = if (format.containsKey(MediaFormat.KEY_ENCODER_DELAY)) {
            format.getInteger(MediaFormat.KEY_ENCODER_DELAY)
        } else 0
        // Read raw AAC frames and trim the container's priming and tail here, once.
        val priming = maxOf(delay, (-firstTimeUs * (JANGDAN_SAMPLE_RATE / 1_000_000.0)).roundToInt())
        format.setInteger(MediaFormat.KEY_ENCODER_DELAY, 0)
        format.setInteger(MediaFormat.KEY_ENCODER_PADDING, 0)
        val decoder = MediaCodec.createDecoderByType(mime)
        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            val bytes = readRollPcm(extractor, decoder, firstTimeUs, priming, source.frames)
            return ShortArray(source.frames).also { ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(it) }
        } finally {
            decoder.release()
        }
    } finally {
        extractor.release()
    }
}

private suspend fun readRollPcm(
    extractor: MediaExtractor,
    decoder: MediaCodec,
    firstTimeUs: Long,
    priming: Int,
    frames: Int,
): ByteArray {
    val data = ByteArray(frames * PCM_FRAME_BYTES)
    val info = MediaCodec.BufferInfo()
    var skipFrames = priming
    var inputEnded = false
    var written = 0
    val deadline = SystemClock.elapsedRealtime() + 4_000
    while (SystemClock.elapsedRealtime() < deadline) {
        currentCoroutineContext().ensureActive()
        if (!inputEnded) {
            val index = decoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val input = checkNotNull(decoder.getInputBuffer(index))
                val size = extractor.readSampleData(input, 0)
                inputEnded = size < 0
                if (inputEnded) {
                    decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    decoder.queueInputBuffer(index, 0, size, extractor.sampleTime - firstTimeUs, 0)
                    extractor.advance()
                }
            }
        }
        val index = decoder.dequeueOutputBuffer(info, 10_000)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val pcm = decoder.outputFormat
            check(pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE) == JANGDAN_SAMPLE_RATE)
            check(pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
            check(!pcm.containsKey(MediaFormat.KEY_PCM_ENCODING) ||
                pcm.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT)
        }
        if (index < 0) continue
        if (info.size > 0) {
            val decodedFrames = info.size / PCM_FRAME_BYTES
            val skip = minOf(skipFrames, decodedFrames)
            skipFrames -= skip
            val count = minOf(decodedFrames - skip, frames - written)
            if (count > 0) {
                val buffer = checkNotNull(decoder.getOutputBuffer(index))
                buffer.position(info.offset + skip * PCM_FRAME_BYTES)
                buffer.get(data, written * PCM_FRAME_BYTES, count * PCM_FRAME_BYTES)
                written += count
            }
        }
        decoder.releaseOutputBuffer(index, false)
        if (written == frames) return data
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
    }
    error("Incomplete jangdan PCM: $written/$frames frames")
}
