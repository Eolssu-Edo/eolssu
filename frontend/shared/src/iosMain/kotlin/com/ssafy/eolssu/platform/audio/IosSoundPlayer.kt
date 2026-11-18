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
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject

/**
 * 리소스 파일을 메모리로 읽어 `AVAudioPlayer`로 튼다. 정간보 읽기의 음·시김새 예시음이라 '연주 듣기'처럼 재생 세션을 써서
 * 무음 모드에서도 들리게 하고, 다 들으면 세션을 놓아 다른 앱 소리를 되돌린다(S15P31E102-914).
 */
class IosSoundPlayer : SoundPlayer {
    private var player: AVAudioPlayer? = null
    private var sessionActive = false
    private val finished = Finished()

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
        audioPlayer.delegate = finished
        if (!audioPlayer.prepareToPlay()) return false
        // 녹음 중이면 세션을 바꾸지 못한다. 그때는 지금 세션 그대로 튼다.
        if (!sessionActive) sessionActive = IosAudioSession.startPlayback()
        if (!audioPlayer.play()) {
            releaseSession()
            return false
        }
        player = audioPlayer
        return true
    }

    override fun stop() {
        player?.stop()
        player = null
        releaseSession()
    }

    private fun releaseSession() {
        if (!sessionActive) return
        sessionActive = false
        IosAudioSession.stopPlayback()
    }

    private inner class Finished : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (player === this@IosSoundPlayer.player) {
                this@IosSoundPlayer.player = null
                releaseSession()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
