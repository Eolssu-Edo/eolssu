package com.ssafy.eolssu.backend.common.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import jakarta.servlet.http.Cookie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.bind.annotation.RestController

private const val TEST_SECRET = "test-only-signing-key-32-bytes-1234567890"

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["eolssu.security.jwt-secret=$TEST_SECRET"])
@AutoConfigureMockMvc
@Import(PrincipalFixture::class)
class AccessTokenSecurityTests {
    @Autowired lateinit var mvc: MockMvc
    @LocalServerPort var port: Int = 0

    @Test fun `real HTTP validates signed cookies and rejects invalid cookies`() {
        HttpClient.newHttpClient().use { client ->
            for ((value, expected) in listOf(token() to 200, "invalid" to 401,
                token(expires = Instant.now().minusSeconds(1)) to 401)) {
                val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/security-test/principal"))
                    .header("Cookie", "access_token=$value").build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(expected, response.statusCode())
                if (expected == 200) assertEquals("{\"userId\":1}", response.body())
                else assertTrue(response.body().contains("\"errorCode\":\"UNAUTHENTICATED\""))
            }
        }
    }

    @Test fun `public endpoints ignore expired and malformed access cookies`() {
        for (value in listOf(token(expires = Instant.now().minusSeconds(300)), "broken.jwt")) {
            mvc.perform(get("/api/terms").cookie(Cookie("access_token", value)))
                .andExpect(status().isOk).andExpect(jsonPath("$.items").isArray)
            mvc.perform(post("/api/auth/refresh").cookie(Cookie("access_token", value), Cookie("refresh_token", "fixture-refresh")))
                .andExpect(status().isOk).andExpect(jsonPath("$.refreshReached").value(true))
        }
    }

    @Test fun `real HTTP keeps public routes open and returns common 406`() {
        HttpClient.newHttpClient().use { client ->
            for (path in listOf("/api/terms", "/api/auth/refresh")) {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .header("Cookie", "access_token=${token(expires = Instant.now().minusSeconds(300))}; refresh_token=fixture-refresh")
                if (path.endsWith("refresh")) builder.POST(HttpRequest.BodyPublishers.noBody())
                val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(200, response.statusCode())
            }
            val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/actuator/health"))
                .header("Accept", "image/png").build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(406, response.statusCode())
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
            assertTrue(response.body().contains("\"errorCode\":\"NOT_ACCEPTABLE\""))
            assertFalse(response.body().contains("\"type\""))
        }
    }

    @Test fun `accepts a signed cookie and exposes users id`() {
        mvc.perform(get("/api/security-test/principal").cookie(Cookie("access_token", token())))
            .andExpect(status().isOk).andExpect(jsonPath("$.userId").value(1))
    }

    @Test fun `supports jwt post processor for authenticated controller tests`() {
        mvc.perform(post("/api/security-test/principal").with(jwt().jwt { it.subject("1") }))
            .andExpect(status().isOk).andExpect(jsonPath("$.userId").value(1))
    }

    @Test fun `rejects forged expired malformed and invalid subject cookies with common error`() {
        for (value in listOf(token(secret = "other-test-signing-key-32-bytes-123456789"),
            token(expires = Instant.now().minusSeconds(300)), "broken.jwt.value",
            token(subject = "not-a-user"), token(subject = "0"), token(subject = "9223372036854775808"))) {
            mvc.perform(get("/api/security-test/principal").cookie(Cookie("access_token", value)))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.httpStatus").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").isString)
                .andExpect(jsonPath("$.result.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.result.details").doesNotExist())
        }
    }

    @Test fun `ignores authorization header query and other cookies`() {
        mvc.perform(get("/api/security-test/principal").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/security-test/principal").param("access_token", token()))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/security-test/principal").cookie(Cookie("refresh_token", token())))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/security-test/principal").cookie(Cookie("access_token", token()))
            .header("Authorization", "Bearer invalid"))
            .andExpect(status().isOk)
    }
}

@SpringBootTest(properties = ["eolssu.security.jwt-secret="])
@AutoConfigureMockMvc
@Import(PrincipalFixture::class)
class MissingAccessTokenKeyTests {
    @Autowired lateinit var mvc: MockMvc
    @Test fun `starts without a key but rejects even a correctly signed cookie`() {
        mvc.perform(get("/api/actuator/health/readiness")).andExpect(status().isOk)
        mvc.perform(get("/api/security-test/principal").cookie(Cookie("access_token", token())))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result.errorCode").value("UNAUTHENTICATED"))
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "eolssu.security.jwt-secret=$TEST_SECRET",
    "spring.servlet.multipart.max-file-size=1KB",
    "spring.servlet.multipart.max-request-size=2KB",
])
@Import(UploadLimitFixture::class)
class MultipartLimitTests {
    @LocalServerPort var port: Int = 0

    @Test fun `real multipart parser returns common 413 for an authenticated upload`() {
        val boundary = "test-multipart-boundary"
        val body = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.wav\"\r\n" +
            "Content-Type: audio/wav\r\n\r\n" + "x".repeat(3072) + "\r\n--$boundary--\r\n"
        HttpClient.newHttpClient().use { client ->
            val response = client.send(HttpRequest.newBuilder(
                URI("http://127.0.0.1:$port/api/practice-sessions/1/recording"))
                .header("Cookie", "access_token=${token()}")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(413, response.statusCode())
            assertTrue(response.body().contains("\"errorCode\":\"RECORDING_TOO_LARGE\""))
            assertFalse(response.body().contains("\"type\""))
        }
    }
}

@TestComponent
@RestController
class UploadLimitFixture {
    @PostMapping("/api/practice-sessions/{sessionId}/recording")
    fun upload(@RequestParam file: MultipartFile) = mapOf("size" to file.size)
}

@TestComponent
@RestController
class PrincipalFixture {
    @org.springframework.web.bind.annotation.GetMapping("/api/terms")
    fun terms() = mapOf("items" to emptyList<String>())

    @PostMapping("/api/auth/refresh")
    fun refresh(@org.springframework.web.bind.annotation.CookieValue("refresh_token") refresh: String) =
        mapOf("refreshReached" to (refresh == "fixture-refresh"))

    @RequestMapping("/api/security-test/principal")
    fun principal(@AuthenticationPrincipal jwt: Jwt) = mapOf("userId" to requireNotNull(jwt.subject).toLong())
}

private fun token(
    secret: String = TEST_SECRET,
    subject: String = "1",
    expires: Instant = Instant.now().plusSeconds(300),
): String = SignedJWT(
    JWSHeader(JWSAlgorithm.HS256),
    JWTClaimsSet.Builder().subject(subject).issueTime(Date.from(Instant.now().minusSeconds(600)))
        .expirationTime(Date.from(expires)).build(),
).apply { sign(MACSigner(secret.toByteArray(Charsets.UTF_8))) }.serialize()
