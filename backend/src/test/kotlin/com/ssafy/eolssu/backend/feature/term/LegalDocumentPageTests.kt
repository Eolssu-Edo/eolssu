package com.ssafy.eolssu.backend.feature.term

import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.S3RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.UnavailableRecordingStorage
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

private fun HttpClient.get(port: Int, path: String): HttpResponse<String> =
    send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Cookie", "access_token=invalid.jwt").GET().build(),
        HttpResponse.BodyHandlers.ofString())

private fun assertPublished(response: HttpResponse<String>, path: String, effectiveDate: String) {
    assertEquals(200, response.statusCode(), path)
    assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"))
    assertTrue(response.body().contains("이도(Edo)"))
    assertTrue(response.body().contains("kendyjenny@naver.com"))
    assertTrue(response.body().contains(effectiveDate), path)
    assertFalse(response.body().contains("미확정"))
    assertFalse(response.body().contains("[대표"))
    assertFalse(response.body().contains("<script"))
    assertTrue(response.headers().firstValue("Content-Security-Policy").isPresent)
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
@Import(LegalDocumentPageTests.LocalStorage::class)
class LegalDocumentPageTests {
    @Value("\${local.server.port}") private var port = 0

    @Test fun `local storage publishes only the domestic storage version as complete UTF-8 HTML`() {
        HttpClient.newHttpClient().use { client ->
            for (document in LegalDocumentPageController.DOCUMENTS) {
                val path = "/api/terms/documents/2026-10-30/$document"
                assertPublished(client.get(port, path), path, "2026년 10월 30일")
                // The overseas transfer notice must not become visible while recordings are still on the domestic disk.
                assertEquals(503, client.get(port, "/api/terms/documents/2026-10-31/$document").statusCode())
            }
            assertEquals(200, client.get(port, "/api/terms/documents/account-deletion").statusCode())
            assertEquals(401, client.get(port, "/api/terms/documents/2026-10-30/private").statusCode())
            // The app and Play Console link the current alias, which must follow the storage actually in use.
            assertPublished(client.get(port, "/api/terms/documents/current/privacy"), "current", "2026년 10월 30일")
        }
    }

    @TestConfiguration
    class LocalStorage {
        @Bean fun legalTestStorage() = LocalRecordingStorage(Files.createTempDirectory("legal-storage-"),
            "http://backend:8080/api/recordings/read", "synthetic-legal-secret-at-least-thirty-two-bytes")
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
@Import(R2LegalDocumentTests.R2Storage::class)
class R2LegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0

    @Test fun `R2 storage publishes the overseas transfer version and keeps the previous version as history`() {
        HttpClient.newHttpClient().use { client ->
            for (document in LegalDocumentPageController.DOCUMENTS) {
                val path = "/api/terms/documents/2026-10-31/$document"
                assertPublished(client.get(port, path), path, "2026년 10월 31일")
                val previous = "/api/terms/documents/2026-10-30/$document"
                assertPublished(client.get(port, previous), previous, "2026년 10월 30일")
            }
            val privacy = client.get(port, "/api/terms/documents/2026-10-31/privacy").body()
            assertTrue(privacy.contains("Cloudflare, Inc."))
            assertTrue(privacy.contains("국외 이전"))
            assertFalse(privacy.contains("국내 서버·비공개 디스크"))
            val current = client.get(port, "/api/terms/documents/current/privacy")
            assertPublished(current, "current", "2026년 10월 31일")
            assertTrue(current.body().contains("Cloudflare, Inc."))
            assertEquals(200, client.get(port, "/api/terms/documents/account-deletion").statusCode())
            assertTrue(LegalDocumentPageController.PUBLIC_PATHS.containsAll(LegalDocumentPageController.DOCUMENTS.flatMap {
                listOf("/api/terms/documents/2026-10-30/$it", "/api/terms/documents/2026-10-31/$it", "/api/terms/documents/current/$it")
            }))
        }
    }

    @TestConfiguration
    class R2Storage {
        // Construction signs locally and opens no connection; these tests never upload.
        @Bean fun legalTestStorage() = S3RecordingStorage.create(RecordingStorageProperties(endpoint = "http://127.0.0.1:9",
            bucket = "synthetic", accessKeyId = "synthetic", secretAccessKey = "synthetic"))
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnpublishedLegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0
    @Test fun `deploying a draft does not publish it before storage cutover and activation`() {
        HttpClient.newHttpClient().use { client ->
            for (version in listOf("2026-10-30", "2026-10-31")) {
                val response = client.get(port, "/api/terms/documents/$version/privacy")
                assertEquals(503, response.statusCode())
                assertFalse(response.body().contains("국내 서버"))
                assertFalse(response.body().contains("Cloudflare"))
            }
            assertEquals(503, client.get(port, "/api/terms/documents/account-deletion").statusCode())
        }
    }
}

private fun assertNothingPublished(client: HttpClient, port: Int) {
    for (version in listOf("2026-10-30", "2026-10-31", "current")) {
        assertEquals(503, client.get(port, "/api/terms/documents/$version/privacy").statusCode(), version)
    }
    assertEquals(503, client.get(port, "/api/terms/documents/account-deletion").statusCode())
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
class MissingRecordingStorageLegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0
    @Test fun `activation alone cannot publish a storage policy without the matching storage`() {
        HttpClient.newHttpClient().use { assertNothingPublished(it, port) }
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
@Import(UnconfiguredR2LegalDocumentTests.Unconfigured::class)
class UnconfiguredR2LegalDocumentTests {
    @Value("\${local.server.port}") private var port = 0
    // type=s3 with missing R2 values is the likeliest cutover mistake; it must not look like a completed R2 switch.
    @Test fun `s3 selected without R2 values publishes neither policy`() {
        HttpClient.newHttpClient().use { assertNothingPublished(it, port) }
    }

    @TestConfiguration
    class Unconfigured {
        @Bean fun legalTestStorage() = UnavailableRecordingStorage()
    }
}
