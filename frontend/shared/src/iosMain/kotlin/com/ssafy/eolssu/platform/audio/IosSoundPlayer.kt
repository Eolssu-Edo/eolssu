package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.SoundPlayer
import eolssu.shared.generated.resources.Res
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

/** 리소스 파일을 메모리로 읽어 `AVAudioPlayer`로 튼다. 소리는 기기의 무음 스위치를 따른다(기본 오디오 세션). */
class IosSoundPlayer : SoundPlayer {
    private var player: AVAudioPlayer? = null

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun play(path: String): Boolean {
        stop()
        val bytes = try {
            Res.readBytes(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        // 읽는 동안 창을 닫았으면 틀지 않는다.
        currentCoroutineContext().ensureActive()
        val audioPlayer = AVAudioPlayer(data = bytes.toNSData(), error = null)
        if (!audioPlayer.prepareToPlay() || !audioPlayer.play()) return false
        player = audioPlayer
        return true
    }

    override fun stop() {
        player?.stop()
        player = null
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
