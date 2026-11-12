package com.ssafy.eolssu.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeMark

/** A foreground audio source. The id identifies one playback request, not a database row. */
data class AudioSource(val id: String, val url: String, val localFile: LocalAudioFile? = null)

/** A file issued by the app's own audio store. Network URLs cannot be used as local files. */
class LocalAudioFile internal constructor(internal val path: String)

enum class AudioPlaybackStatus { Idle, Loading, Playing, Paused, Completed, Interrupted, Failed, Unavailable }

/** Position is the decoded media position, including any leading silence and tail. */
data class AudioPlaybackState(
    val sourceId: String? = null,
    val status: AudioPlaybackStatus = AudioPlaybackStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** 위치를 읽은 시각. UI 전달 지연을 재생 경과 시간으로 오인하지 않도록 함께 보낸다. */
    val positionSampledAt: TimeMark? = null,
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
