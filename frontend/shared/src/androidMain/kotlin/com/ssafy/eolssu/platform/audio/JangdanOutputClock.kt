package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE

/** Converts only advancing hardware timestamps, including their unsigned 32-bit frame rollover. */
internal class JangdanOutputClock {
    private var previousFrame: Long? = null
    private var previousNanos = 0L
    private var frameZero: Long? = null

    fun reset() {
        previousFrame = null
        previousNanos = 0
        frameZero = null
    }

    fun observe(position: Long, nanos: Long, writtenFrames: Long, nowNanos: Long): Long? {
        var frame = (writtenFrames and -0x1_0000_0000L) or (position and 0xffff_ffffL)
        if (frame > writtenFrames) frame -= 0x1_0000_0000L
        // AudioTrack may report a frame committed for presentation slightly in the future.
        if (frame < 0 || nanos <= 0 || nowNanos - nanos !in -500_000_000L..500_000_000L ||
            previousFrame?.let { frame < it || nanos < previousNanos } == true) {
            reset()
            return null
        }
        val previous = previousFrame
        if (previous != null && frame > previous && nanos > previousNanos) {
            frameZero = nanos - frame * 1_000_000_000L / JANGDAN_SAMPLE_RATE
        }
        previousFrame = frame
        previousNanos = nanos
        return frameZero
    }
}
