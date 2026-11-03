package com.ssafy.eolssu.core.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 밖에서 앱으로 돌아온 주소(Android App Links, iOS 인증 세션의 앱 전용 주소). 간편 로그인을 마친 브라우저가 복귀 주소로 돌아올 때 쓴다.
 * 진입점(Android `MainActivity`, iOS 인증 세션)이 [deliver]로 넣고, 받을 화면이 [take]로 한 번만 꺼낸다.
 * 주소에는 인가 코드가 들어 있으므로 로그·화면 이동 경로에 남기지 않는다.
 */
class IncomingLinks {
    private val _pending = MutableStateFlow<String?>(null)

    /** 아직 아무도 꺼내지 않은 주소. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun deliver(uri: String) {
        _pending.value = uri
    }

    /** [uri]가 아직 남아 있으면 꺼내고 true. 다른 쪽이 먼저 꺼냈으면 false. */
    fun take(uri: String): Boolean = _pending.compareAndSet(uri, null)
}
