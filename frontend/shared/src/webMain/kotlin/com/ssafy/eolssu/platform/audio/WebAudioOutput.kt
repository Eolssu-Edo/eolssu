@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.audio

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 앱 전체가 함께 쓰는 Web Audio 출력 문맥 하나(S15P31E102-1260). 장단 플레이어가 붙어 있는 동안만 깨어 있다.
 *
 * - 브라우저는 사용자 제스처 없이 소리를 막는다. 플레이어가 붙어 있는 동안(연습 화면) 누름·키 입력마다
 *   문맥을 만들거나 `resume()`한다. 화면 밖에서는 문맥을 만들지 않는다.
 * - Safari는 제스처 안에서 시작한 문맥에만 소리를 허락하고, 그 허락은 그 문맥에 남는다. 연습 화면은 탭이 가려지거나
 *   다른 화면으로 갈 때마다 플레이어를 놓았다가 같은 플레이어를 다시 쓰므로, 문맥을 닫지 않고 멈춰(`suspend`) 두었다가
 *   돌아와서 제스처 없이 이어 쓴다. 멈춘 문맥은 소리를 내지 않고 탭 소리 표시도 띄우지 않는다.
 */
internal class WebAudioOutput {
    private var context: AudioContext? = null
    private var users = 0
    private var gestures: JsAny? = null

    /** 플레이어가 붙는다. 붙어 있는 동안 사용자 제스처에서 출력을 연다. */
    fun attach() {
        users++
        if (gestures == null) gestures = listenUserGestures { if (users > 0) usable()?.let(::resume) }
    }

    /** 플레이어가 떨어진다. 마지막 플레이어가 떨어지면 제스처 듣기를 끝내고 문맥을 멈춘다. */
    fun detach() {
        if (users == 0) return
        users--
        if (users > 0) return
        gestures?.let(::removeListeners)
        gestures = null
        context?.let { current -> runCatching { current.`suspend`().then({ null }, { null }) } }
    }

    /** 재생에 쓸 문맥. 없거나 닫혔으면 새로 만들고, 멈춰 있으면 깨운다. 브라우저가 막고 있으면 상태가 "suspended"로 남는다. */
    fun start(): AudioContext? = usable()?.also(::resume)

    /**
     * 이미 연 문맥을 깨우고 실제로 돌기 시작할 때까지 잠깐 기다린다. 아직 없으면 만들지 않는다(첫 누름에서 만든다).
     * 멈추라는 요청이 아직 끝나지 않았어도 깨우기 요청이 그 뒤에 처리된다. 제스처가 없어 막혀 있으면 기다리다 넘긴다.
     */
    suspend fun awaitResumed(timeoutMs: Long = RESUME_WAIT_MS) {
        val current = context?.takeIf { it.state != "closed" } ?: return
        val resumed = runCatching { current.resume() }.getOrNull() ?: return
        withTimeoutOrNull(timeoutMs) {
            try {
                resumed.awaitJs()
            } catch (_: IllegalStateException) {
                null // 제스처가 없어 거절됨. 다음 누름에서 다시 깨운다.
            }
        }
    }

    private fun usable(): AudioContext? {
        context?.takeIf { it.state != "closed" }?.let { return it }
        return runCatching { AudioContext() }.getOrNull().also { context = it }
    }

    // 멈추라는 요청이 아직 끝나지 않았으면 state가 "running"으로 보이므로, 상태와 상관없이 깨운다.
    private fun resume(current: AudioContext) {
        runCatching { current.resume().then({ null }, { null }) }
    }

    private companion object {
        const val RESUME_WAIT_MS = 300L
    }
}
