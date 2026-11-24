package com.ssafy.eolssu.platform.navigation

import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 새로고침·탭 닫기의 떠나기 확인(S15P31E102-1265). 실제로 페이지를 떠나지 않도록 `beforeunload`를 직접 보내
 * 기본 동작을 막았는지(브라우저가 확인 창을 띄울지)만 본다. 등록소에 아무것도 없으면 `popstate`도 그래프로 그대로 넘어가
 * 설치가 다른 테스트에 영향을 주지 않는다.
 */
class BrowserBackInterceptorTest {
    @Test
    fun leavingAsksOnlyWithUnsavedChangesAndTheAppCanSkipItOnce() {
        val guard = BrowserBackGuard()
        var unsaved = false
        val remove = guard.screens.watchUnsaved { unsaved }
        installBrowserBackInterceptor(guard)
        holdKarmaReloadCheck()
        try {
            assertFalse(leave(), "저장하지 않은 변경이 없으면 묻지 않는다")
            unsaved = true
            assertTrue(leave(), "저장하지 않은 변경이 있으면 묻는다")
            skipNextUnsavedChangesWarning()
            assertFalse(leave(), "앱이 스스로 다시 불러오기 직전 한 번은 건너뛴다")
            assertTrue(leave(), "건너뛰기는 한 번뿐이다")
        } finally {
            restoreKarmaReloadCheck()
            remove()
        }
    }

    private fun leave(): Boolean {
        val event = cancelableEvent("beforeunload")
        window.dispatchEvent(event)
        return event.defaultPrevented
    }
}

/** Wasm의 `kotlinx-browser`에는 `EventInit` 생성 함수가 없어 JS로 만든다. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelableEvent(type: String): Event = js("new Event(type, { cancelable: true })")

/**
 * Karma는 테스트 창의 `onbeforeunload`에 '테스트가 페이지를 새로 불러왔다' 오류 보고를 걸어 둔다.
 * 직접 보낸 `beforeunload`가 이것을 부르면 테스트가 모두 통과해도 실행이 실패로 끝나므로, 보내는 동안만 떼어 둔다.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun holdKarmaReloadCheck(): Unit =
    js("(window.__eolssuHeldBeforeUnload = window.onbeforeunload, window.onbeforeunload = null, undefined)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun restoreKarmaReloadCheck(): Unit =
    js("(window.onbeforeunload = window.__eolssuHeldBeforeUnload, window.__eolssuHeldBeforeUnload = undefined, undefined)")
