package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.ReferenceAudioPlayer
import com.ssafy.eolssu.core.audio.referenceSeekTarget
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.TimeSource
import kotlinx.browser.document
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
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event

/**
 * HTML `<audio>` 하나로 모범 연주·녹음·안내 음원을 튼다. JS·Wasm이 함께 쓰고, 상태 전이는 `AndroidAudioPlayer`와 같다.
 *
 * 모범 연주와 녹음은 주소를 받는 네트워크 대기 뒤에 `load()`가 불려 클릭 제스처를 벗어난다.
 * iOS Safari는 제스처 안에서 한 번 `play()`한 요소만 그 뒤의 재생을 허락하므로, 요소를 바꾸지 않고 재사용하며
 * 재생 중이 아닐 때 들어온 첫 터치·클릭·키 입력에서 무음 클립을 한 번 틀어 둔다.
 *
 * @param retainCompletedMedia 끝난 음원을 버리지 않고 남겨 [seekTo]로 다시 옮길 수 있게 한다(안내 음원).
 */
class WebAudioPlayer(private val retainCompletedMedia: Boolean = false) : ReferenceAudioPlayer {
    override val available = true
    private val _state = MutableStateFlow(AudioPlaybackState())
    override val state = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val element = document.createElement("audio") as HTMLAudioElement

    /** 지금 요소에 걸린 재생 요청. null이면 요소의 이벤트(무음 클립 포함)를 무시한다. */
    private var source: AudioSource? = null
    private var prepared = false
    private var startPositionMs = 0L
    private var speed = 1f
    private var waitingForClock: Long? = null
    private var unlocked = false
    private var released = false
    private var poll: Job? = null
    private var prepareTimeout: Job? = null

    private val visibility: (Event) -> Unit = { if (pageHidden()) interrupt() }
    private val unlock: (Event) -> Unit = { unlockForLaterPlayback() }

    init {
        element.preload = "auto"
        element.addEventListener("loadedmetadata", { _: Event -> onMetadata() })
        element.addEventListener("playing", { _: Event -> onPlaying() })
        element.addEventListener("waiting", { _: Event -> onWaiting() })
        element.addEventListener("ended", { _: Event -> onEnded() })
        element.addEventListener("error", { _: Event -> if (source != null) fail() })
        document.addEventListener("visibilitychange", visibility)
        UnlockEvents.forEach { document.addEventListener(it, unlock, true) }
    }

    override fun load(source: AudioSource, positionMs: Long, speed: Float) {
        if (released) return
        disposeMedia()
        _state.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading, positionMs.coerceAtLeast(0))
        // 웹에는 앱 저장소 파일이 없다. 파일 재생 요청은 소리 없이 기다리지 않고 바로 실패로 알린다.
        if (source.localFile != null || !validUrl(source.url) || !validSpeed(speed) || positionMs < 0) return fail()
        this.source = source
        this.speed = speed
        startPositionMs = positionMs
        runCatching {
            element.src = source.url
            element.defaultPlaybackRate = speed.toDouble()
            element.playbackRate = speed.toDouble()
        }.onFailure { return fail() }
        prepareTimeout = scope.launch {
            delay(PREPARE_TIMEOUT_MS)
            if (this@WebAudioPlayer.source === source && _state.value.status == AudioPlaybackStatus.Loading) fail()
        }
        // 안내 음원은 버튼 제스처 안에서 바로 불리므로 여기서 동기로 play()해야 자동재생 허락이 유지된다.
        playElement(source)
    }

    override fun resume(speed: Float) {
        if (released) return
        if (!validSpeed(speed)) return fail()
        this.speed = speed
        val current = source ?: return
        if (!prepared || _state.value.status != AudioPlaybackStatus.Paused) return
        element.playbackRate = speed.toDouble()
        playElement(current)
    }

    override fun pause() {
        if (source == null) return
        when (_state.value.status) {
            AudioPlaybackStatus.Loading -> {
                val paused = _state.value.copy(status = AudioPlaybackStatus.Paused)
                disposeMedia()
                _state.value = paused
            }
            AudioPlaybackStatus.Playing -> {
                element.pause()
                stopPolling()
                _state.value = _state.value.copy(status = AudioPlaybackStatus.Paused, positionMs = currentPositionMs(),
                    positionSampledAt = null, positionClockRate = null)
            }
            else -> Unit
        }
    }

    override fun seekTo(positionMs: Long) {
        if (source == null || !prepared) return
        val target = _state.value.referenceSeekTarget(positionMs) ?: return
        runCatching {
            stopPolling()
            element.currentTime = target.positionMs / 1000.0
            _state.value = target
            if (target.status == AudioPlaybackStatus.Playing) {
                waitingForClock = target.positionMs
                startPolling()
            }
        }.onFailure { fail() }
    }

    override fun setSpeed(speed: Float) {
        if (released) return
        if (!validSpeed(speed)) return fail()
        this.speed = speed
        if (source == null) return
        runCatching {
            element.defaultPlaybackRate = speed.toDouble()
            element.playbackRate = speed.toDouble()
        }.onFailure { fail() }
    }

    override fun stop() {
        if (released) return
        disposeMedia()
        _state.value = AudioPlaybackState()
    }

    override fun release() {
        if (released) return
        released = true
        disposeMedia()
        if (element.hasAttribute("src")) clearElement()
        _state.value = AudioPlaybackState()
        document.removeEventListener("visibilitychange", visibility)
        removeUnlockListeners()
        scope.cancel()
    }

    private fun onMetadata() {
        if (source == null) return
        runCatching {
            prepared = true
            val duration = element.duration
            val durationMs = if (duration.isFinite()) (duration * 1000).toLong() else 0L
            if (durationMs <= 0L || startPositionMs >= durationMs) return fail()
            _state.value = _state.value.copy(durationMs = durationMs)
            if (startPositionMs > 0L) element.currentTime = startPositionMs / 1000.0
        }.onFailure { fail() }
    }

    private fun onPlaying() {
        if (source == null || _state.value.status !in Resumable) return
        prepareTimeout?.cancel()
        val position = currentPositionMs()
        // 실제로 위치가 움직이기 전까지 반주 시계를 0배로 둔다(디코더가 아직 진행하지 않음).
        waitingForClock = position
        _state.value = _state.value.copy(status = AudioPlaybackStatus.Playing, positionMs = position,
            positionSampledAt = TimeSource.Monotonic.markNow(), positionClockRate = 0f)
        startPolling()
    }

    /** 버퍼링이 시작되면 다음 위치 표본을 기다리지 않고 반주 시계를 바로 0배로 멈춘다. */
    private fun onWaiting() {
        if (source == null || _state.value.status != AudioPlaybackStatus.Playing) return
        val position = currentPositionMs()
        waitingForClock = position
        _state.value = _state.value.copy(positionMs = position, positionSampledAt = TimeSource.Monotonic.markNow(),
            positionClockRate = 0f)
    }

    private fun onEnded() {
        if (source == null) return
        val durationMs = _state.value.durationMs.takeIf { it > 0L } ?: currentPositionMs()
        val completed = _state.value.copy(status = AudioPlaybackStatus.Completed, positionMs = durationMs,
            durationMs = durationMs, positionSampledAt = null, positionClockRate = null)
        if (retainCompletedMedia) {
            stopPolling()
            prepareTimeout?.cancel()
        } else disposeMedia()
        _state.value = completed
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun playElement(requested: AudioSource) {
        runCatching {
            // 거절 사유: 자동재생 정책(NotAllowedError), 지원하지 않는 형식, 다른 load()에 의한 중단(무시).
            element.play().catch {
                if (source === requested) fail()
                null
            }
        }.onFailure { if (source === requested) fail() }
    }

    private fun startPolling() {
        poll?.cancel()
        poll = scope.launch {
            while (isActive && source != null && _state.value.status == AudioPlaybackStatus.Playing) {
                samplePosition()
                delay(if (waitingForClock == null) POLL_MS else WAITING_POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        poll?.cancel()
        poll = null
    }

    private fun samplePosition() {
        val position = currentPositionMs()
        if (waitingForClock?.let { position > it } == true) waitingForClock = null
        val advancing = waitingForClock == null
        if (advancing || position != _state.value.positionMs) {
            _state.value = _state.value.copy(positionMs = position, positionSampledAt = TimeSource.Monotonic.markNow(),
                positionClockRate = if (advancing) element.playbackRate.toFloat() else 0f)
        }
    }

    /** 페이지가 가려지면 앱의 오디오 포커스 상실처럼 멈추고 자원을 놓는다. 다시 재생은 사용자가 누른다. */
    private fun interrupt() {
        if (source == null || _state.value.status !in Resumable) return
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
        positionMs = if (prepared) currentPositionMs() else _state.value.positionMs,
        positionSampledAt = null, positionClockRate = null)

    private fun disposeMedia() {
        stopPolling()
        prepareTimeout?.cancel()
        prepareTimeout = null
        val hadSource = source != null
        source = null
        prepared = false
        waitingForClock = null
        if (hadSource) clearElement()
    }

    private fun clearElement() {
        runCatching {
            element.pause()
            element.removeAttribute("src")
            element.load()
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun unlockForLaterPlayback() {
        if (unlocked || released || source != null) return
        unlocked = true
        removeUnlockListeners()
        runCatching {
            element.src = SILENT_WAV
            element.play().catch { null }
        }
    }

    private fun removeUnlockListeners() = UnlockEvents.forEach { document.removeEventListener(it, unlock, true) }

    private fun currentPositionMs(): Long =
        ((element.currentTime.takeIf { it.isFinite() } ?: 0.0) * 1000).toLong().coerceAtLeast(0L)

    private fun validSpeed(value: Float) = value.isFinite() && value in 0.25f..4f

    private fun validUrl(value: String) =
        value.startsWith("https://") || value.startsWith("http://") || value.startsWith("blob:")

    private companion object {
        const val PREPARE_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
        const val WAITING_POLL_MS = 10L
        val Resumable = setOf(AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)
        val UnlockEvents = listOf("touchend", "pointerup", "click", "keydown")

        /** 10ms 무음 8kHz 8bit PCM. iOS Safari에서 이 요소의 재생 허락을 얻는 데만 쓴다. */
        const val SILENT_WAV = "data:audio/wav;base64,UklGRnQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YVAAAACAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgA=="
    }
}
