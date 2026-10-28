package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.config.PasswordConfig
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
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
class WithdrawalApiDatabaseTests {
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
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990701,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990701,'USR209_W','검증',990701,990701,50)")
        jdbc.update(
            "INSERT INTO users (email,password_hash,nickname,instrument_id,status) VALUES ('bye@usr209.test',?,'연습자',990701,'ACTIVE')",
            PasswordConfig().passwordEncoder().encode("Current!234"),
        )
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'bye@usr209.test'", Long::class.java)!!
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990701)")
        jdbc.update("DELETE FROM users WHERE instrument_id = 990701")
        jdbc.update("DELETE FROM instruments WHERE id = 990701")
        jdbc.update("DELETE FROM pitches WHERE id = 990701")
    }

    @Test
    fun `withdrawal makes the member pending revokes every device and signing in again cancels it`() {
        val thisDevice = signIn()
        val otherDevice = signIn()

        val response = withdraw(thisDevice.cookieHeader(), """{"currentPassword":"Current!234"}""")
        assertEquals(200, response.statusCode(), response.body())
        val result = tree(response).path("result")
        assertTrue(result.path("withdrawn").asBoolean())
        val withdrawnAt = Instant.parse(result.path("withdrawnAt").asString())
        assertEquals(WithdrawalPolicy.purgeAt(withdrawnAt), Instant.parse(result.path("purgeAt").asString()))
        val cleared = response.headers().allValues("Set-Cookie")
        assertEquals(setOf("access_token", "refresh_token"), cleared.map { it.substringBefore('=') }.toSet())
        assertTrue(cleared.all { it.contains("Max-Age=0") })

        val row = jdbc.queryForMap("SELECT status, withdrawal_requested_at, purge_scheduled_at FROM users WHERE id = ?", userId)
        assertEquals("WITHDRAWAL_PENDING", row["status"])
        assertEquals(withdrawnAt, (row["withdrawal_requested_at"] as LocalDateTime).toInstant(ZoneOffset.UTC))
        assertEquals(WithdrawalPolicy.purgeAt(withdrawnAt), (row["purge_scheduled_at"] as LocalDateTime).toInstant(ZoneOffset.UTC))
        assertEquals(listOf("WITHDRAWN", "WITHDRAWN"), reasons())
        assertEquals(401, refresh(otherDevice.refresh).statusCode())

        val signedIn = signInResponse()
        assertEquals(200, signedIn.statusCode(), signedIn.body())
        assertTrue(tree(signedIn).path("result").path("withdrawalCancelled").asBoolean())
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String::class.java, userId))
    }

    @Test
    fun `wrong password is 403 and shares the lock with password change`() {
        val device = signIn()
        repeat(3) {
            assertError(changePassword(device.cookieHeader(), """{"currentPassword":"Wrong!234","newPassword":"NewPassword!567"}"""), 403, "CURRENT_PASSWORD_MISMATCH")
        }
        assertError(withdraw(device.cookieHeader(), """{"currentPassword":"Wrong!234"}"""), 403, "CURRENT_PASSWORD_MISMATCH")
        val locked = withdraw(device.cookieHeader(), """{"currentPassword":"Wrong!234"}""")
        assertError(locked, 429, "TOO_MANY_REQUESTS")
        assertTrue(tree(locked).path("result").path("details").path("retryAvailableAt").isString)
        assertError(withdraw(device.cookieHeader(), """{"currentPassword":"Current!234"}"""), 429, "TOO_MANY_REQUESTS")
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String::class.java, userId))
    }

    @Test
    fun `missing password is 400 and the access cookie is required`() {
        val device = signIn()
        assertError(withdraw(device.cookieHeader(), "{}"), 400, "INVALID_REQUEST")
        assertError(withdraw(device.cookieHeader(), """{"currentPassword":""}"""), 400, "INVALID_REQUEST")
        assertEquals(401, withdraw(null, """{"currentPassword":"Current!234"}""").statusCode())
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String::class.java, userId))
    }

    private class Device(val access: String, val refresh: String) {
        fun cookieHeader() = "access_token=$access; refresh_token=$refresh"
    }

    private fun reasons(): List<String?> = jdbc.query("SELECT revoke_reason FROM refresh_tokens WHERE user_id = ? ORDER BY id", { rs, _ -> rs.getString(1) }, userId)

    private fun signIn(): Device {
        val response = signInResponse()
        assertEquals(200, response.statusCode(), response.body())
        return Device(cookie(response, "access_token"), cookie(response, "refresh_token"))
    }

    private fun signInResponse(): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/sign-in")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"bye@usr209.test","password":"Current!234"}""")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun cookie(response: HttpResponse<String>, name: String) =
        response.headers().allValues("Set-Cookie").first { it.startsWith("$name=") }.substringAfter('=').substringBefore(';')

    private fun refresh(token: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/refresh")).header("Cookie", "refresh_token=$token")
            .POST(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun withdraw(cookie: String?, body: String) = send("DELETE", "/api/users/me", cookie, body)
    private fun changePassword(cookie: String?, body: String) = send("PATCH", "/api/users/me/password", cookie, body)

    private fun send(method: String, path: String, cookie: String?, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
        cookie?.let { builder.header("Cookie", it) }
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
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
