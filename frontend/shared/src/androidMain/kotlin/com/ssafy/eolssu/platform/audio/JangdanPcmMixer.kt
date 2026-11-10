package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import kotlin.math.roundToInt

/** Owned by the output worker. Samples and gains are exactly those exposed to the echo reference. */
internal class JangdanPcmMixer {
    private data class Voice(val clip: JangdanClip, val pcm: ShortArray, val gain: Float, var offset: Int = 0)
    private val voices = ArrayDeque<Voice>()
    val active: Boolean get() = voices.isNotEmpty()

    /** Equal-priority SoundPool behavior: a ninth voice replaces the oldest one. */
    fun play(clip: JangdanClip, pcm: ShortArray, gain: Float): Boolean {
        require(pcm.isNotEmpty() && gain.isFinite() && gain in 0f..1f)
        val replaced = voices.size == 8
        if (replaced) voices.removeFirst()
        voices.addLast(Voice(clip, pcm, gain))
        return replaced
    }

    fun clear() = voices.clear()

    fun render(frames: Int, firstFrame: Long): JangdanPcmBlock {
        require(frames > 0 && firstFrame >= 0)
        val mixed = FloatArray(frames)
        val events = mutableListOf<RenderedJangdanEvent>()
        var voiceFrames = 0
        for (voice in voices) {
            if (voice.offset == 0) events += RenderedJangdanEvent(voice.clip, voice.gain, firstFrame)
            val count = minOf(frames, voice.pcm.size - voice.offset)
            voiceFrames = maxOf(voiceFrames, count)
            for (i in 0 until count) mixed[i] += voice.pcm[voice.offset + i] * voice.gain
            voice.offset += count
        }
        voices.removeAll { it.offset == it.pcm.size }
        var clipped = false
        val pcm = ShortArray(frames) { i ->
            val value = mixed[i].roundToInt()
            if (value !in Short.MIN_VALUE..Short.MAX_VALUE) clipped = true
            value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return JangdanPcmBlock(pcm, events, clipped, voiceFrames)
    }
}

/** A block's start events are published only after AudioTrack accepts its first PCM frame. */
internal class JangdanPcmBlock(
    val pcm: ShortArray,
    val events: List<RenderedJangdanEvent>,
    val clipped: Boolean,
    val voiceFrames: Int,
) {
    var written: Int = 0
        private set
    val remaining: Int get() = pcm.size - written

    fun accept(frames: Int): List<RenderedJangdanEvent> {
        require(frames in 0..remaining)
        val started = if (written == 0 && frames > 0) events else emptyList()
        written += frames
        return started
    }
}
