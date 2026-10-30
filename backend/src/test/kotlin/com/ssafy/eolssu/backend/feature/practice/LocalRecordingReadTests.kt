package com.ssafy.eolssu.backend.feature.practice

import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LocalRecordingReadTests.StorageConfig::class)
class LocalRecordingReadTests {
    @Value("\${local.server.port}") private var port = 0
    @Autowired private lateinit var storage: LocalRecordingStorage

    @Test fun `HTTP signed read supports ranges but unsigned reads and write methods cannot access audio`() {
        val file = Files.createTempFile("synthetic-recording-", ".m4a")
        val key = "recordings/9/12345678-1234-1234-1234-123456789abc.m4a"
        try {
            Files.write(file, byteArrayOf(1, 2, 3, 4))
            storage.put(key, file)
            val query = URI(storage.readUrl(key, Duration.ofMinutes(15))).rawQuery
            val url = URI("http://localhost:$port/api/recordings/read?$query")
            HttpClient.newHttpClient().use { client ->
                val result = client.send(HttpRequest.newBuilder(url).header("Cookie", "access_token=bad.jwt").GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                assertEquals(200, result.statusCode())
                assertContentEquals(byteArrayOf(1, 2, 3, 4), result.body())
                assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
                val ranged = client.send(HttpRequest.newBuilder(url).header("Range", "bytes=1-2").GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                assertEquals(206, ranged.statusCode())
                assertContentEquals(byteArrayOf(2, 3), ranged.body())
                val bad = URI(url.toString().substringBefore("signature=") + "signature=wrong")
                assertEquals(404, client.send(HttpRequest.newBuilder(bad).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                assertEquals(401, client.send(HttpRequest.newBuilder(url).DELETE().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                storage.delete(key)
                assertEquals(404, client.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
            }
        } finally {
            storage.delete(key)
            Files.deleteIfExists(file)
        }
    }

    @TestConfiguration
    class StorageConfig {
        @Bean fun localStorage() = LocalRecordingStorage(Files.createTempDirectory("private-recording-test-"), "http://backend:8080/api/recordings/read", "synthetic-secret-at-least-32-bytes-long")
    }
}
