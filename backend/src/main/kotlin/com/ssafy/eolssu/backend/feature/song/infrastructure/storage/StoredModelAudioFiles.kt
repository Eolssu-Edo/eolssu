package com.ssafy.eolssu.backend.feature.song.infrastructure.storage

import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioFiles
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioUnavailable
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Uses the already configured storage signer; it never accepts an arbitrary caller URL/key. */
class StoredModelAudioFiles(
    private val endpoint: URI?,
    private val sign: ((String, Duration) -> String)?,
    private val read: (URI) -> ByteArray,
) : ModelAudioFiles {
    override fun playbackUrl(audio: ModelAudio): String = signed(audio, audio.objectKey).toString()
    override fun performance(audio: ModelAudio): ByteArray = protected {
        val key = audio.performanceObjectKey ?: throw ModelAudioUnavailable()
        read(signed(audio, key))
    }

    private fun signed(audio: ModelAudio, key: String): URI = protected {
        if (!audio.hasValidMetadata() || endpoint == null || sign == null) throw ModelAudioUnavailable()
        val uri = URI(sign(key, Duration.ofMinutes(15)))
        if (uri.scheme != "https" || uri.host != endpoint.host || uri.port != endpoint.port ||
            uri.rawUserInfo != null || uri.rawFragment != null) throw ModelAudioUnavailable()
        uri
    }

    private fun <T> protected(action: () -> T): T = try { action() } catch (_: Exception) { throw ModelAudioUnavailable() }
}

/** The performance document is small; cap both response bytes and the whole request lifetime. */
class ModelPerformanceReader(private val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) : AutoCloseable {
    fun read(uri: URI): ByteArray {
        val output = ByteArrayOutputStream()
        val handler = HttpResponse.BodyHandler { _: HttpResponse.ResponseInfo ->
            HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArrayConsumer { part ->
                part.ifPresent { bytes ->
                    if (output.size() + bytes.size > MAX_BYTES) throw ModelAudioUnavailable()
                    output.write(bytes)
                }
            }) { output.toByteArray() }
        }
        val future = client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), handler)
        return try {
            val response = future.get(10, TimeUnit.SECONDS)
            if (response.statusCode() != 200 || response.body().isEmpty() ||
                !response.headers().firstValue("Content-Type").orElse("").substringBefore(';').equals("application/json", true))
                throw ModelAudioUnavailable()
            response.body()
        } catch (_: Exception) {
            future.cancel(true)
            throw ModelAudioUnavailable()
        }
    }
    override fun close() = client.close()
    companion object { const val MAX_BYTES = 2 * 1024 * 1024 }
}
