@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.audio

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Float32Array
import org.khronos.webgl.toFloatArray

/** 브라우저가 마이크를 열지 못한 이유. [name]은 DOMException 이름(`NotAllowedError`·`NotFoundError` 등)이다. */
class WebMicException(val name: String) : IllegalStateException("Microphone unavailable: $name")

/** [browserNanos]와 같은 시계(`performance.now()`, 페이지를 연 뒤 흐른 시간)의 ns. 녹음 입력 시각과 관측 시각이 쓴다. */
internal fun browserNanos(): Long = (performanceNowMs() * 1_000_000.0).toLong()

/**
 * getUserMedia → AudioContext → AudioWorklet으로 마이크를 연다. 브라우저의 에코 제거·잡음 억제·자동 이득은 악기 소리를 깎으므로 끈다
 * (브라우저가 무시할 수 있다). AudioWorklet은 1024샘플 묶음을 메인 스레드로 보내고, 묶음 끝의 입력 시각은
 * `getOutputTimestamp()`로 오디오 시계를 페이지 시계에 맞춰 출력 지연만큼 당겨 잡는다(지원하지 않으면 받은 시각).
 */
internal object BrowserMicOpener : WebMicOpener {
    override suspend fun open(onChunk: (FloatArray, Long) -> Unit, onStopped: () -> Unit): WebMicInput =
        suspendCancellableCoroutine { continuation ->
            lateinit var handle: BrowserMicHandle
            handle = openBrowserMic(
                onReady = { rate ->
                    if (continuation.isActive) continuation.resume(BrowserMic(handle, rate)) else closeBrowserMic(handle)
                },
                onFailed = { name -> if (continuation.isActive) continuation.resumeWithException(WebMicException(name)) },
                onChunk = { data, endMs -> onChunk(data.toFloatArray(), (endMs * 1_000_000.0).toLong()) },
                onStopped = onStopped,
            )
            continuation.invokeOnCancellation { closeBrowserMic(handle) }
        }
}

private class BrowserMic(private val handle: BrowserMicHandle, override val sampleRate: Int) : WebMicInput {
    override val live: Boolean get() = browserMicLive(handle)
    override fun connect(on: Boolean) = connectBrowserMic(handle, on)
    override fun close() = closeBrowserMic(handle)
}

/** JS 쪽 마이크 객체. Kotlin은 속을 보지 않고 아래 함수에 넘기기만 한다. */
internal external interface BrowserMicHandle : JsAny

private fun performanceNowMs(): Double = js("performance.now()")

private fun connectBrowserMic(handle: BrowserMicHandle, on: Boolean): Unit = js("handle.connect(on)")

private fun closeBrowserMic(handle: BrowserMicHandle): Unit = js("handle.close()")

private fun browserMicLive(handle: BrowserMicHandle): Boolean = js("handle.live()")

// 워크릿 코드는 Blob 주소로 올린다(별도 정적 파일 없이 JS·Wasm 빌드가 같이 쓴다). 문자열에 '$'를 쓰지 않는다(Kotlin 템플릿).
private fun openBrowserMic(
    onReady: (Int) -> Unit,
    onFailed: (String) -> Unit,
    onChunk: (Float32Array, Double) -> Unit,
    onStopped: () -> Unit,
): BrowserMicHandle = js(
    """(function () {
  var h = { closed: false, ready: false, connected: false, stream: null, ctx: null, src: null, node: null };
  function release() {
    if (h.closed) return;
    h.closed = true;
    try { if (h.src && h.node && h.connected) h.src.disconnect(h.node); } catch (e) {}
    try { if (h.node) { h.node.port.onmessage = null; h.node.disconnect(); } } catch (e) {}
    try { if (h.stream) h.stream.getTracks().forEach(function (t) { t.stop(); }); } catch (e) {}
    try { if (h.ctx) { h.ctx.onstatechange = null; if (h.ctx.state !== 'closed') h.ctx.close(); } } catch (e) {}
  }
  function fail(error) {
    if (h.closed) return;
    release();
    onFailed(error && error.name ? String(error.name) : String(error));
  }
  function stopped() { if (!h.closed && h.ready) onStopped(); }
  h.close = release;
  h.live = function () {
    if (h.closed || !h.ready || h.ctx.state !== 'running') return false;
    return h.stream.getAudioTracks().some(function (t) { return t.readyState === 'live'; });
  };
  h.connect = function (on) {
    if (h.closed || !h.ready) return;
    if (on && !h.connected) { h.src.connect(h.node); h.connected = true; }
    else if (!on && h.connected) { h.src.disconnect(h.node); h.connected = false; }
  };
  var media = navigator.mediaDevices;
  var Context = window.AudioContext || window.webkitAudioContext;
  if (!media || !media.getUserMedia || !Context || typeof AudioWorkletNode === 'undefined') {
    setTimeout(function () { fail({ name: 'NotSupportedError' }); }, 0);
    return h;
  }
  media.getUserMedia({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: 1 } })
    .then(function (stream) {
      if (h.closed) { stream.getTracks().forEach(function (t) { t.stop(); }); return; }
      h.stream = stream;
      stream.getAudioTracks().forEach(function (t) { t.addEventListener('ended', stopped); });
      var ctx = new Context({ latencyHint: 'interactive' });
      h.ctx = ctx;
      var code = 'class EolssuCapture extends AudioWorkletProcessor {' +
        'constructor() { super(); this.b = new Float32Array(1024); this.n = 0; this.f = 0; }' +
        'process(inputs) { var c = inputs[0] && inputs[0][0]; if (c) { for (var i = 0; i < c.length; i++) {' +
        'if (this.n === 0) this.f = currentFrame + i; this.b[this.n++] = c[i];' +
        'if (this.n === 1024) { this.port.postMessage({ f: this.f, d: this.b }, [this.b.buffer]); this.b = new Float32Array(1024); this.n = 0; }' +
        '} } return true; } }' +
        'registerProcessor("eolssu-capture", EolssuCapture);';
      var url = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
      return ctx.audioWorklet.addModule(url).then(function () {
        URL.revokeObjectURL(url);
        if (h.closed) return;
        h.src = ctx.createMediaStreamSource(stream);
        var node = new AudioWorkletNode(ctx, 'eolssu-capture', {
          numberOfInputs: 1, numberOfOutputs: 1, outputChannelCount: [1],
          channelCount: 1, channelCountMode: 'explicit', channelInterpretation: 'speakers'
        });
        h.node = node;
        node.port.onmessage = function (event) {
          if (h.closed) return;
          var data = event.data.d;
          var now = performance.now();
          var end = now;
          var stamp = typeof ctx.getOutputTimestamp === 'function' ? ctx.getOutputTimestamp() : null;
          if (stamp && stamp.contextTime > 0 && stamp.performanceTime > 0) {
            var mapped = stamp.performanceTime + ((event.data.f + data.length) / ctx.sampleRate - stamp.contextTime) * 1000;
            end = Math.min(now, mapped - ((ctx.outputLatency || 0) + (ctx.baseLatency || 0)) * 1000);
          }
          onChunk(data, end);
        };
        node.connect(ctx.destination);
        ctx.onstatechange = function () { if (ctx.state !== 'running') stopped(); };
        return ctx.resume().then(function () {
          if (h.closed) return;
          if (ctx.state !== 'running') { fail({ name: 'NotAllowedError' }); return; }
          h.ready = true;
          onReady(Math.round(ctx.sampleRate));
        });
      });
    })
    .catch(fail);
  return h;
})()"""
)

/** 브라우저의 마이크 권한 상태(`granted`·`denied`·`prompt`)를 한 번 읽고 바뀔 때마다 알린다. 묻지 못하는 브라우저는 알리지 않는다. */
internal fun watchBrowserMicPermission(onState: (String) -> Unit): Unit = js(
    """(function () {
  try {
    if (!navigator.permissions || !navigator.permissions.query) return;
    navigator.permissions.query({ name: 'microphone' }).then(function (status) {
      onState(String(status.state));
      status.onchange = function () { onState(String(status.state)); };
    }).catch(function () {});
  } catch (e) {}
})()"""
)

/** 마이크를 한 번 열어 권한 창을 띄우고 바로 닫는다. 허용되면 true. 브라우저가 이미 막았으면 창 없이 false다. */
internal suspend fun requestBrowserMic(): Boolean = suspendCancellableCoroutine { continuation ->
    askBrowserMic { allowed -> if (continuation.isActive) continuation.resume(allowed) }
}

private fun askBrowserMic(done: (Boolean) -> Unit): Unit = js(
    """(function () {
  var media = navigator.mediaDevices;
  if (!media || !media.getUserMedia) { setTimeout(function () { done(false); }, 0); return; }
  media.getUserMedia({ audio: true }).then(function (stream) {
    stream.getTracks().forEach(function (t) { t.stop(); });
    done(true);
  }).catch(function () { done(false); });
})()"""
)
