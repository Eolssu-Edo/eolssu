package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Immutable PCM publication; clear invalidates work already decoding or waiting to decode. */
internal class JangdanPcmCache(
    private val decode: suspend (JangdanSample) -> ShortArray,
    private val stretch: suspend (ShortArray, Int) -> ShortArray,
) {
    private val lock = Any()
    private val preparing = Mutex()
    private var generation = 0L
    private val originals = mutableMapOf<JangdanSample, ShortArray>()
    private val samples = mutableMapOf<JangdanClip, ShortArray>()
    private var adjustedClip: JangdanClip? = null

    fun get(clip: JangdanClip): ShortArray? = synchronized(lock) { samples[clip] }

    fun clear() = synchronized(lock) {
        generation++
        samples.clear()
        originals.clear()
        adjustedClip = null
    }

    suspend fun prepare(clips: Set<JangdanClip>): Boolean {
        // Capture before waiting: a pre-release queued request must not become a new-session request.
        val token = synchronized(lock) { generation }
        return preparing.withLock {
            val adjusted = clips.filter { it.targetFrames != null }
            if (adjusted.size > 1 || adjusted.any { it.targetFrames !in 1..JANGDAN_SAMPLE_RATE * 4 }) return@withLock false
            if (synchronized(lock) { token != generation }) return@withLock false
            for (clip in clips) {
                if (get(clip) != null) continue
                val original = synchronized(lock) { originals[clip.sample] } ?: decode(clip.sample)
                if (synchronized(lock) { token != generation }) return@withLock false
                val pcm = clip.targetFrames?.let { stretch(original, it) } ?: original
                synchronized(lock) {
                    if (token != generation) return@withLock false
                    originals[clip.sample] = original
                    samples[clip] = pcm
                }
            }
            synchronized(lock) {
                if (token != generation) return@withLock false
                val next = adjusted.firstOrNull()
                adjustedClip?.takeIf { it != next }?.let(samples::remove)
                adjustedClip = next
            }
            true
        }
    }
}
