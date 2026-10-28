package com.ssafy.eolssu.backend.feature.user

import com.ssafy.eolssu.backend.config.PasswordConfig
import com.ssafy.eolssu.backend.feature.auth.EmailVerificationApiDatabaseTests
import com.ssafy.eolssu.backend.feature.auth.VerificationTestClock
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
class MyInfoApiDatabaseTests {
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
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990501,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990501,'USR208_A','검증',990501,990501,50)")
        jdbc.update(
            "INSERT INTO users (email,password_hash,nickname,instrument_id,notation_mode,status) VALUES ('me@usr208.test',?,'연습자',990501,'HANGUL','ACTIVE')",
            PasswordConfig().passwordEncoder().encode("Example!234"),
        )
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'me@usr208.test'", Long::class.java)!!
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990501)")
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id = 990501)")
        jdbc.update("DELETE FROM users WHERE instrument_id = 990501")
        jdbc.update("DELETE FROM instruments WHERE id = 990501")
        jdbc.update("DELETE FROM pitches WHERE id = 990501")
    }

    @Test
    fun `my info has linked providers in link order and no email or provider ids`() {
        jdbc.update("INSERT INTO user_social_accounts (user_id,provider,provider_user_id,created_at) VALUES (?,'NAVER','naver-208','2026-10-28 01:00:00')", userId)
        jdbc.update("INSERT INTO user_social_accounts (user_id,provider,provider_user_id,created_at) VALUES (?,'KAKAO','kakao-208','2026-10-27 00:00:00')", userId)

        val response = get("/api/users/me", signIn())
        assertEquals(200, response.statusCode(), response.body())
        val result = tree(response).path("result")
        assertEquals(userId.toString(), result.path("userId").asString())
        assertEquals("연습자", result.path("nickname").asString())
        assertEquals("990501", result.path("instrumentId").asString())
        assertEquals("HANGUL", result.path("notationMode").asString())
        val linked = (0 until result.path("linkedProviders").size()).map { result.path("linkedProviders").path(it) }
            .map { it.path("provider").asString() to it.path("linkedAt").asString() }
        assertEquals(listOf("KAKAO" to "2026-10-27T00:00:00Z", "NAVER" to "2026-10-28T01:00:00Z"), linked)
        assertFalse(response.body().contains("usr208.test"))
        assertFalse(response.body().contains("kakao-208"))
    }

    @Test
    fun `my info needs the access cookie and a withdrawal pending member is not found`() {
        assertEquals(401, get("/api/users/me", null).statusCode())
        val access = signIn()
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = '2026-10-28', purge_scheduled_at = '2026-11-27' WHERE id = ?", userId)
        assertError(get("/api/users/me", access), 404, "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `nickname is saved and setting the same nickname again still succeeds`() {
        val access = signIn()
        val changed = patch("/api/users/me", access, """{"nickname":"새 닉네임"}""")
        assertEquals(200, changed.statusCode(), changed.body())
        assertEquals("새 닉네임", tree(changed).path("result").path("nickname").asString())
        assertEquals("새 닉네임", jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String::class.java, userId))
        assertEquals(200, patch("/api/users/me", access, """{"nickname":"새 닉네임"}""").statusCode())
        assertError(patch("/api/users/me", access, """{"nickname":""}"""), 400, "INVALID_REQUEST")
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = '2026-10-28', purge_scheduled_at = '2026-11-27' WHERE id = ?", userId)
        assertError(patch("/api/users/me", access, """{"nickname":"다른 이름"}"""), 404, "RESOURCE_NOT_FOUND")
        assertEquals("새 닉네임", jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String::class.java, userId))
    }

    private fun signIn(): String {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/sign-in")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"me@usr208.test","password":"Example!234"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response.headers().allValues("Set-Cookie").first { it.startsWith("access_token=") }.substringBefore(';')
    }

    private fun get(path: String, cookie: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
        cookie?.let { builder.header("Cookie", it) }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun patch(path: String, cookie: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json").header("Cookie", cookie)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
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
