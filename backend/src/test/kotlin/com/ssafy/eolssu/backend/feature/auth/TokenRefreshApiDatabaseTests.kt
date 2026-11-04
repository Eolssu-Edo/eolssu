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

    @Test
    fun `sign out requires the access cookie revokes this device and clears cookies`() {
        // access_token 만료는 실제 시각으로 검증하므로 발급도 실제 시각으로 한다.
        clock.set(Instant.now())
        val login = signIn()
        val access = cookie(login, "access_token")
        val refresh = cookie(login, "refresh_token")
        assertError(post("/api/auth/sign-out", "refresh_token=$refresh"), 401, "UNAUTHENTICATED")

        val signedOut = post("/api/auth/sign-out", "access_token=$access; refresh_token=$refresh")
        assertEquals(200, signedOut.statusCode(), signedOut.body())
        assertTrue(tree(signedOut).path("result").path("signedOut").asBoolean())
        val cleared = signedOut.headers().allValues("Set-Cookie")
        assertEquals(setOf("access_token", "refresh_token"), cleared.map { it.substringBefore('=') }.toSet())
        assertTrue(cleared.all { it.contains("Max-Age=0") })
        assertEquals(listOf("SIGN_OUT"), reasons())
        assertError(refresh(refresh), 401, "UNAUTHENTICATED")
    }

    // 466: access_token은 로그인 세션에 묶여, 세션을 끊으면 만료 전이라도 바로 401이다.
    @Test
    fun `signing out rejects that device's access token at once while rotation keeps the session`() {
        clock.set(Instant.now())
        val login = signIn()
        val access = cookie(login, "access_token")
        assertEquals(200, me(access).statusCode())

        val rotated = refresh(cookie(login, "refresh_token"))
        assertEquals(200, rotated.statusCode(), rotated.body())
        assertEquals(200, me(access).statusCode(), "회전은 같은 세션이라 이전 access_token도 유효하다")
        val sessions = jdbc.queryForList("SELECT DISTINCT session_id FROM refresh_tokens WHERE user_id = ?", String::class.java, userId)
        assertEquals(1, sessions.size)

        val other = signIn()
        assertNotEquals(sessions.single(), jdbc.queryForObject("SELECT session_id FROM refresh_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1", String::class.java, userId))

        val signedOut = post("/api/auth/sign-out", "access_token=${cookie(rotated, "access_token")}; refresh_token=${cookie(rotated, "refresh_token")}")
        assertEquals(200, signedOut.statusCode(), signedOut.body())
        val rejected = me(access)
        assertError(rejected, 401, "UNAUTHENTICATED")
        assertTrue(rejected.headers().allValues("Set-Cookie").all { it.contains("Max-Age=0") })
        assertEquals(200, me(cookie(other, "access_token")).statusCode(), "다른 기기의 세션은 그대로다")
    }

    @Test
    fun `reuse detection rejects every access token of the member at once`() {
        clock.set(Instant.now())
        val first = signIn()
        val other = signIn()
        val firstRefresh = cookie(first, "refresh_token")
        // 캐시에 '활성'을 먼저 채워, 탐지가 커밋 뒤 캐시를 지우는지 본다.
        assertEquals(200, me(cookie(first, "access_token")).statusCode())
        assertEquals(200, me(cookie(other, "access_token")).statusCode())
        assertEquals(200, refresh(firstRefresh).statusCode())
        assertError(refresh(firstRefresh), 401, "UNAUTHENTICATED")
        assertError(me(cookie(first, "access_token")), 401, "UNAUTHENTICATED")
        assertError(me(cookie(other, "access_token")), 401, "UNAUTHENTICATED")
    }

    @Test
    fun `withdrawing rejects every access token of the member at once`() {
        clock.set(Instant.now())
        val first = signIn()
        val other = signIn()
        // /users/me는 컨트롤러가 회원 상태를 따로 보므로, 상태를 보지 않는 보호 API로 인증 필터만 확인한다.
        val sheets = { access: String -> client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me/sheets")).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json").header("Cookie", "access_token=$access").GET().build(), HttpResponse.BodyHandlers.ofString()) }
        assertEquals(200, sheets(cookie(other, "access_token")).statusCode())
        val withdrawn = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Cookie", "access_token=${cookie(first, "access_token")}")
                .method("DELETE", HttpRequest.BodyPublishers.ofString("""{"currentPassword":"Example!234"}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertError(sheets(cookie(other, "access_token")), 401, "UNAUTHENTICATED")
    }

    // 배포 도구는 마이그레이션 뒤 교체가 실패하면 JAR만 되돌린다. 이전 JAR이 session_id 없이 넣은 행도 재발급되어야 한다.
    @Test
    fun `a refresh token stored without a session by the previous release still rotates into a new session`() {
        clock.set(Instant.now())
        val token = "previous-release-refresh-token"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 1 DAY, UTC_TIMESTAMP(6))", userId, hash)
        val rotated = refresh(token)
        assertEquals(200, rotated.statusCode(), rotated.body())
        assertEquals(200, me(cookie(rotated, "access_token")).statusCode())
        assertEquals(listOf(null, false), jdbc.query("SELECT session_id FROM refresh_tokens WHERE user_id = ? ORDER BY id", { rs, _ -> rs.getString(1)?.isEmpty() }, userId))
    }

    @Test
    fun `access tokens issued before session binding stay valid until they expire`() {
        val key = javax.crypto.spec.SecretKeySpec("test-signing-key-at-least-32-bytes-long!!".toByteArray(), "HmacSHA256")
        val encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder(com.nimbusds.jose.jwk.source.ImmutableSecret(key))
        val now = Instant.now()
        val claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().subject(userId.toString()).issuedAt(now).expiresAt(now.plusSeconds(600)).build()
        val header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build()
        val legacy = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).tokenValue
        assertEquals(200, me(legacy).statusCode())
    }

    private fun me(access: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me")).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json").header("Cookie", "access_token=$access").GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

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
            // 466: 운영과 같은 캐시로, 세션을 끊으면 커밋 뒤 캐시가 지워져 바로 401인지 본다.
            registry.add("eolssu.auth.session-cache-ttl") { "10s" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
