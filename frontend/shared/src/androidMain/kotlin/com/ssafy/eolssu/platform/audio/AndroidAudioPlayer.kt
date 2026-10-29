package com.ssafy.eolssu.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Foreground-only player. All MediaPlayer operations/callbacks use one worker looper. */
class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val thread = HandlerThread("eolssu-model-audio").apply { start() }
    private val handler = Handler(thread.looper)
    private val _state = MutableStateFlow(AudioPlaybackState())
    override val state = _state.asStateFlow()
    override val available = true
    @Volatile private var released = false
    private var player: MediaPlayer? = null
    private var prepared = false
    private var speed = 1f
    private var focusGranted = false
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private val prepareTimeout = Runnable { if (_state.value.status == AudioPlaybackStatus.Loading) fail() }
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) handler.post { interrupt() }
        // No automatic resume: the user explicitly restarts after calls/other media.
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) handler.post { interrupt() }
        }
    }
    private val updatePosition = object : Runnable {
        override fun run() {
            val current = player ?: return
            if (_state.value.status != AudioPlaybackStatus.Playing || !prepared) return
            runCatching { _state.value = _state.value.copy(positionMs = current.currentPosition.toLong()) }
                .onFailure { fail() }
            if (_state.value.status == AudioPlaybackStatus.Playing) handler.postDelayed(this, 50L)
        }
    }

    override fun load(source: AudioSource, positionMs: Long, speed: Float) = dispatch {
        disposeMedia()
        _state.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading, positionMs.coerceAtLeast(0))
        val uri = runCatching { URI(source.url) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null ||
            !validSpeed(speed) || positionMs !in 0..Int.MAX_VALUE.toLong()
        ) {
            fail()
            return@dispatch
        }
        this.speed = speed
        val current = runCatching { MediaPlayer() }.getOrElse { fail(); return@dispatch }
        player = current
        runCatching {
            current.setAudioAttributes(attributes)
            current.setOnErrorListener { media, _, _ -> if (player === media) fail(); true }
            current.setOnCompletionListener { media ->
                if (player === media) {
                    runCatching {
                        val completed = _state.value.copy(status = AudioPlaybackStatus.Completed,
                            positionMs = media.duration.toLong(), durationMs = media.duration.toLong())
                        disposeMedia()
                        _state.value = completed
                    }.onFailure { fail() }
                }
            }
            current.setOnPreparedListener { media ->
                if (player === media) {
                    runCatching {
                        prepared = true
                        if (media.duration <= 0 || positionMs >= media.duration) {
                            fail()
                        } else {
                            _state.value = _state.value.copy(durationMs = media.duration.toLong())
                            if (positionMs == 0L) startPrepared() else {
                                media.setOnSeekCompleteListener { seeked ->
                                    if (player === seeked) {
                                        runCatching {
                                            seeked.setOnSeekCompleteListener(null)
                                            startPrepared()
                                        }.onFailure { fail() }
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= 26) media.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                                else @Suppress("DEPRECATION") media.seekTo(positionMs.toInt())
                            }
                        }
                    }.onFailure { fail() }
                }
            }
            current.setDataSource(source.url)
            current.prepareAsync()
            handler.postDelayed(prepareTimeout, 30_000L)
        }.onFailure { fail() }
    }

    override fun resume(speed: Float) = dispatch {
        if (!validSpeed(speed)) return@dispatch fail()
        this.speed = speed
        if (prepared && _state.value.status == AudioPlaybackStatus.Paused) startPrepared()
    }

    override fun pause() = dispatch {
        val current = player ?: return@dispatch
        if (!prepared) {
            val paused = _state.value.copy(status = AudioPlaybackStatus.Paused)
            disposeMedia()
            _state.value = paused
        } else if (_state.value.status == AudioPlaybackStatus.Playing) {
            runCatching {
                current.pause()
                handler.removeCallbacks(updatePosition)
                _state.value = _state.value.copy(status = AudioPlaybackStatus.Paused, positionMs = current.currentPosition.toLong())
                abandonFocus()
            }.onFailure { fail() }
        }
    }

    override fun setSpeed(speed: Float) = dispatch {
        if (!validSpeed(speed)) return@dispatch fail()
        this.speed = speed
        if (_state.value.status == AudioPlaybackStatus.Playing) {
            runCatching { player?.playbackParams = PlaybackParams().allowDefaults().setSpeed(speed).setPitch(1f) }
                .onFailure { fail() }
        }
    }

    override fun stop() = dispatch {
        disposeMedia()
        _state.value = AudioPlaybackState()
    }

    override fun release() {
        if (released) return
        released = true
        handler.post {
            disposeMedia()
            _state.value = AudioPlaybackState()
            thread.quitSafely()
        }
    }

    private fun startPrepared() {
        val current = player ?: return
        if (!prepared || !requestFocus()) return interrupt()
        runCatching {
            // Setting PlaybackParams can start prepared media, so acquire focus first.
            current.playbackParams = PlaybackParams().allowDefaults().setSpeed(speed).setPitch(1f)
            current.start()
            handler.removeCallbacks(prepareTimeout)
            registerNoisyReceiver()
            _state.value = _state.value.copy(status = AudioPlaybackStatus.Playing, positionMs = current.currentPosition.toLong())
            handler.removeCallbacks(updatePosition)
            handler.post(updatePosition)
        }.onFailure { fail() }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        if (focusGranted) return true
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusListener, handler).build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusGranted
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        else if (focusGranted) runCatching { audioManager.abandonAudioFocus(focusListener) }
        focusRequest = null
        focusGranted = false
        if (noisyRegistered) runCatching { appContext.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    @Suppress("DEPRECATION")
    private fun registerNoisyReceiver() {
        if (noisyRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else appContext.registerReceiver(noisyReceiver, filter)
        noisyRegistered = true
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
        positionMs = if (prepared) runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: _state.value.positionMs else _state.value.positionMs)

    private fun disposeMedia() {
        handler.removeCallbacks(prepareTimeout)
        handler.removeCallbacks(updatePosition)
        val previous = player
        player = null
        prepared = false
        previous?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnSeekCompleteListener(null)
            it.setOnErrorListener(null)
            runCatching { it.release() }
        }
        abandonFocus()
    }

    private fun validSpeed(value: Float) = value.isFinite() && value in 0.25f..4f
    private fun dispatch(block: () -> Unit) { if (!released) handler.post { if (!released) block() } }
}
