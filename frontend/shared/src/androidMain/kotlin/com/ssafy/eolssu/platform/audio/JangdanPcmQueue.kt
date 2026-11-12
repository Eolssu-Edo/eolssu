package com.ssafy.eolssu.platform.audio

import kotlin.math.roundToInt

/** 출력 소유자가 동기화한다. 타격은 절대 프레임 위치에 배치하고 블록 경계에서 잘리지 않는다. */
internal class JangdanPcmQueue {
    var nextFrame = 0L
        private set

    private data class Voice(val pcm: ShortArray, val gain: Float, val start: Long)
    private val voices = mutableListOf<Voice>()

    fun add(pcm: ShortArray, gain: Float, frame: Long) {
        voices += Voice(pcm, gain, frame.coerceAtLeast(nextFrame))
    }

    fun read(frames: Int): ShortArray {
        val end = nextFrame + frames
        val mixed = FloatArray(frames)
        for (voice in voices) {
            val from = maxOf(nextFrame, voice.start)
            val until = minOf(end, voice.start + voice.pcm.size)
            for (at in from until until) {
                mixed[(at - nextFrame).toInt()] += voice.pcm[(at - voice.start).toInt()] * voice.gain
            }
        }
        voices.removeAll { it.start + it.pcm.size <= end }
        nextFrame = end
        return ShortArray(frames) { mixed[it].roundToInt().coerceIn(-32768, 32767).toShort() }
    }

    fun clear() { voices.clear(); nextFrame = 0 }
    fun discardBefore(frame: Long) {
        if (frame <= nextFrame) return
        nextFrame = frame
        voices.removeAll { it.start + it.pcm.size <= frame }
    }
}
