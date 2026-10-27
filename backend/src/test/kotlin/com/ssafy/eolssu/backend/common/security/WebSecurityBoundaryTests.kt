package com.ssafy.eolssu.backend.common.security

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["eolssu.security.allowed-origins=http://localhost:5173"],
)
class WebSecurityBoundaryTests {
    @Value("\${local.server.port}")
    private var port: Int = 0

    private val json = JsonMapper.builder().build()

    @Test
    fun `rejects a member API without login using the common error body`() {
        val response = send("GET", "/api/users/me")

        assertError(response, 401, "UNAUTHORIZED", "UNAUTHENTICATED")
    }

    @Test
    fun `returns not found for an unknown public path using the common error body`() {
        val response = send("GET", "/api/songs/999/no-such-resource")

        assertError(response, 404, "NOT_FOUND", "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `rejects state changing requests that are not JSON`() {
        val form = send("POST", "/api/auth/sign-in", "Content-Type" to "application/x-www-form-urlencoded", body = "email=a")
        assertError(form, 415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE")

        val text = send("POST", "/api/auth/sign-in", "Content-Type" to "text/plain", body = "email=a")
        assertError(text, 415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE")

        val emptyForm = send("POST", "/api/auth/refresh", "Content-Type" to "application/x-www-form-urlencoded")
        assertError(emptyForm, 415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE")

        val untyped = send("POST", "/api/auth/sign-in", body = "email=a")
        assertError(untyped, 415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE")
    }

    @Test
    fun `lets JSON and bodiless requests through to routing`() {
        val jsonBody = send("POST", "/api/auth/sign-in", "Content-Type" to "application/json; charset=UTF-8", body = "{}")
        assertError(jsonBody, 404, "NOT_FOUND", "RESOURCE_NOT_FOUND")

        val bodiless = send("POST", "/api/auth/refresh")
        assertError(bodiless, 404, "NOT_FOUND", "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `accepts multipart only for the recording upload`() {
        val multipart = "multipart/form-data; boundary=test-boundary"
        val upload = send("POST", "/api/practice-sessions/1/recording", "Content-Type" to multipart, body = "--test-boundary--")
        assertError(upload, 401, "UNAUTHORIZED", "UNAUTHENTICATED")

        val elsewhere = send("POST", "/api/auth/sign-in", "Content-Type" to multipart, body = "--test-boundary--")
        assertError(elsewhere, 415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE")
    }

    @Test
    fun `rejects state changing requests from another site`() {
        val crossSite = send("POST", "/api/auth/sign-in", "Content-Type" to "application/json", "Origin" to "https://evil.example", body = "{}")
        assertError(crossSite, 403, "FORBIDDEN", "FORBIDDEN")

        val opaque = send("POST", "/api/auth/sign-in", "Content-Type" to "application/json", "Origin" to "null", body = "{}")
        assertError(opaque, 403, "FORBIDDEN", "FORBIDDEN")

        val upload = send(
            "POST", "/api/practice-sessions/1/recording",
            "Content-Type" to "multipart/form-data; boundary=test-boundary", "Origin" to "https://evil.example", body = "--test-boundary--",
        )
        assertError(upload, 403, "FORBIDDEN", "FORBIDDEN")
    }

    @Test
    fun `accepts the same site and configured development origins`() {
        val sameSite = send("POST", "/api/auth/sign-in", "Content-Type" to "application/json", "Origin" to "http://127.0.0.1:$port", body = "{}")
        assertError(sameSite, 404, "NOT_FOUND", "RESOURCE_NOT_FOUND")

        val development = send("POST", "/api/auth/sign-in", "Content-Type" to "application/json", "Origin" to "http://localhost:5173", body = "{}")
        assertError(development, 404, "NOT_FOUND", "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `allows credentialed CORS only for configured development origins`() {
        fun preflight(origin: String) = send(
            "OPTIONS", "/api/auth/sign-in",
            "Origin" to origin, "Access-Control-Request-Method" to "POST", "Access-Control-Request-Headers" to "content-type",
        )

        val allowed = preflight("http://localhost:5173")
        assertEquals("http://localhost:5173", allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        assertEquals("true", allowed.headers().firstValue("Access-Control-Allow-Credentials").orElse(null))

        val denied = preflight("https://evil.example")
        assertNull(denied.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
    }

    @Test
    fun `keeps health public and other management endpoints hidden`() {
        assertEquals(200, send("GET", "/api/actuator/health/readiness").statusCode())
        assertEquals(404, send("GET", "/api/actuator/env").statusCode())
    }

    private fun send(method: String, path: String, vararg headers: Pair<String, String>, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(5))
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        return HttpClient.newHttpClient().use { it.send(builder.build(), HttpResponse.BodyHandlers.ofString()) }
    }

    private fun assertError(response: HttpResponse<String>, status: Int, statusName: String, errorCode: String) {
        assertEquals(status, response.statusCode(), response.body())
        val body: JsonNode = json.readTree(response.body())
        assertEquals(statusName, body.path("httpStatus").asString())
        assertFalse(body.path("isSuccess").asBoolean(true))
        assertEquals(status, body.path("code").asInt())
        assertEquals(errorCode, body.path("result").path("errorCode").asString())
        assertFalse(body.path("message").asString().isBlank())
        assertFalse(body.has("trace") || body.has("exception") || body.has("path"))
    }
}
