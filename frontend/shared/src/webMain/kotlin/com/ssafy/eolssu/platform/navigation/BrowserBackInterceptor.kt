package com.ssafy.eolssu.platform.navigation

import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * 브라우저 뒤로 가기(`popstate`)를 화면 이동 그래프의 브라우저 연결보다 먼저 받아, [guard]에 확인할 화면이 있으면 막는다.
 * 막을 때는 그래프에 이벤트를 넘기지 않고 `history.forward()`로 주소를 되돌린 뒤 화면의 뒤로 가기 동작을 부른다.
 * 되돌리며 생기는 `popstate`도 그래프에 넘기지 않는다. 같은 대상(window)의 리스너 중 캡처 단계 리스너가 먼저 불린다.
 * 앱이 떠 있는 동안 한 번만 건다.
 */
internal fun installBrowserBackInterceptor(guard: BrowserBackGuard) {
    var restoring = false
    window.addEventListener(
        "popstate",
        { event: Event ->
            if (restoring) {
                restoring = false
                event.stopImmediatePropagation()
            } else {
                val onBack = guard.onBack
                if (onBack != null) {
                    event.stopImmediatePropagation()
                    restoring = true
                    window.history.forward()
                    onBack()
                }
            }
        },
        true,
    )
}
