package com.ssafy.eolssu.backend.feature.auth

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
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
class SignUpApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Autowired private lateinit var mail: CapturingVerificationMail
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun before() {
        cleanup()
        clock.set(Instant.parse("2026-10-28T03:00:00Z"))
        mail.messages.clear(); mail.fail = false
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990201,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990201,'DANSO','단소',990201,990201,50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active) VALUES (990202,'AUTH207_OFF','중지',990201,990201,50,FALSE)")
        for ((id, code, required) in listOf(Triple(990211, "AGE_OVER_14", true), Triple(990212, "SERVICE_TERMS", true), Triple(990215, "MARKETING_NOTICE", false))) {
            jdbc.update(
                "INSERT INTO terms (id,code,version,title,content_url,is_required,seq,effective_at) VALUES (?,?,'2026-10',?,'https://example.test/terms',?,?,'2026-10-01')",
                id, code, code, required, id - 990210,
            )
        }
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@auth207.test')")
        jdbc.update("DELETE FROM user_term_agreements WHERE term_id BETWEEN 990211 AND 990215")
        jdbc.update("DELETE FROM users WHERE email LIKE '%@auth207.test'")
        jdbc.update("DELETE FROM email_verifications WHERE email LIKE '%@auth207.test'")
        jdbc.update("DELETE FROM terms WHERE id BETWEEN 990211 AND 990215")
        jdbc.update("DELETE FROM instruments WHERE id IN (990201, 990202)")
        jdbc.update("DELETE FROM pitches WHERE id = 990201")
    }

    @Test
    fun `verified sign up creates the member logs in with cookies and consumes the token once`() {
        val token = verify("New@auth207.test")
        val response = signUp("New@auth207.test", token)
        assertEquals(201, response.statusCode(), response.body())
        val result = tree(response).path("result")
        assertEquals("CREATED", tree(response).path("httpStatus").asString())
        assertEquals("990201", result.path("instrumentId").asString())
        assertEquals("HANJA", result.path("notationMode").asString())
        assertEquals("2026-10-28T03:00:00Z", result.path("createdAt").asString())
        val userId = result.path("userId").asString().toLong()
        val cookies = response.headers().allValues("Set-Cookie")
        val access = cookies.single { it.startsWith("access_token=") }
        val refresh = cookies.single { it.startsWith("refresh_token=") }
        assertTrue(access.contains("Path=/api;") && access.contains("HttpOnly") && access.contains("SameSite=Lax"), access)
        assertTrue(refresh.contains("Path=/api/auth;"), refresh)
        assertFalse(response.body().contains(access.substringAfter('=').substringBefore(';')))

        val user = jdbc.queryForMap("SELECT email, password_hash, nickname, instrument_id, notation_mode, status FROM users WHERE id = ?", userId)
        assertEquals("new@auth207.test", user["email"])
        assertTrue((user["password_hash"] as String).startsWith("{argon2}\$argon2id\$"))
        assertEquals("ACTIVE", user["status"])
        assertEquals(listOf(990211L to true, 990212L to true, 990215L to false), jdbc.query(
            "SELECT term_id, agreed FROM user_term_agreements WHERE user_id = ? ORDER BY term_id", { rs, _ -> rs.getLong(1) to rs.getBoolean(2) }, userId,
        ))
        val refreshValue = refresh.substringAfter('=').substringBefore(';')
        assertEquals(sha256(refreshValue), jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String::class.java, userId))
        assertNotNull(jdbc.queryForObject("SELECT consumed_at FROM email_verifications WHERE email = 'new@auth207.test' AND token_hash IS NOT NULL", Any::class.java))

        assertError(signUp("New@auth207.test", token), 401, "EMAIL_NOT_VERIFIED")
    }

    @Test
    fun `required terms duplicate email and inactive instrument are rejected without writing`() {
        val token = verify("terms@auth207.test")
        assertError(signUp("terms@auth207.test", token, agreements = """[{"termId":"990212","version":"2026-10","agreed":true}]"""), 422, "TERMS_AGREEMENT_REQUIRED")
        assertError(signUp("terms@auth207.test", token, instrumentId = "990202"), 400, "INVALID_REQUEST")
        assertError(signUp("terms@auth207.test", token, password = "password"), 400, "INVALID_REQUEST")
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'terms@auth207.test'", Int::class.java))
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM email_verifications WHERE email = 'terms@auth207.test' AND token_hash IS NOT NULL", Any::class.java))

        jdbc.update("INSERT INTO users (email,password_hash,nickname,instrument_id,status) VALUES ('terms@auth207.test','test-hash','먼저',990201,'ACTIVE')")
        assertError(signUp("terms@auth207.test", token), 409, "EMAIL_ALREADY_EXISTS")
    }

    private fun verify(email: String): String {
        val sent = post("/api/auth/email-verifications", """{"email":"$email","purpose":"SIGN_UP"}""")
        assertEquals(202, sent.statusCode(), sent.body())
        val id = tree(sent).path("result").path("verificationId").asString()
        val confirmed = post("/api/auth/email-verifications/$id/confirm", """{"code":"${mail.messages.last()}"}""")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        return tree(confirmed).path("result").path("verificationToken").asString()
    }

    private fun signUp(
        email: String,
        token: String,
        password: String = "Example!234",
        agreements: String = """[{"termId":"990211","version":"2026-10","agreed":true},{"termId":"990212","version":"2026-10","agreed":true},{"termId":"990215","version":"2026-10","agreed":false}]""",
        instrumentId: String? = null,
    ) = post("/api/auth/sign-up", """{"email":"$email","password":"$password","nickname":"연습자","verificationToken":"$token","agreements":$agreements${instrumentId?.let { ""","instrumentId":"$it"""" } ?: ""}}""")

    private fun post(path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(),
    )
    private fun tree(response: HttpResponse<String>): JsonNode = json.readTree(response.body())
    private fun sha256(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
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
