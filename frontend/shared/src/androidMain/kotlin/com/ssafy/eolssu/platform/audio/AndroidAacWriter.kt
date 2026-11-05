package com.ssafy.eolssu.platform.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/** 녹음 작업 하나가 소유한다. 입력 샘플 수로 시각을 정해 일시정지 시간을 파일에 넣지 않는다. */
internal class AndroidAacWriter(file: File, private val sampleRate: Int) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var started = false
    private var samples = 0L
    private var eos = false

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun write(pcm: ShortArray, count: Int) {
        var offset = 0
        val deadline = System.nanoTime() + 1_000_000_000L
        while (offset < count) {
            check(System.nanoTime() < deadline) { "AAC input timeout" }
            drain(false)
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) continue
            val buffer = requireNotNull(codec.getInputBuffer(index)).apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }
            val length = minOf(count - offset, buffer.remaining() / 2)
            check(length > 0)
            repeat(length) { buffer.putShort(pcm[offset + it]) }
            codec.queueInputBuffer(index, 0, length * 2, samples * 1_000_000 / sampleRate, 0)
            samples += length
            offset += length
        }
        drain(false)
    }

    fun finish() {
        val deadline = System.nanoTime() + 2_000_000_000L
        var queued = false
        while (!eos) {
            check(System.nanoTime() < deadline) { "AAC finalization timeout" }
            if (!queued) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, samples * 1_000_000 / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queued = true
                }
            }
            drain(true)
        }
        check(started && samples > 0) { "Empty recording" }
        requireNotNull(muxer).stop()
        started = false
    }

    private fun drain(wait: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, if (wait) 10_000 else 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started)
                    track = requireNotNull(muxer).addTrack(codec.outputFormat)
                    requireNotNull(muxer).start()
                    started = true
                }
                else -> if (index >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(started)
                            val buffer = requireNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            requireNotNull(muxer).writeSampleData(track, buffer, info)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                    } finally { codec.releaseOutputBuffer(index, false) }
                    if (eos) return
                }
            }
        }
    }

    fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (started) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        started = false
    }
}
