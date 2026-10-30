package com.ssafy.eolssu.backend.feature.term

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.legal.publication-enabled=true"])
@Import(LegalDocumentPageTests.LocalStorage::class)
class AccountDeletionPageTests {
    @Value("\${local.server.port}") private var port = 0
    @Test fun `instructions remain public with stale cookies and member endpoints stay protected`() {
        HttpClient.newHttpClient().use { client ->
            for (cookie in listOf(null, "access_token=invalid.jwt")) {
                val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/terms/documents/account-deletion"))
                cookie?.let { request.header("Cookie", it) }
                val result = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(200, result.statusCode())
                assertTrue(result.headers().firstValue("Content-Type").orElse("").startsWith("text/html"))
                assertTrue(result.body().contains("이도(Edo)"))
                assertTrue(result.body().contains("mailto:kendyjenny@naver.com"))
                assertTrue(result.body().contains("30일 유예기간"))
                assertFalse(result.body().contains("<script"))
            }
            val protected = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me")).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(401, protected.statusCode())
        }
    }
}
