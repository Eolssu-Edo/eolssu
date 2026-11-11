package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFoundation.AVAudioTimePitchAlgorithmSpectral
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol
import kotlin.coroutines.resume

/**
 * Foreground-only `AVPlayer`. Every call and callback runs on the main queue, mirroring the single worker of the Android player.
 * Interruptions (calls, Siri, another app taking audio) and unplugged headphones stop playback as Interrupted; the user restarts it.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioPlaybackState())
    override val state = _state.asStateFlow()
    override val available = true
    private var released = false
    private var player: AVPlayer? = null
    private var item: AVPlayerItem? = null
    private var prepared = false
    private var speed = 1f
    private var sessionActive = false
    private var loading: Job? = null
    private var ticker: Job? = null
    private val itemObservers = mutableListOf<NSObjectProtocol>()
    private val notifications = NSNotificationCenter.defaultCenter
    private val sessionObservers = listOf(
        notifications.addObserverForName(AVAudioSessionInterruptionNotification, `object` = null,
            queue = NSOperationQueue.mainQueue) { note ->
            if (note?.number(AVAudioSessionInterruptionTypeKey) == AVAudioSessionInterruptionTypeBegan) interrupt()
        },
        notifications.addObserverForName(AVAudioSessionRouteChangeNotification, `object` = null,
            queue = NSOperationQueue.mainQueue) { note ->
            if (note?.number(AVAudioSessionRouteChangeReasonKey) == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) interrupt()
        },
    )

    override fun load(source: AudioSource, positionMs: Long, speed: Float) = dispatch {
        disposeMedia()
        _state.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading, positionMs.coerceAtLeast(0))
        if (!validSpeed(speed) || positionMs !in 0..Int.MAX_VALUE.toLong()) return@dispatch fail()
        val localFile = source.localFile
        val url = if (localFile == null) {
            NSURL.URLWithString(source.url)?.takeIf {
                it.scheme == "https" && !it.host.isNullOrBlank() && it.user == null && it.password == null && it.fragment == null
            }
        } else {
            localFile.takeIf { source.url.isEmpty() }?.let { NSURL.fileURLWithPath(it.path) }
        } ?: return@dispatch fail()
        this.speed = speed
        val current = AVPlayerItem(uRL = url).apply { audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmSpectral }
        val media = AVPlayer(playerItem = current)
        item = current
        player = media
        itemObservers += notifications.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, `object` = current,
            queue = NSOperationQueue.mainQueue) { _ -> if (item === current) complete() }
        itemObservers += notifications.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, `object` = current,
            queue = NSOperationQueue.mainQueue) { _ -> if (item === current) fail() }
        loading = scope.launch {
            val ready = withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                while (isActive && current.status != AVPlayerItemStatusReadyToPlay) {
                    if (current.status == AVPlayerItemStatusFailed) return@withTimeoutOrNull false
                    delay(POLL_MS)
                }
                true
            }
            if (item !== current) return@launch
            val durationMs = seconds(current.duration).let { if (it.isFinite()) (it * 1000).toLong() else 0L }
            if (ready != true || durationMs <= 0 || positionMs >= durationMs) return@launch fail()
            prepared = true
            _state.value = _state.value.copy(durationMs = durationMs)
            if (positionMs > 0 && !seek(media, positionMs)) return@launch fail()
            if (item === current) startPrepared()
        }
    }

    override fun resume(speed: Float) = dispatch {
        if (!validSpeed(speed)) return@dispatch fail()
        this.speed = speed
        if (prepared && _state.value.status == AudioPlaybackStatus.Paused) startPrepared()
    }

    override fun pause() = dispatch {
        val media = player ?: return@dispatch
        if (!prepared) {
            val paused = _state.value.copy(status = AudioPlaybackStatus.Paused)
            disposeMedia()
            _state.value = paused
        } else if (_state.value.status == AudioPlaybackStatus.Playing) {
            media.pause()
            ticker?.cancel()
            _state.value = _state.value.copy(status = AudioPlaybackStatus.Paused, positionMs = positionOf(media))
            deactivateSession()
        }
    }

    override fun setSpeed(speed: Float) = dispatch {
        if (!validSpeed(speed)) return@dispatch fail()
        this.speed = speed
        if (_state.value.status == AudioPlaybackStatus.Playing) player?.rate = speed
    }

    override fun stop() = dispatch {
        disposeMedia()
        _state.value = AudioPlaybackState()
    }

    override fun release() {
        if (released) return
        released = true
        scope.launch {
            disposeMedia()
            sessionObservers.forEach { notifications.removeObserver(it) }
            _state.value = AudioPlaybackState()
            scope.cancel()
        }
    }

    private fun startPrepared() {
        val media = player ?: return
        if (!prepared || !activateSession()) return interrupt()
        media.rate = speed
        _state.value = _state.value.copy(status = AudioPlaybackStatus.Playing, positionMs = positionOf(media))
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && _state.value.status == AudioPlaybackStatus.Playing && player === media) {
                _state.value = _state.value.copy(positionMs = positionOf(media))
                delay(POLL_MS)
            }
        }
    }

    private suspend fun seek(media: AVPlayer, positionMs: Long): Boolean = suspendCancellableCoroutine { continuation ->
        media.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, preferredTimescale = 1000),
            toleranceBefore = kCMTimeZero.readValue(), toleranceAfter = kCMTimeZero.readValue()) { finished ->
            if (continuation.isActive) continuation.resume(finished)
        }
    }

    private fun complete() {
        val completed = _state.value.copy(status = AudioPlaybackStatus.Completed,
            positionMs = _state.value.durationMs, durationMs = _state.value.durationMs)
        disposeMedia()
        _state.value = completed
    }

    private fun interrupt() {
        if (_state.value.status !in setOf(AudioPlaybackStatus.Playing, AudioPlaybackStatus.Loading, AudioPlaybackStatus.Paused)) return
        val interrupted = snapshot(AudioPlaybackStatus.Interrupted)
        disposeMedia()
        _state.value = interrupted
    }

    private fun fail() {
        val failed = snapshot(AudioPlaybackStatus.Failed)
        disposeMedia()
        _state.value = failed
    }

    private fun snapshot(status: AudioPlaybackStatus): AudioPlaybackState = _state.value.copy(status = status,
        positionMs = player?.takeIf { prepared }?.let(::positionOf) ?: _state.value.positionMs)

    private fun disposeMedia() {
        loading?.cancel()
        ticker?.cancel()
        loading = null
        ticker = null
        itemObservers.forEach { notifications.removeObserver(it) }
        itemObservers.clear()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        item = null
        prepared = false
        deactivateSession()
    }

    private fun activateSession(): Boolean {
        if (!sessionActive) sessionActive = IosAudioSession.startPlayback()
        return sessionActive
    }

    private fun deactivateSession() {
        if (!sessionActive) return
        IosAudioSession.stopPlayback()
        sessionActive = false
    }

    private fun positionOf(media: AVPlayer): Long =
        seconds(media.currentTime()).let { if (it.isFinite()) (it * 1000).toLong().coerceAtLeast(0) else _state.value.positionMs }

    private fun seconds(time: CValue<CMTime>): Double = CMTimeGetSeconds(time)

    private fun NSNotification.number(key: Any?): ULong? = (userInfo?.get(key) as? NSNumber)?.unsignedLongValue

    private fun validSpeed(value: Float) = value.isFinite() && value in 0.25f..4f
    private fun dispatch(block: () -> Unit) { if (!released) scope.launch { if (!released) block() } }

    private companion object {
        const val PREPARE_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
