package com.ssafy.eolssu.platform.permission

import com.ssafy.eolssu.core.audio.MicPermission
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioApplicationRecordPermissionUndetermined
import kotlin.coroutines.resume

/**
 * 기기의 마이크 권한(`AVAudioApplication`, iOS 17+). 권한 창은 아직 묻지 않았을 때만 뜨고,
 * 한 번 거부하면 iOS가 다시 묻지 않으므로 설정 앱에서 바꿔야 한다. 권한 설명은 `Info.plist`의 `NSMicrophoneUsageDescription`이다.
 */
class IosMicPermission : MicPermission {
    override fun isGranted(): Boolean =
        AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionGranted

    override suspend fun request(): Boolean {
        val permission = AVAudioApplication.sharedInstance.recordPermission
        if (permission != AVAudioApplicationRecordPermissionUndetermined) return permission == AVAudioApplicationRecordPermissionGranted
        return suspendCancellableCoroutine { continuation ->
            AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
    }
}
