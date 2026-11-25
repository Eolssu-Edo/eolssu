package com.ssafy.eolssu.platform.permission

import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.platform.audio.requestBrowserMic
import com.ssafy.eolssu.platform.audio.watchBrowserMicPermission

/**
 * 브라우저 마이크 권한(S15P31E102-1262). 허용 여부는 Permissions API로 읽어 두고, 묻기는 마이크를 한 번 열었다 닫는다.
 * 권한 상태를 알려 주지 않는 브라우저는 허용 전으로 보고 평가를 시작할 때마다 묻는다(허용을 기억한 브라우저는 창 없이 바로 허용).
 * 브라우저가 막은 권한은 주소창의 사이트 설정에서만 풀 수 있어 앱이 설정을 열지 못한다. 그래서 '다시 묻지 못함'으로 나누지 않고
 * 거부 안내를 띄운다.
 */
class WebMicPermission : MicPermission {
    private var granted = false

    init {
        watchBrowserMicPermission { state -> granted = state == "granted" }
    }

    override fun isGranted(): Boolean = granted

    override suspend fun request(): Boolean = requestBrowserMic().also { granted = it }
}
