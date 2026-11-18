package com.ssafy.eolssu.platform.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategorySoloAmbient
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive

/**
 * 앱에 하나뿐인 `AVAudioSession`을 녹음기와 재생기가 나눠 쓴다. 세션을 끄면 그 위의 녹음도 멈추므로,
 * 녹음하는 동안에는 재생기가 세션을 켜거나 끄지 못한다. 모든 호출은 메인 스레드에서 한다.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosAudioSession {
    private val session get() = AVAudioSession.sharedInstance()
    private var recording = false

    fun startRecording(): Boolean {
        // 측정 모드는 음성용 자동 음량 조절·잡음 제거를 줄인다(Android의 VOICE_RECOGNITION과 같은 목적).
        // 녹음 중에도 메트로놈·안내음이 스피커로 나도록 PlayAndRecord + DefaultToSpeaker를 쓴다.
        // 실패해도 녹음 중으로 둔다. 부르는 쪽이 실패 뒤 [stopRecording]으로 세션을 기본값으로 되돌린다.
        recording = true
        return session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null,
        ) && session.setActive(true, error = null)
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        deactivate()
    }

    /** 지금 재생 세션을 쓰는 재생기 수. 마지막 재생기가 멈출 때만 세션을 끈다. */
    private var playbackOwners = 0

    /**
     * 재생 세션(Playback)을 켠다. 무음 스위치와 상관없이 소리가 난다. 녹음 중이면 false: 재생기는 녹음을 끊지 않고 중단 상태로 둔다.
     * 녹음이 끝나며 세션이 기본값으로 돌아갔을 수 있어 부를 때마다 범주를 다시 맞춘다. 성공하면 [stopPlayback]을 한 번 불러야 한다.
     */
    fun startPlayback(): Boolean {
        if (recording) return false
        if (!session.setCategory(AVAudioSessionCategoryPlayback, error = null) || !session.setActive(true, error = null)) return false
        playbackOwners++
        return true
    }

    fun stopPlayback() {
        if (playbackOwners > 0) playbackOwners--
        if (playbackOwners == 0 && !recording) deactivate()
    }

    /** 끈 뒤 기본 설정(SoloAmbient)으로 되돌려 측정 모드가 남지 않게 한다. */
    private fun deactivate() {
        session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        session.setCategory(AVAudioSessionCategorySoloAmbient, mode = AVAudioSessionModeDefault, options = 0uL, error = null)
    }
}
