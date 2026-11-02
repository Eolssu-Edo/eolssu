package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

@Composable
actual fun rememberReferenceAudioPlayer(): AudioPlayer {
    val client = koinInject<HttpClient>()
    return remember(client) { IosReferenceAudioPlayer(client) }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosReferenceAudioPlayer(private val client: HttpClient) : AudioPlayer {
    override val available = true
    private val mutable = MutableStateFlow(AudioPlaybackState())
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: AVAudioPlayer? = null
    override fun load(source: AudioSource, positionMs: Long, speed: Float) {
        stop()
        mutable.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading)
        job = scope.launch {
            try {
                val response = client.get(source.url)
                check(response.status.value == 200)
                val bytes = response.body<ByteArray>()
                ensureActive()
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                val current = AVAudioPlayer(data = data, error = null)
                current.enableRate = true
                current.rate = speed
                current.currentTime = positionMs / 1000.0
                check(current.prepareToPlay())
                player = current
                check(current.play())
                while (isActive) {
                    if (mutable.value.status == AudioPlaybackStatus.Paused) { delay(100); continue }
                    mutable.value = AudioPlaybackState(source.id,
                        if (current.playing) AudioPlaybackStatus.Playing else AudioPlaybackStatus.Completed,
                        (current.currentTime * 1000).toLong(), (current.duration * 1000).toLong())
                    if (!current.playing) break
                    delay(100)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Failed) }
        }
    }
    override fun resume(speed: Float) { player?.let { it.rate = speed; it.play(); mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Playing) } }
    override fun pause() { player?.pause(); mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Paused) }
    override fun setSpeed(speed: Float) { player?.rate = speed }
    override fun stop() { job?.cancel(); job = null; player?.stop(); player = null; mutable.value = AudioPlaybackState() }
    override fun release() { stop(); scope.cancel() }
}
