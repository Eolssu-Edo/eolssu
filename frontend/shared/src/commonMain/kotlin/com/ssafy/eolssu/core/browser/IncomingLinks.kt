package com.ssafy.eolssu.core.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 밖에서 앱을 연 주소(App Links). 간편 로그인을 마친 브라우저가 복귀 주소로 앱을 다시 열 때 쓴다.
 * 진입점(Android `MainActivity`)이 [deliver]로 넣고, 받을 화면이 [take]로 한 번만 꺼낸다.
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
