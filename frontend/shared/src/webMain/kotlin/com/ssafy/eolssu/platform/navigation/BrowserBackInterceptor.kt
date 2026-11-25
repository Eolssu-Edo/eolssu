package com.ssafy.eolssu.platform.navigation

import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import kotlinx.browser.window
import org.w3c.dom.BeforeUnloadEvent
import org.w3c.dom.events.Event

/**
 * 브라우저 뒤로 가기(`popstate`)를 화면 이동 그래프의 브라우저 연결보다 먼저 받아, [guard]에 확인할 화면이 있으면 막는다.
 * 막을 때는 그래프에 이벤트를 넘기지 않고 `history.forward()`로 주소를 되돌린 뒤 화면의 뒤로 가기 동작을 부른다.
 * 되돌리며 생기는 `popstate`도 그래프에 넘기지 않는다. 같은 대상(window)의 리스너 중 캡처 단계 리스너가 먼저 불린다.
 * 화면의 동작은 주소를 되돌린 뒤에 불러, 그 동작이 화면을 닫아도 그래프가 맞춘 주소를 되돌리기가 덮지 않게 한다.
 *
 * 앞 기록이 없으면(같은 주소로 다시 이동했거나 주소창에서 `#경로`를 바꿨으면) `history.forward()`가 아무 일도 하지 않는다.
 * 그때는 [RESTORE_TIMEOUT_MS] 뒤에 주소는 그대로 두고 화면의 동작만 부른다. 기다리는 동작이 남아 다음 브라우저 이동을
 * 잘못 막는 일을 없앤다. 주소창에서 다른 `#경로`를 넣은 경우에는 화면이 다음 이동 전까지 주소와 다를 수 있다.
 *
 * 새로고침·탭 닫기(`beforeunload`)는 저장하지 않은 변경이 있을 때만 브라우저의 떠나기 확인을 띄운다.
 * 앱이 스스로 다시 불러올 때는 [skipNextUnsavedChangesWarning]으로 한 번 건너뛴다.
 * 앱이 떠 있는 동안 한 번만 건다.
 */
internal fun installBrowserBackInterceptor(guard: BrowserBackGuard) {
    var restoring: (() -> Unit)? = null
    var fallback: Int? = null
    window.addEventListener(
        "popstate",
        { event: Event ->
            val pending = restoring
            if (pending != null) {
                restoring = null
                fallback?.let(::cancelTimeout)
                fallback = null
                event.stopImmediatePropagation()
                pending()
            } else {
                val onBack = guard.current()
                if (onBack != null) {
                    event.stopImmediatePropagation()
                    restoring = onBack
                    window.history.forward()
                    fallback = requestTimeout({
                        fallback = null
                        val late = restoring
                        restoring = null
                        late?.invoke()
                    }, RESTORE_TIMEOUT_MS)
                }
            }
        },
        true,
    )
    window.addEventListener("beforeunload", { event: Event ->
        if (skipUnsavedWarning) {
            skipUnsavedWarning = false
        } else if (guard.screens.hasUnsavedChanges()) {
            // 브라우저가 정한 문구로 묻는다. 예전 브라우저는 returnValue가 있어야 묻는다.
            event.preventDefault()
            (event as? BeforeUnloadEvent)?.returnValue = ""
        }
    })
}

/**
 * 다음 한 번의 떠나기(`beforeunload`)에서 미저장 확인을 건너뛴다. 사용자가 아니라 앱이 스스로 페이지를 다시 불러올 때
 * (다른 탭의 계정 변경으로 `location.reload()` 하기 직전, S15P31E102-1264) 바로 앞에서 부른다. 한 번 쓰면 다시 묻는다.
 */
internal fun skipNextUnsavedChangesWarning() {
    skipUnsavedWarning = true
}

private var skipUnsavedWarning = false

/** `history.forward()` 뒤 `popstate`를 기다리는 시간. 보통 몇 ms 안에 오며, 느린 기기에서도 겹치지 않게 넉넉히 둔다. */
private const val RESTORE_TIMEOUT_MS = 1000

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestTimeout(action: () -> Unit, delayMs: Int): Int = js("window.setTimeout(action, delayMs)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelTimeout(handle: Int): Unit = js("window.clearTimeout(handle)")
