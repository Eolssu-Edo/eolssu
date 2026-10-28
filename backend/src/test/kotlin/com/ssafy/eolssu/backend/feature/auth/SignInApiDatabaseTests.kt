package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.config.PasswordConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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
class SignInApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val passwordHash = PasswordConfig().passwordEncoder().encode("Example!234")

    @BeforeEach
    fun before() {
        cleanup()
        clock.set(Instant.parse("2026-10-28T03:00:00Z"))
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990301,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990301,'AUTH207_IN','검증',990301,990301,50)")
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990301)")
        jdbc.update("DELETE FROM users WHERE instrument_id = 990301")
        jdbc.update("DELETE FROM instruments WHERE id = 990301")
        jdbc.update("DELETE FROM pitches WHERE id = 990301")
    }

    private fun member(email: String, status: String = "ACTIVE"): Long {
        if (status == "WITHDRAWAL_PENDING") {
            jdbc.update(
                "INSERT INTO users (email,password_hash,nickname,instrument_id,notation_mode,status,withdrawal_requested_at,purge_scheduled_at) VALUES (?,?,'대기',990301,'HANGUL',?,'2026-10-20','2026-11-20 19:00:00')",
                email, passwordHash, status,
            )
        } else {
            jdbc.update("INSERT INTO users (email,password_hash,nickname,instrument_id,notation_mode,status) VALUES (?,?,'연습자',990301,'HANJA',?)", email, passwordHash, status)
        }
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
    }

    @Test
    fun `active member signs in with cookies and the login is recorded`() {
        val id = member("active@auth207in.test")
        val response = signIn("Active@auth207in.test", "Example!234")
        assertEquals(200, response.statusCode(), response.body())
        val result = tree(response).path("result")
        assertEquals(id.toString(), result.path("userId").asString())
        assertEquals("연습자", result.path("nickname").asString())
        assertEquals("990301", result.path("instrumentId").asString())
        assertEquals("HANJA", result.path("notationMode").asString())
        assertFalse(result.path("withdrawalCancelled").asBoolean(true))
        val cookies = response.headers().allValues("Set-Cookie")
        assertTrue(cookies.any { it.startsWith("access_token=") && it.contains("Path=/api;") })
        assertTrue(cookies.any { it.startsWith("refresh_token=") && it.contains("Path=/api/auth;") })
        assertEquals(LocalDateTime.parse("2026-10-28T03:00:00"), jdbc.queryForObject("SELECT last_login_at FROM users WHERE id = ?", LocalDateTime::class.java, id))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Int::class.java, id))
    }

    @Test
    fun `withdrawal pending member signs in and becomes active again`() {
        val id = member("pending@auth207in.test", "WITHDRAWAL_PENDING")
        val response = signIn("pending@auth207in.test", "Example!234")
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(tree(response).path("result").path("withdrawalCancelled").asBoolean())
        val row = jdbc.queryForMap("SELECT status, withdrawal_requested_at, purge_scheduled_at FROM users WHERE id = ?", id)
        assertEquals("ACTIVE", row["status"])
        assertNull(row["withdrawal_requested_at"])
        assertNull(row["purge_scheduled_at"])
    }

    @Test
    fun `wrong unknown and malformed sign ins are rejected and the fifth failure locks the email`() {
        member("wrong@auth207in.test")
        assertError(signIn("absent@auth207in.test", "Example!234"), 401, "INVALID_CREDENTIALS")
        assertError(signIn("not-an-email", "Example!234"), 400, "INVALID_REQUEST")
        repeat(4) { assertError(signIn("wrong@auth207in.test", "Wrong!234"), 401, "INVALID_CREDENTIALS") }
        val locked = signIn("wrong@auth207in.test", "Wrong!234")
        assertError(locked, 429, "TOO_MANY_REQUESTS")
        assertEquals("2026-10-28T03:15:00Z", tree(locked).path("result").path("details").path("retryAvailableAt").asString())
        assertError(signIn("wrong@auth207in.test", "Example!234"), 429, "TOO_MANY_REQUESTS")
        assertTrue(response429HasNoCookies(signIn("wrong@auth207in.test", "Example!234")))
    }

    private fun response429HasNoCookies(response: HttpResponse<String>) = response.headers().allValues("Set-Cookie").isEmpty()

    private fun signIn(email: String, password: String) = post("/api/auth/sign-in", """{"email":"$email","password":"$password"}""")

    private fun post(path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(),
    )
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
