package com.ssafy.eolssu.backend.feature.practice.infrastructure.storage

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.deleteIfExists
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** 실제 R2 대신 로컬 HTTP 서버를 S3 엔드포인트로 두고 요청 모양을 확인한다. 실제 버킷 확인은 README의 수동 검증 절차다. */
class S3RecordingStorageTests {
    private class Received(val method: String, val path: String, val body: ByteArray, val contentType: String?, val authorization: String?)

    private val received = CopyOnWriteArrayList<Received>()
    private var status = 200
    @Volatile private var delayMillis = 0L
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            Thread.sleep(delayMillis)
            val raw = exchange.requestBody.readAllBytes()
            // http 엔드포인트에서는 SDK가 본문을 서명 청크(aws-chunked)로 보낸다. https(R2)에서는 그대로 보낸다.
            val body = if (exchange.requestHeaders.getFirst("x-amz-content-sha256").orEmpty().startsWith("STREAMING-")) decodeChunks(raw) else raw
            received += Received(
                exchange.requestMethod, exchange.requestURI.rawPath, body,
                exchange.requestHeaders.getFirst("Content-Type"), exchange.requestHeaders.getFirst("Authorization"),
            )
            exchange.sendResponseHeaders(if (exchange.requestMethod == "DELETE" && status == 200) 204 else status, -1)
            exchange.close()
        }
        start()
    }
    private val properties = RecordingStorageProperties(
        endpoint = "http://127.0.0.1:${server.address.port}", bucket = "eolssu-recordings",
        accessKeyId = "test-access-key", secretAccessKey = "test-secret-key",
    )
    private val storage = S3RecordingStorage.create(properties, maxAttempts = 1)
    private val file = Files.createTempFile("recording-", ".m4a").also { Files.write(it, byteArrayOf(1, 2, 3)) }

    @AfterEach
    fun stop() {
        server.stop(0)
        file.deleteIfExists()
    }

    private fun decodeChunks(raw: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var at = 0
        while (true) {
            val headerEnd = (at until raw.size - 1).first { raw[it] == '\r'.code.toByte() && raw[it + 1] == '\n'.code.toByte() }
            val size = String(raw, at, headerEnd - at).substringBefore(';').toInt(16)
            if (size == 0) return out.toByteArray()
            out.write(raw, headerEnd + 2, size)
            at = headerEnd + 2 + size + 2
        }
    }

    @Test
    fun `puts and deletes the object by key in the configured bucket with a SigV4 signature`() {
        storage.put("recordings/501/a.m4a", file)
        storage.delete("recordings/501/a.m4a")

        assertEquals(listOf("PUT", "DELETE"), received.map { it.method })
        assertEquals(listOf("/eolssu-recordings/recordings/501/a.m4a"), received.map { it.path }.distinct())
        assertContentEquals(byteArrayOf(1, 2, 3), received[0].body)
        assertEquals("audio/mp4", received[0].contentType)
        assertTrue(received.all { it.authorization.orEmpty().startsWith("AWS4-HMAC-SHA256 Credential=test-access-key/") })
    }

    @Test
    fun `reports write and delete failures as storage errors`() {
        status = 503
        assertFailsWith<RecordingStorageException> { storage.put("recordings/501/a.m4a", file) }
        assertFailsWith<RecordingStorageException> { storage.delete("recordings/501/a.m4a") }

        server.stop(0)
        assertFailsWith<RecordingStorageException> { storage.put("recordings/501/a.m4a", file) }
    }

    @Test
    fun `gives up on a slow storage within the call timeout`() {
        delayMillis = 1_000
        val quick = S3RecordingStorage.create(properties, maxAttempts = 3, callTimeout = Duration.ofMillis(200))

        val started = System.nanoTime()
        assertFailsWith<RecordingStorageException> { quick.put("recordings/501/a.m4a", file) }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(800))
    }

    @Test
    fun `the upload finishes before the 60 second proxy read timeout`() {
        // Nginx /api/의 proxy_read_timeout 기본값은 60초다. 저장소 상한 + ffprobe 상한(10초)이 그 안에 들어야
        // 느린 저장소가 Nginx HTML 504가 아니라 공통 오류 형식 503으로 끝난다.
        assertTrue(S3RecordingStorage.CALL_TIMEOUT + Duration.ofSeconds(10) < Duration.ofSeconds(60))
    }

    @Test
    fun `signs a read-only GET URL that expires after the given time without exposing the secret`() {
        val url = URI(storage.readUrl("recordings/501/a.m4a", Duration.ofMinutes(15)))
        val query = url.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals("127.0.0.1", url.host)
        assertEquals("/eolssu-recordings/recordings/501/a.m4a", url.rawPath)
        assertEquals("900", query["X-Amz-Expires"])
        assertEquals("AWS4-HMAC-SHA256", query["X-Amz-Algorithm"])
        assertEquals("host", query["X-Amz-SignedHeaders"])
        assertTrue(query.getValue("X-Amz-Credential").startsWith("test-access-key%2F"))
        assertTrue(query.getValue("X-Amz-Credential").contains("%2Fauto%2Fs3%2Faws4_request"))
        assertFalse(url.toString().contains("test-secret-key"))
        assertTrue(received.isEmpty()) // 서명은 네트워크 없이 만든다
    }

    @Test
    fun `is configured only when every connection value is present`() {
        assertTrue(properties.isConfigured)
        assertFalse(properties.copy(secretAccessKey = " ").isConfigured)
        assertFalse(RecordingStorageProperties().isConfigured)
    }
}
