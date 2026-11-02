package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event

@Composable
actual fun rememberReferenceAudioPlayer(): AudioPlayer = remember { WebReferenceAudioPlayer() }

/** HTML media playback stays in the application; no external window or download. */
class WebReferenceAudioPlayer : AudioPlayer {
    override val available = true
    private val mutable = MutableStateFlow(AudioPlaybackState())
    override val state = mutable.asStateFlow()
    private var audio: HTMLAudioElement? = null
    private var released = false
    private val visibility: (Event) -> Unit = { stop() }
    init { document.addEventListener("visibilitychange", visibility) }
    @OptIn(ExperimentalWasmJsInterop::class)
    override fun load(source: AudioSource, positionMs: Long, speed: Float) {
        if (released) return
        stop()
        val element = document.createElement("audio") as HTMLAudioElement
        audio = element
        mutable.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading)
        element.setAttribute("hidden", "")
        document.body?.appendChild(element)
        element.src = source.url
        element.playbackRate = speed.toDouble()
        element.addEventListener("loadedmetadata", { if (audio === element) {
            element.currentTime = positionMs / 1000.0
            update(element, AudioPlaybackStatus.Loading)
        } })
        element.addEventListener("playing", { if (audio === element) update(element, AudioPlaybackStatus.Playing) })
        element.addEventListener("timeupdate", { if (audio === element) update(element, mutable.value.status) })
        element.addEventListener("ended", { if (audio === element) update(element, AudioPlaybackStatus.Completed) })
        element.addEventListener("error", { if (audio === element) update(element, AudioPlaybackStatus.Failed) })
        // Called synchronously from the user's button gesture, preserving browser autoplay permission.
        element.play().catch { if (audio === element) update(element, AudioPlaybackStatus.Failed); null }
    }
    private fun update(element: HTMLAudioElement, status: AudioPlaybackStatus) {
        mutable.value = mutable.value.copy(status = status,
            positionMs = (element.currentTime.takeIf { it.isFinite() } ?: 0.0).times(1000).toLong(),
            durationMs = (element.duration.takeIf { it.isFinite() } ?: 0.0).times(1000).toLong())
    }
    @OptIn(ExperimentalWasmJsInterop::class)
    override fun resume(speed: Float) {
        val element = audio ?: return
        element.playbackRate = speed.toDouble()
        element.play().catch { if (audio === element) update(element, AudioPlaybackStatus.Failed); null }
    }
    override fun pause() { audio?.pause(); mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Paused) }
    override fun setSpeed(speed: Float) { audio?.playbackRate = speed.toDouble() }
    override fun stop() {
        val element = audio
        audio = null
        element?.pause()
        element?.removeAttribute("src")
        element?.load()
        element?.parentNode?.removeChild(element)
        mutable.value = AudioPlaybackState()
    }
    override fun release() { stop(); released = true; document.removeEventListener("visibilitychange", visibility) }
}
