package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.config.PasswordConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmailVerificationApiDatabaseTests.Fixtures::class)
class PasswordChangeApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private var userId = 0L

    @BeforeEach
    fun before() {
        cleanup()
        // access_token 만료는 실제 시각으로 검증하므로 발급도 실제 시각으로 한다.
        clock.set(Instant.now())
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990601,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990601,'USR208_PW','검증',990601,990601,50)")
        jdbc.update(
            "INSERT INTO users (email,password_hash,nickname,instrument_id,status) VALUES ('pw@usr208pw.test',?,'연습자',990601,'ACTIVE')",
            PasswordConfig().passwordEncoder().encode("Current!234"),
        )
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'pw@usr208pw.test'", Long::class.java)!!
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990601)")
        jdbc.update("DELETE FROM users WHERE instrument_id = 990601")
        jdbc.update("DELETE FROM instruments WHERE id = 990601")
        jdbc.update("DELETE FROM pitches WHERE id = 990601")
    }

    @Test
    fun `changing the password signs out every other device and keeps this device signed in`() {
        val thisDevice = signIn("Current!234")
        val otherDevice = signIn("Current!234")

        val changed = patch(thisDevice.cookieHeader(), """{"currentPassword":"Current!234","newPassword":"NewPassword!567"}""")
        assertEquals(200, changed.statusCode(), changed.body())
        assertTrue(tree(changed).path("result").path("changed").asBoolean())
        val newRefresh = cookie(changed, "refresh_token")
        assertNotEquals(thisDevice.refresh, newRefresh)

        assertEquals(listOf("PASSWORD_CHANGED", "PASSWORD_CHANGED", null), reasons())
        assertEquals(401, refresh(otherDevice.refresh).statusCode())
        assertEquals(401, refresh(thisDevice.refresh).statusCode())
        assertEquals(200, refresh(newRefresh).statusCode())
        assertEquals(401, signInResponse("Current!234").statusCode())
        assertEquals(200, signInResponse("NewPassword!567").statusCode())
    }

    @Test
    fun `wrong current password is 403 and the fifth failure is 429`() {
        val device = signIn("Current!234")
        val wrong = """{"currentPassword":"Wrong!234","newPassword":"NewPassword!567"}"""
        repeat(4) { assertError(patch(device.cookieHeader(), wrong), 403, "CURRENT_PASSWORD_MISMATCH") }
        val locked = patch(device.cookieHeader(), wrong)
        assertError(locked, 429, "TOO_MANY_REQUESTS")
        assertTrue(tree(locked).path("result").path("details").path("retryAvailableAt").isString)
        assertError(patch(device.cookieHeader(), """{"currentPassword":"Current!234","newPassword":"NewPassword!567"}"""), 429, "TOO_MANY_REQUESTS")
        assertEquals(listOf<String?>(null), reasons())
    }

    @Test
    fun `invalid new passwords and missing fields are 400 and the access cookie is required`() {
        val device = signIn("Current!234")
        for (body in listOf(
            """{"currentPassword":"Current!234","newPassword":"Current!234"}""",
            """{"currentPassword":"Current!234","newPassword":"nodigits!"}""",
            """{"currentPassword":"Current!234"}""",
        )) {
            assertError(patch(device.cookieHeader(), body), 400, "INVALID_REQUEST")
        }
        assertEquals(401, patch(null, """{"currentPassword":"Current!234","newPassword":"NewPassword!567"}""").statusCode())
        assertEquals(200, signInResponse("Current!234").statusCode())
    }

    @Test
    fun `password change and another device refresh at the same time never fail with a server error`() {
        val passwords = listOf("Current!234", "NewPassword!567")
        val statuses = mutableListOf<Pair<Int, Int>>()
        repeat(20) { round ->
            val thisDevice = signIn(passwords[round % 2])
            val otherDevice = signIn(passwords[round % 2])
            val body = """{"currentPassword":"${passwords[round % 2]}","newPassword":"${passwords[(round + 1) % 2]}"}"""
            // 비밀번호 확인(Argon2)이 끝나 트랜잭션에 들어가는 시점과 재발급이 겹치도록 재발급을 조금씩 늦춘다.
            val change = java.util.concurrent.CompletableFuture.supplyAsync { patch(thisDevice.cookieHeader(), body).statusCode() }
            Thread.sleep((round % 10) * 10L)
            val refreshed = refresh(otherDevice.refresh).statusCode()
            statuses += change.get() to refreshed
            assertTrue(statuses.last().first == 200 && statuses.last().second in setOf(200, 401), statuses.toString())
        }
    }

    private class Device(val access: String, val refresh: String) {
        fun cookieHeader() = "access_token=$access; refresh_token=$refresh"
    }

    private fun reasons(): List<String?> = jdbc.query("SELECT revoke_reason FROM refresh_tokens WHERE user_id = ? ORDER BY id", { rs, _ -> rs.getString(1) }, userId)

    private fun signIn(password: String): Device {
        val response = signInResponse(password)
        assertEquals(200, response.statusCode(), response.body())
        return Device(cookie(response, "access_token"), cookie(response, "refresh_token"))
    }

    private fun signInResponse(password: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/sign-in")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"pw@usr208pw.test","password":"$password"}""")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun cookie(response: HttpResponse<String>, name: String) =
        response.headers().allValues("Set-Cookie").first { it.startsWith("$name=") }.substringAfter('=').substringBefore(';')

    private fun refresh(token: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/refresh")).header("Cookie", "refresh_token=$token")
            .POST(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun patch(cookie: String?, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me/password")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
        cookie?.let { builder.header("Cookie", it) }
        return client.send(builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun tree(response: HttpResponse<String>): JsonNode = json.readTree(response.body())
    private fun assertError(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, tree(response).path("result").path("errorCode").asString())
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
