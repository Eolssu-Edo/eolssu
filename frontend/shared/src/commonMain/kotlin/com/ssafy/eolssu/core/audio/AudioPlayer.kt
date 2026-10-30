package com.ssafy.eolssu.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A foreground audio source. The id identifies one playback request, not a database row. */
data class AudioSource(val id: String, val url: String)

enum class AudioPlaybackStatus { Idle, Loading, Playing, Paused, Completed, Interrupted, Failed, Unavailable }

/** Position is the decoded media position, including any leading silence and tail. */
data class AudioPlaybackState(
    val sourceId: String? = null,
    val status: AudioPlaybackStatus = AudioPlaybackStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** Each screen owns a player. Calls return immediately; preparation and position updates are asynchronous. */
interface AudioPlayer {
    val available: Boolean
    val state: StateFlow<AudioPlaybackState>
    fun load(source: AudioSource, positionMs: Long, speed: Float)
    fun resume(speed: Float)
    fun pause()
    fun setSpeed(speed: Float)
    /** Frees media/focus resources while keeping the player reusable. */
    fun stop()
    /** Frees all resources. This instance cannot be reused. */
    fun release()
}

/** Explicit platform capability, never a silent timer that pretends to play. */
class UnavailableAudioPlayer : AudioPlayer {
    override val available = false
    override val state = MutableStateFlow(AudioPlaybackState(status = AudioPlaybackStatus.Unavailable))
    override fun load(source: AudioSource, positionMs: Long, speed: Float) = Unit
    override fun resume(speed: Float) = Unit
    override fun pause() = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
