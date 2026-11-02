package com.ssafy.eolssu.platform.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation

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
        recording = session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null,
        ) && session.setActive(true, error = null)
        return recording
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        deactivate()
    }

    /** 녹음 중이면 false: 재생기는 녹음을 끊지 않고 중단 상태로 둔다. */
    fun startPlayback(): Boolean =
        !recording && session.setCategory(AVAudioSessionCategoryPlayback, error = null) && session.setActive(true, error = null)

    fun stopPlayback() {
        if (!recording) deactivate()
    }

    private fun deactivate() {
        session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }
}
