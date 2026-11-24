@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.audio

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsNumber
import kotlin.js.Promise
import kotlin.js.js
import org.khronos.webgl.ArrayBuffer

// JS·Wasm이 함께 쓰는 Web Audio API의 필요한 부분만 선언한다.

internal open external class AudioNode : JsAny {
    fun connect(destination: AudioNode): AudioNode
    fun disconnect()
}

internal external class AudioParam : JsAny {
    var value: Double
    fun setValueAtTime(value: Double, startTime: Double): AudioParam
    fun linearRampToValueAtTime(value: Double, endTime: Double): AudioParam
}

internal external class AudioBuffer : JsAny {
    val length: Int
    val sampleRate: Double
}

internal external class GainNode : AudioNode {
    val gain: AudioParam
}

internal external class AudioBufferSourceNode : AudioNode {
    var buffer: AudioBuffer?
    fun start(`when`: Double, offset: Double, duration: Double)
    fun stop()
}

internal external interface AudioTimestamp : JsAny {
    val contextTime: Double
    val performanceTime: Double
}

internal external class AudioContext : JsAny {
    val currentTime: Double
    /** "suspended"·"running"·"closed", Safari는 통화 같은 중단에 "interrupted"도 쓴다. */
    val state: String
    val baseLatency: JsNumber?
    val outputLatency: JsNumber?
    val destination: AudioNode
    fun resume(): Promise<JsAny?>
    fun `suspend`(): Promise<JsAny?>
    fun close(): Promise<JsAny?>
    fun createBufferSource(): AudioBufferSourceNode
    fun createGain(): GainNode
    fun getOutputTimestamp(): AudioTimestamp
}

internal external class OfflineAudioContext(numberOfChannels: Int, length: Int, sampleRate: Double) : JsAny {
    fun decodeAudioData(audioData: ArrayBuffer): Promise<AudioBuffer>
}

internal external interface FetchResponse : JsAny {
    val ok: Boolean
    val status: Int
    fun arrayBuffer(): Promise<ArrayBuffer>
}

internal external fun fetch(input: String, init: JsAny): Promise<FetchResponse>

/** 받은 바이트의 SHA-256이 [integrity](SRI 형식)와 다르면 브라우저가 fetch를 거절한다. */
internal fun integrityRequest(integrity: String): JsAny = js("({ integrity: integrity })")

internal fun performanceNow(): Double = js("performance.now()")

/** kotlinx-browser의 `Document`에는 Page Visibility API(`document.hidden`)가 선언되어 있지 않다. */
internal fun pageHidden(): Boolean = js("document.hidden === true")

internal fun arrayBufferBytes(buffer: ArrayBuffer): Int = js("buffer.byteLength")

/**
 * 사용자 제스처(누름·키 입력)마다 [onGesture]를 부른다. 캡처 단계에서 들으므로 화면이 이벤트를 막아도 받는다.
 * 돌려준 값을 [removeListeners]에 넘기면 해제한다.
 */
internal fun listenUserGestures(onGesture: () -> Unit): JsAny = js("""(() => {
    const types = ['pointerdown', 'pointerup', 'mousedown', 'touchend', 'keydown', 'click'];
    const listener = () => onGesture();
    types.forEach((type) => window.addEventListener(type, listener, true));
    return () => types.forEach((type) => window.removeEventListener(type, listener, true));
})()""")

internal fun removeListeners(dispose: JsAny): Unit = js("dispose()")
