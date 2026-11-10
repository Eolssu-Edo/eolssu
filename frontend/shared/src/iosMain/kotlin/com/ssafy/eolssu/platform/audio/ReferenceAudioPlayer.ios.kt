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
import platform.Foundation.*

@Composable
actual fun rememberReferenceAudioPlayer(): ReferenceAudioPlayer {
    val client = koinInject<HttpClient>()
    return remember(client) { IosReferenceAudioPlayer(client) }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosReferenceAudioPlayer(private val client: HttpClient) : ReferenceAudioPlayer {
    override val available = true
    private val mutable = MutableStateFlow(AudioPlaybackState())
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var positionJob: Job? = null
    private var player: AVAudioPlayer? = null
    override fun load(source: AudioSource, positionMs: Long, speed: Float) {
        stop()
        mutable.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading)
        job = scope.launch {
            try {
                val localFile = source.localFile
                val data = if (localFile == null) {
                    val response = client.get(source.url)
                    check(response.status.value == 200)
                    val bytes = response.body<ByteArray>()
                    bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                } else {
                    check(source.url.isEmpty())
                    checkNotNull(NSData.dataWithContentsOfURL(
                        NSURL.fileURLWithPath(localFile.path), options = NSDataReadingMappedIfSafe, error = null,
                    ))
                }
                ensureActive()
                val current = AVAudioPlayer(data = data, error = null)
                current.enableRate = true
                current.rate = speed
                current.currentTime = positionMs / 1000.0
                check(current.prepareToPlay())
                player = current
                check(current.play())
                mutable.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Playing,
                    (current.currentTime * 1000).toLong(), (current.duration * 1000).toLong())
                observePosition(current)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail() }
        }
    }
    override fun resume(speed: Float) {
        val current = player ?: return
        try {
            current.rate = speed
            check(current.play())
            mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Playing)
            observePosition(current)
        } catch (_: Exception) {
            // Stop the polling job before it can report a failed resume as normal completion.
            fail()
        }
    }
    private fun observePosition(current: AVAudioPlayer) {
        positionJob?.cancel()
        positionJob = scope.launch {
            try {
                while (isActive && player === current) {
                    if (mutable.value.status != AudioPlaybackStatus.Paused) {
                        mutable.value = mutable.value.copy(
                            status = if (current.playing) AudioPlaybackStatus.Playing else AudioPlaybackStatus.Completed,
                            positionMs = (current.currentTime * 1000).toLong(),
                            durationMs = (current.duration * 1000).toLong(),
                        )
                        if (!current.playing) break
                    }
                    delay(100)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail() }
        }
    }
    override fun pause() { player?.pause(); mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Paused) }
    override fun seekTo(positionMs: Long) {
        val current = player ?: return
        val target = mutable.value.referenceSeekTarget(positionMs) ?: return
        try {
            current.currentTime = target.positionMs / 1000.0
            mutable.value = target
        } catch (_: Exception) { fail() }
    }
    override fun setSpeed(speed: Float) { player?.rate = speed }
    override fun stop() {
        job?.cancel(); job = null
        positionJob?.cancel(); positionJob = null
        player?.stop(); player = null
        mutable.value = AudioPlaybackState()
    }
    override fun release() { stop(); scope.cancel() }
    private fun fail() {
        job?.cancel(); job = null
        positionJob?.cancel(); positionJob = null
        player?.stop(); player = null
        mutable.value = mutable.value.copy(status = AudioPlaybackStatus.Failed)
    }
}
