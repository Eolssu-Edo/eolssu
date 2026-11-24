@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.network.ApiJson
import com.ssafy.eolssu.core.network.ServerAddress
import eolssu.shared.generated.resources.Res
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** 디코딩한 장단 소리와 manifest가 정한 길이(44.1kHz frame). */
internal class DecodedJangdanSample(val buffer: AudioBuffer, val frames: Int)

/**
 * 앱 전체가 함께 쓰는 장단 소리. 한 번 받아 디코딩한 소리는 다른 연습 화면에서도 다시 받지 않는다.
 * 서버가 비공개 R2 서명 주소로 넘기므로 매번 주소가 달라 브라우저 HTTP 캐시에 기대지 않는다.
 * 받은 M4A는 manifest의 SHA-256(SRI)과 크기가 맞을 때만 쓴다.
 */
internal class WebJangdanSampleCache(
    private val server: ServerAddress,
    private val manifest: suspend () -> ByteArray = { Res.readBytes("files/jangdan/manifest.json") },
) {
    @Serializable
    private data class Manifest(val assets: List<Asset>)

    @Serializable
    private data class Asset(val file: String, val sha256: String, val bytes: Int, val frames: Int)

    private val mutex = Mutex()
    private var assets: Map<String, Asset>? = null
    private val decoded = mutableMapOf<JangdanSample, DecodedJangdanSample>()
    private var decoder: OfflineAudioContext? = null

    suspend fun sampleFor(sample: JangdanSample): DecodedJangdanSample = mutex.withLock {
        decoded[sample]?.let { return@withLock it }
        val asset = assetFor(sample)
        val url = "${server.baseUrl.trimEnd('/')}/api/jangdan-samples/${sample.file}/${asset.sha256}"
        val response = fetch(url, integrityRequest(jangdanSampleIntegrity(asset.sha256))).awaitJs()
        check(response.ok) { "Jangdan sample unavailable: ${response.status}" }
        val data = response.arrayBuffer().awaitJs()
        check(arrayBufferBytes(data) == asset.bytes) { "Invalid jangdan sample" }
        // 44.1kHz 오프라인 문맥으로 디코딩하면 원본 frame 그대로 남고, 재생 문맥이 장치 rate로 바꿔 튼다.
        val context = decoder ?: OfflineAudioContext(1, 1, JANGDAN_SAMPLE_RATE.toDouble()).also { decoder = it }
        val buffer = context.decodeAudioData(data).awaitJs()
        check(buffer.sampleRate == JANGDAN_SAMPLE_RATE.toDouble() && buffer.length > 0) { "Unexpected jangdan PCM" }
        // 브라우저가 컨테이너의 앞뒤 여백을 덜 잘라도 manifest 길이까지만 튼다.
        DecodedJangdanSample(buffer, minOf(asset.frames, buffer.length)).also { decoded[sample] = it }
    }

    private suspend fun assetFor(sample: JangdanSample): Asset {
        val byName = assets ?: ApiJson.decodeFromString<Manifest>(manifest().decodeToString()).assets.let { rows ->
            require(rows.all { it.bytes in 1..128 * 1024 && it.frames in 1..88_200 && SHA256.matches(it.sha256) })
            rows.associateBy { it.file }.also { mapping ->
                require(mapping.size == rows.size)
                assets = mapping
            }
        }
        return checkNotNull(byName["${sample.file}.m4a"])
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/** manifest의 16진수 SHA-256을 fetch `integrity`가 받는 `sha256-<base64>`로 바꾼다. */
internal fun jangdanSampleIntegrity(sha256Hex: String): String = "sha256-" + Base64.encode(sha256Hex.hexToByteArray())

/** JS Promise를 기다린다. 거절되면 예외로 던진다. */
internal suspend fun <T : JsAny?> Promise<T>.awaitJs(): T = suspendCancellableCoroutine { continuation ->
    then(
        { value -> continuation.resume(value); null },
        { error -> continuation.resumeWithException(IllegalStateException("JS promise rejected: $error")); null },
    )
}
