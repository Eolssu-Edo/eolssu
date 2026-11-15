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
                assertEquals(503, client.get(port, "/api/terms/documents/2026-11-02/$document").statusCode())
                assertEquals(503, client.get(port, "/api/terms/documents/2026-11-03/$document").statusCode())
                assertEquals(503, client.get(port, "/api/terms/documents/2026-11-15/$document").statusCode())
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

    @Test fun `R2 storage publishes the user sheet version and keeps the previous versions as history`() {
        HttpClient.newHttpClient().use { client ->
            for (document in LegalDocumentPageController.DOCUMENTS) {
                val path = "/api/terms/documents/2026-11-15/$document"
                assertPublished(client.get(port, path), path, "2026년 11월 15일")
                for ((version, effectiveDate) in listOf("2026-11-03" to "2026년 11월 3일", "2026-11-02" to "2026년 11월 2일",
                        "2026-10-30" to "2026년 10월 30일")) {
                    val previous = "/api/terms/documents/$version/$document"
                    assertPublished(client.get(port, previous), previous, effectiveDate)
                }
            }
            val privacy = client.get(port, "/api/terms/documents/2026-11-02/privacy").body()
            assertTrue(privacy.contains("Cloudflare, Inc."))
            assertTrue(privacy.contains("국외 이전"))
            assertFalse(privacy.contains("국내 서버·비공개 디스크"))
            val current = client.get(port, "/api/terms/documents/current/privacy")
            assertPublished(current, "current", "2026년 11월 15일")
            assertTrue(current.body().contains("Cloudflare, Inc."))
            // S15P31E102-375: saved records keep their recordings; unsaved ones still go after 30 days.
            assertTrue(current.body().contains("'연습 기록 저장'한 연주의 녹음은 그 기록을 삭제하거나 탈퇴 삭제가 끝날 때까지 보관합니다."))
            assertTrue(current.body().contains("저장한 연습 기록의 녹음은 그 기록을 삭제하거나 탈퇴 삭제가 끝날 때까지."))
            assertTrue(client.get(port, "/api/terms/documents/current/collection").body()
                .contains("저장한 연습 기록의 녹음은 그 기록을 삭제하거나 탈퇴 일괄 삭제 시까지 보관합니다."))
            assertTrue(client.get(port, "/api/terms/documents/current/service").body()
                .contains("저장하지 않은 녹음 원본은 업로드 후 30일이 지나면 삭제되고 탈퇴 취소로 복원되지 않습니다."))
            // S15P31E102-918: user sheet originals stay on the domestic temporary spool; only generated audio goes to R2 for 10 minutes.
            val privacy918 = current.body()
            assertTrue(privacy918.contains("국내 서버 임시 공간의 원본 파일과 원본 파일명·형식·크기 기록은 악보 변환을 마치거나 실패하면 바로, 늦어도 업로드 후 30분 안에 삭제합니다."))
            assertTrue(privacy918.contains("제목을 입력하지 않으면 앱이 첫 파일 이름(확장자 제외)을 제목으로 채웁니다."))
            assertTrue(privacy918.contains("<h3>국외 이전(내 악보 재생 음원 보관)</h3>"))
            assertTrue(privacy918.contains("보유·이용 기간: 재생 음원 파일은 생성 후 10분"))
            // The 2026-11-02 migration sentence must not move with the version date.
            assertTrue(privacy918.contains("2026년 11월 2일에 운영자가 같은 방법으로 이전했으며"))
            assertTrue(privacy918.contains("올린 악보 원본 파일은 이전하지 않음"))
            assertTrue(privacy918.contains("내 악보와 재생 음원, 곡별 설정·동의·계정 연결 정보를 삭제합니다."))
            assertTrue(client.get(port, "/api/terms/documents/current/collection").body().contains("<h2>내 악보 기능 이용 시 처리하는 정보</h2>"))
            val service918 = client.get(port, "/api/terms/documents/current/service").body()
            assertTrue(service918.contains("내 악보(악보 파일 올리기·변환·편집·재생 음원 만들기) 기능을 제공합니다."))
            assertTrue(service918.contains("내 악보로 올리는 악보 파일은 회원이 직접 만들었거나 이용할 권리가 있는 자료여야 합니다."))
            // The previous version stays unchanged as history.
            assertFalse(client.get(port, "/api/terms/documents/2026-11-03/privacy").body().contains("내 악보"))
            val deletion = client.get(port, "/api/terms/documents/account-deletion")
            assertEquals(200, deletion.statusCode())
            assertTrue(deletion.body().contains("학습 진도, 내 악보와 재생 음원, 곡별 설정"))
            assertTrue(deletion.body().contains("저장한 연습 기록의 녹음은 그 기록을 삭제하거나 탈퇴 삭제가 끝날 때까지 보관합니다."))
            assertTrue(LegalDocumentPageController.PUBLIC_PATHS.containsAll(LegalDocumentPageController.DOCUMENTS.flatMap {
                listOf("/api/terms/documents/2026-10-30/$it", "/api/terms/documents/2026-11-02/$it", "/api/terms/documents/2026-11-03/$it",
                    "/api/terms/documents/2026-11-15/$it", "/api/terms/documents/current/$it")
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
            for (version in listOf("2026-10-30", "2026-11-02", "2026-11-03", "2026-11-15")) {
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
    for (version in listOf("2026-10-30", "2026-11-02", "2026-11-03", "2026-11-15", "current")) {
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
