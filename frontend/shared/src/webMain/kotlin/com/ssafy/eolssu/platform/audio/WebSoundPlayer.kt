package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.SoundPlayer
import eolssu.shared.generated.resources.Res
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event

/**
 * 리소스 주소(`Res.getUri`)를 `<audio>`로 튼다. JS·Wasm이 함께 쓴다.
 * 브라우저는 페이지를 한 번도 누르지 않았으면 소리를 막는다. 막히면 false를 돌려주고 그림만 움직인다.
 */
class WebSoundPlayer(
    // 테스트는 네트워크 요청 없이 재생할 수 없는 주소를 넣는다. 없는 파일을 요청하면 Karma 서버의 404 경고가
    // 늦게 도착해 다른 테스트의 실패로 붙는다(S15P31E102-1285).
    private val uriOf: (String) -> String = { Res.getUri(it) },
) : SoundPlayer {
    private var audio: HTMLAudioElement? = null

    @OptIn(ExperimentalWasmJsInterop::class)
    override suspend fun play(path: String): Boolean {
        stop()
        val element = document.createElement("audio") as HTMLAudioElement
        element.src = uriOf(path)
        audio = element
        // 'playing'(소리가 실제로 나기 시작함)을 기다린다. 파일을 못 읽거나('error') 브라우저가 막으면(play() 거절) 실패다.
        val started = suspendCancellableCoroutine { continuation ->
            val finish = { ok: Boolean -> if (continuation.isActive) continuation.resume(ok) }
            element.addEventListener("playing", { _: Event -> finish(true) })
            element.addEventListener("error", { _: Event -> finish(false) })
            continuation.invokeOnCancellation { element.pause() }
            element.play().catch {
                finish(false)
                null
            }
        }
        if (!started && audio === element) audio = null
        return started
    }

    override fun stop() {
        val current = audio ?: return
        audio = null
        current.pause()
        current.removeAttribute("src")
    }
}
