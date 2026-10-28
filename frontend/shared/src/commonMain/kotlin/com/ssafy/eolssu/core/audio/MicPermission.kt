package com.ssafy.eolssu.core.audio

/**
 * 기기(OS)의 마이크 권한. 앱 안에서 고른 '허용'(`PracticeSettingsStore.micGranted`)과 별개다.
 * 구현이 없는 플랫폼(현재 iOS·Web)은 등록하지 않고, 부르는 쪽은 권한이 있는 것으로 본다.
 */
interface MicPermission {
    fun isGranted(): Boolean

    /** OS 권한 창을 띄워 묻는다. 이미 허용돼 있으면 바로 true. */
    suspend fun request(): Boolean
}
