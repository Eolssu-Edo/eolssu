package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val PERFORMANCE_ENTRY = "performance.common.json"
private const val PERFORMANCE_SOURCE_HEADER = "X-Eolssu-Performance-Source-Sha256"
private const val MAX_PERFORMANCE_BYTES = 2 * 1024 * 1024

data class GeneratedSheetAudio(val m4a: ByteArray, val midi: ByteArray, val durationMs: Long, val performance: JsonNode? = null)

internal fun ByteArray.sheetAudioSha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
class PlaybackFailure(val reason: String, val details: JsonNode? = null): RuntimeException(reason)
fun interface PlaybackClient {
    fun generate(requestJson: String): GeneratedSheetAudio
    fun estimate(requestJson: String): Long = throw PlaybackFailure("SERVER_ERROR")
    val available: Boolean get()=true
}

/** Only backend-owned JSON is sent to the fixed internal renderer; the entire body has a deadline. */
class HttpPlaybackClient(private val baseUrl: String, private val timeout: Duration,private val json: JsonMapper): PlaybackClient {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val configured: Boolean get()=baseUrl.isNotBlank() && !timeout.isZero && !timeout.isNegative
    override val available: Boolean get()=configured && runCatching {
        client.send(HttpRequest.newBuilder(URI(baseUrl.trimEnd('/')+"/health")).timeout(Duration.ofSeconds(3)).GET().build(),
            HttpResponse.BodyHandlers.discarding()).statusCode()==200
    }.getOrDefault(false)
    override fun estimate(requestJson: String): Long {
        if(!configured) throw PlaybackFailure("SERVER_ERROR")
        try {
            val snapshot = json.readTree(requestJson)
            val sourceHash = sourceHash(snapshot)
            val request=HttpRequest.newBuilder(URI(baseUrl.trimEnd('/')+"/v1/estimate")).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/json").header(PERFORMANCE_SOURCE_HEADER, sourceHash).POST(HttpRequest.BodyPublishers.ofString(requestJson)).build()
            val future=client.sendAsync(request,HttpResponse.BodyHandler { LimitedBody(4096) })
            val response=try { future.get(15,TimeUnit.SECONDS) } finally { if(!future.isDone) future.cancel(true) }
            val result=json.readTree(response.body())
            if(response.statusCode()!=200) throw PlaybackFailure(result.path("errorCode").asString().takeIf { it in setOf("EXPORT_TOO_LONG","INVALID_REQUEST","TEMPO_UNRENDERABLE") } ?: "SERVER_ERROR", result.get("details"))
            val duration=result.path("durationMs")
            if(!duration.isIntegralNumber || duration.longValue() !in 1..600000) throw PlaybackFailure("SERVER_ERROR")
            return duration.longValue()
        } catch(e: PlaybackFailure) { throw e
        } catch(_: InterruptedException) { Thread.currentThread().interrupt(); throw PlaybackFailure("SERVER_ERROR")
        } catch(_: Exception) { throw PlaybackFailure("SERVER_ERROR") }
    }
    override fun generate(requestJson: String): GeneratedSheetAudio {
        if(!configured) throw PlaybackFailure("SERVER_ERROR")
        try {
            val snapshot = json.readTree(requestJson)
            val sourceHash = sourceHash(snapshot)
            val request=HttpRequest.newBuilder(URI(baseUrl.trimEnd('/')+"/v1/render")).timeout(timeout)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(requestJson))
            request.header(PERFORMANCE_SOURCE_HEADER, sourceHash)
            val future=client.sendAsync(request.build(),HttpResponse.BodyHandler { LimitedBody(17*1024*1024 + MAX_PERFORMANCE_BYTES) })
            val response=try { future.get(timeout.toMillis(),TimeUnit.MILLISECONDS) } finally { if(!future.isDone) future.cancel(true) }
            if(response.statusCode()==409) throw PlaybackFailure("BUSY")
            if(response.statusCode()!=200) {
                val code=runCatching { json.readTree(response.body()).path("errorCode").asString() }.getOrNull()
                throw PlaybackFailure(code?.takeIf { it in setOf("TIMEOUT","TOO_LARGE") } ?: "SERVER_ERROR")
            }
            if(response.headers().firstValue("Content-Type").orElse("").substringBefore(';')!="application/zip") throw PlaybackFailure("SERVER_ERROR")
            return decode(response.body(), snapshot, sourceHash)
        } catch(e: PlaybackFailure) { throw e
        } catch(_: TimeoutException) { throw PlaybackFailure("TIMEOUT")
        } catch(_: HttpTimeoutException) { throw PlaybackFailure("TIMEOUT")
        } catch(e: ExecutionException) { throw PlaybackFailure(if(e.cause is HttpTimeoutException) "TIMEOUT" else "SERVER_ERROR")
        } catch(_: InterruptedException) { Thread.currentThread().interrupt(); throw PlaybackFailure("SERVER_ERROR")
        } catch(_: Exception) { throw PlaybackFailure("SERVER_ERROR") }
    }

    private fun sourceHash(snapshot: JsonNode): String =
        if (snapshot.path("expressions").isObject)
            com.ssafy.eolssu.backend.feature.sheet.application.SheetExpressions(json).hash(json.writeValueAsString(snapshot.path("score")))
        else json.writeValueAsBytes(snapshot.path("score")).sheetAudioSha256()

    internal fun decode(bytes: ByteArray, snapshot: JsonNode? = null, sourceHash: String? = null): GeneratedSheetAudio {
        val files=mutableMapOf<String,ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                val limit = when (entry.name) {
                    "audio.m4a" -> 15 * 1024 * 1024
                    "score.mid" -> 1024 * 1024
                    "result.json" -> 4096
                    PERFORMANCE_ENTRY -> MAX_PERFORMANCE_BYTES
                    else -> throw PlaybackFailure("SERVER_ERROR")
                }
                if(entry.isDirectory || files.containsKey(entry.name)) throw PlaybackFailure("SERVER_ERROR")
                val payload=zip.readNBytes(limit+1)
                if(payload.isEmpty() || payload.size>limit) throw PlaybackFailure("TOO_LARGE")
                files[entry.name]=payload
            }
        }
        val m4a = files["audio.m4a"] ?: throw PlaybackFailure("SERVER_ERROR")
        val midi = files["score.mid"] ?: throw PlaybackFailure("SERVER_ERROR")
        val result = files["result.json"] ?: throw PlaybackFailure("SERVER_ERROR")
        if(m4a.size<12 || String(m4a,4,4,Charsets.US_ASCII)!="ftyp" || midi.size<14 || String(midi,0,4,Charsets.US_ASCII)!="MThd") throw PlaybackFailure("SERVER_ERROR")
        val duration=json.readTree(result).path("durationMs")
        if(!duration.isIntegralNumber || duration.longValue() !in 1..600000) throw PlaybackFailure("SERVER_ERROR")
        val performance = if (snapshot != null && sourceHash != null) {
            files[PERFORMANCE_ENTRY]?.let { payload ->
                val timeline = runCatching { json.readTree(payload) }.getOrNull()
                timeline?.takeIf { matchesPerformance(it, snapshot, sourceHash, m4a, duration.longValue()) }
            }
        } else null
        return GeneratedSheetAudio(m4a, midi, duration.longValue(), performance)
    }

    private fun matchesPerformance(timeline: JsonNode, snapshot: JsonNode, sourceHash: String, m4a: ByteArray, durationMs: Long): Boolean {
        val score = snapshot.path("score")
        val identity = score.path("score")
        val segments = timeline.path("segments")
        val duration = timeline.path("durationMs").asDouble(Double.NaN)
        return timeline.path("schema").asString() == "common-score-performance/v1" &&
            timeline.path("policy").asString() == "educational-preview/v1" &&
            timeline.path("sourceSha256").asString() == sourceHash &&
            timeline.path("scoreVersion").isIntegralNumber && timeline.path("scoreVersion") == identity.path("scoreVersion") &&
            timeline.path("sourceId").isString && timeline.path("sourceId") == identity.path("sourceId") &&
            timeline.path("instrument").isString && timeline.path("instrument") == identity.path("instrument") &&
            timeline.path("bpm").isNumber && timeline.path("bpm").asDouble() == snapshot.path("bpm").asDouble(Double.NaN) &&
            timeline.path("bpmUnit").isString && timeline.path("bpmUnit") == score.path("music").path("bpmUnit") &&
            timeline.path("audioSha256").asString() == m4a.sheetAudioSha256() &&
            timeline.path("durationMs").isNumber && duration > 0 && duration <= durationMs + 1.0 &&
            segments.isArray && !segments.isEmpty
    }
}

private class LimitedBody(private val limit: Int): HttpResponse.BodySubscriber<ByteArray> {
    private val result=CompletableFuture<ByteArray>()
    private val output=ByteArrayOutputStream()
    private lateinit var subscription: Flow.Subscription
    override fun getBody(): CompletionStage<ByteArray> = result
    override fun onSubscribe(value: Flow.Subscription) { subscription=value; value.request(1) }
    override fun onNext(items: List<ByteBuffer>) {
        for(buffer in items) {
            if(buffer.remaining()>limit-output.size()) { subscription.cancel(); result.completeExceptionally(PlaybackFailure("TOO_LARGE")); return }
            val bytes=ByteArray(buffer.remaining()); buffer.get(bytes); output.write(bytes)
        }
        subscription.request(1)
    }
    override fun onError(error: Throwable) { result.completeExceptionally(error) }
    override fun onComplete() { result.complete(output.toByteArray()) }
}
