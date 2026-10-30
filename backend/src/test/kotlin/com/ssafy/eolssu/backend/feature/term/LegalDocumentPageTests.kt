package com.ssafy.eolssu.backend.feature.term

import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import com.ssafy.eolssu.backend.feature.term.api.LegalDocumentPageController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
@Import(LegalDocumentPageTests.LocalStorage::class)
class LegalDocumentPageTests {
    @Value("\${local.server.port}") private var port = 0

    @Test fun `only versioned allowlisted documents are public and render complete UTF-8 HTML`() {
        HttpClient.newHttpClient().use { client ->
            for (path in LegalDocumentPageController.PUBLIC_PATHS) {
                val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                    .header("Cookie", "access_token=invalid.jwt").GET().build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(200, response.statusCode(), path)
                assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"))
                assertTrue(response.body().contains("이도(Edo)"))
                assertTrue(response.body().contains("kendyjenny@naver.com"))
                assertTrue(response.body().contains("2026년 10월 30일"))
                assertFalse(response.body().contains("미확정"))
                assertFalse(response.body().contains("[대표"))
                assertFalse(response.body().contains("<script"))
                assertTrue(response.headers().firstValue("Content-Security-Policy").isPresent)
            }
            val unknown = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/terms/documents/2026-10-30/private"))
                .GET().build(), HttpResponse.BodyHandlers.discarding())
            assertEquals(401, unknown.statusCode())
        }
    }

    @TestConfiguration
    class LocalStorage {
        @Bean fun legalTestStorage() = LocalRecordingStorage(Files.createTempDirectory("legal-storage-"),
            "http://backend:8080/api/recordings/read", "synthetic-legal-secret-at-least-thirty-two-bytes")
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnpublishedLegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0
    @Test fun `deploying a draft does not publish it before storage cutover and activation`() {
        HttpClient.newHttpClient().use { client ->
            val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/terms/documents/2026-10-30/privacy"))
                .GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(503, response.statusCode())
            assertFalse(response.body().contains("국내 서버"))
            val deletion = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/terms/documents/account-deletion"))
                .GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(503, deletion.statusCode())
        }
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
class MissingLocalStorageLegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0
    @Test fun `activation alone cannot publish a domestic storage policy without local storage`() {
        HttpClient.newHttpClient().use { client ->
            val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/terms/documents/2026-10-30/privacy"))
                .GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(503, response.statusCode())
        }
    }
}
