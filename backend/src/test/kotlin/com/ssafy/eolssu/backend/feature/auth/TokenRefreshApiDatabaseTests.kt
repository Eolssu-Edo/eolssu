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
class TokenRefreshApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private var userId = 0L

    @BeforeEach
    fun before() {
        cleanup()
        clock.set(Instant.parse("2026-10-28T03:00:00Z"))
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990401,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990401,'AUTH207_RF','검증',990401,990401,50)")
        jdbc.update(
            "INSERT INTO users (email,password_hash,nickname,instrument_id,status) VALUES ('refresh@auth207rf.test',?,'연습자',990401,'ACTIVE')",
            PasswordConfig().passwordEncoder().encode("Example!234"),
        )
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'refresh@auth207rf.test'", Long::class.java)!!
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990401)")
        jdbc.update("DELETE FROM users WHERE instrument_id = 990401")
        jdbc.update("DELETE FROM instruments WHERE id = 990401")
        jdbc.update("DELETE FROM pitches WHERE id = 990401")
    }

    @Test
    fun `refresh rotates cookies and reusing the old token revokes every session`() {
        val firstRefresh = cookie(signIn(), "refresh_token")
        clock.set(Instant.parse("2026-10-28T03:10:00Z"))
        val refreshed = refresh(firstRefresh)
        assertEquals(200, refreshed.statusCode(), refreshed.body())
        assertEquals("2026-10-28T03:40:00Z", tree(refreshed).path("result").path("accessTokenExpiresAt").asString())
        val secondRefresh = cookie(refreshed, "refresh_token")
        assertNotEquals(firstRefresh, secondRefresh)
        assertTrue(refreshed.headers().allValues("Set-Cookie").any { it.startsWith("access_token=") && !it.startsWith("access_token=;") })
        assertEquals(listOf("ROTATED", null), reasons())

        val reused = refresh(firstRefresh)
        assertError(reused, 401, "UNAUTHENTICATED")
        val cleared = reused.headers().allValues("Set-Cookie")
        assertEquals(setOf("access_token", "refresh_token"), cleared.map { it.substringBefore('=') }.toSet())
        assertTrue(cleared.all { it.contains("Max-Age=0") })
        assertEquals(listOf("ROTATED", "REUSE_DETECTED"), reasons())
        assertError(refresh(secondRefresh), 401, "UNAUTHENTICATED")
    }

    @Test
    fun `missing refresh cookie and withdrawal pending members are unauthenticated`() {
        assertError(post("/api/auth/refresh", null), 401, "UNAUTHENTICATED")
        val token = cookie(signIn(), "refresh_token")
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = '2026-10-28', purge_scheduled_at = '2026-11-27' WHERE id = ?", userId)
        assertError(refresh(token), 401, "UNAUTHENTICATED")
        assertEquals(listOf<String?>(null), reasons())
    }

    private fun reasons(): List<String?> = jdbc.query("SELECT revoke_reason FROM refresh_tokens WHERE user_id = ? ORDER BY id", { rs, _ -> rs.getString(1) }, userId)

    private fun signIn(): HttpResponse<String> {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/sign-in")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"refresh@auth207rf.test","password":"Example!234"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response
    }

    private fun cookie(response: HttpResponse<String>, name: String) =
        response.headers().allValues("Set-Cookie").first { it.startsWith("$name=") }.substringAfter('=').substringBefore(';')

    private fun refresh(token: String) = post("/api/auth/refresh", "refresh_token=$token")

    private fun post(path: String, cookie: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
        cookie?.let { builder.header("Cookie", it) }
        return client.send(builder.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
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
