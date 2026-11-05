package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ExecutionException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class RecognitionFailure(val reason: String) : RuntimeException("Recognition failed: $reason")

fun interface RecognitionClient {
    fun recognize(request: JsonNode): JsonNode
    val available: Boolean get()=true
}

/** 내부 omr 호출. HTTP 자동 재시도 없이 SHT-14의 사용자 재시도만 허용한다. */
class HttpRecognitionClient(
    private val baseUrl: String,
    private val json: JsonMapper,
    private val timeout: Duration = Duration.ofSeconds(630),
) : RecognitionClient {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    override val available: Boolean get()=baseUrl.isNotBlank()
    override fun recognize(request: JsonNode): JsonNode {
        if(!available) throw RecognitionFailure("SERVER_ERROR")
        try {
            val future=client.sendAsync(HttpRequest.newBuilder(URI(baseUrl.trimEnd('/')+"/v1/recognitions"))
                .timeout(timeout).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(request))).build(),HttpResponse.BodyHandlers.ofByteArray())
            val response=try { future.get(timeout.toMillis(),TimeUnit.MILLISECONDS) } finally { if(!future.isDone) future.cancel(true) }
            val bytes=response.body()
            if(response.statusCode()!=200 || bytes.size>MAX_RESPONSE_BYTES) throw RecognitionFailure("SERVER_ERROR")
            val result=json.readTree(bytes)
            if(!result.isObject || result.path("conversionId")!=request.path("conversionId") ||
                result.path("status").asString() !in listOf("DONE","FAILED")) throw RecognitionFailure("SERVER_ERROR")
            return result
        } catch(e: RecognitionFailure) { throw e
        } catch(_: TimeoutException) { throw RecognitionFailure("TIMEOUT")
        } catch(e: ExecutionException) { throw RecognitionFailure(if(e.cause is HttpTimeoutException) "TIMEOUT" else "SERVER_ERROR")
        } catch(_: HttpTimeoutException) { throw RecognitionFailure("TIMEOUT")
        } catch(_: InterruptedException) { Thread.currentThread().interrupt(); throw RecognitionFailure("SERVER_ERROR")
        } catch(_: Exception) { throw RecognitionFailure("SERVER_ERROR") }
    }
    companion object { const val MAX_RESPONSE_BYTES=8*1024*1024 }
}
