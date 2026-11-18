package com.ssafy.eolssu.backend.common.error

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ErrorFixtureController::class)
class CommonErrorHttpTests {
    @Value("\${local.server.port}")
    private var port = 0

    @Test
    fun `real HTTP uses the common error body for MVC 400 405 and unexpected 500`() {
        HttpClient.newHttpClient().use { client ->
            val json = JsonMapper.builder().build()
            for ((method, path, expectedStatus, errorCode) in listOf(
                listOf("GET", "/fixture", "400", "INVALID_REQUEST"),
                listOf("POST", "/fixture/failure", "405", "METHOD_NOT_ALLOWED"),
                listOf("GET", "/fixture/failure", "500", "INTERNAL_SERVER_ERROR"),
            )) {
                val response = client.send(
                    HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                        .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                assertEquals(expectedStatus.toInt(), response.statusCode(), response.body())
                val body = json.readTree(response.body())
                assertEquals(expectedStatus.toInt(), body.path("code").asInt())
                assertFalse(body.path("isSuccess").asBoolean())
                assertEquals(errorCode, body.path("result").path("errorCode").asString())
                assertFalse(response.body().contains("secret"))
                if (expectedStatus == "405") assertEquals("GET", response.headers().firstValue("Allow").orElse(""))
            }
        }
    }
}
