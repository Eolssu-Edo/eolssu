package com.ssafy.eolssu.core.audio

/**
 * 기기(OS)·브라우저의 마이크 권한. 앱 안에서 고른 '허용'(`PracticeSettingsStore.micGranted`)과 별개다.
 * 구현이 없는 플랫폼은 등록하지 않고, 부르는 쪽은 권한이 있는 것으로 본다.
 */
interface MicPermission {
    fun isGranted(): Boolean

    /** OS 권한 창을 띄워 묻는다. 이미 허용돼 있으면 바로 true. */
    suspend fun request(): Boolean

    /** 사용자가 ‘다시 묻지 않음’을 골라 앱에서 권한 창을 다시 띄울 수 없는지(PRACTICE-01 ‘설정에서 허용하기’ 안내). 모르는 플랫폼은 false. */
    fun isPermanentlyDenied(): Boolean = false

    /** 기기의 앱 설정 화면을 연다. 열 수 없는 플랫폼은 false. */
    fun openSettings(): Boolean = false
}
