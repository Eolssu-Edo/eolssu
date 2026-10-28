package com.ssafy.eolssu.backend.feature.growth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper

private const val TEST_SECRET = "test-only-signing-key-32-bytes-1234567890"

/** 실제 HTTP·로그인 쿠키·MySQL로 체류 기록 API(GRO-04)를 확인한다. 넣은 행은 커밋되므로 전후에 지운다. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.security.jwt-secret=$TEST_SECRET"])
@Import(ScoreViewApiDatabaseTests.FixedClockConfig::class)
class ScoreViewApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun seed() {
        cleanup()
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (990189, '검증', 0, '黃', '검증', 0)")
        jdbc.update("INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent) VALUES (990189, 'GRO04_TEST', '검증', 990189, 990189, 50)")
        jdbc.update("INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source) VALUES (990189, '검증', '{}', 60, 'JEONGGAN', '검증')")
        jdbc.update("INSERT INTO users (id, email, password_hash, nickname, instrument_id, status) VALUES (990189, 'member@gro04.test', 'test-hash', '검증', 990189, 'ACTIVE')")
        jdbc.update(
            "INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, note_count, is_scorable, status) " +
                "VALUES (990189, 990189, 990189, '검증', 1, 60, '{}', 1, TRUE, 'PUBLISHED')",
        )
    }

    @AfterEach
    fun after() {
        client.close()
        cleanup()
    }

    private fun cleanup() {
        jdbc.update("DELETE FROM score_views WHERE user_id = 990189")
        jdbc.update("DELETE FROM songs WHERE id = 990189")
        jdbc.update("DELETE FROM users WHERE id = 990189")
        jdbc.update("DELETE FROM jangdans WHERE id = 990189")
        jdbc.update("DELETE FROM instruments WHERE id = 990189")
        jdbc.update("DELETE FROM pitches WHERE id = 990189")
    }

    @Test
    fun `stores a segment once for the cookie member and compares retries with the stored row`() {
        val created = post(body())
        assertEquals(201, created.statusCode(), created.body())
        assertEquals(
            json.readTree(
                """{"httpStatus":"CREATED","isSuccess":true,"message":"체류 기록 저장 성공","code":201,"result":{"songId":"990189",
                "startedAt":"2026-10-28T05:00:00.123456Z","endedAt":"2026-10-28T05:05:00Z","visibleSeconds":300,"countedSeconds":240}}""",
            ),
            json.readTree(created.body()),
        )
        assertEquals(
            mapOf<String, Any>("user_id" to 990189L, "song_id" to 990189L, "visible_seconds" to 300, "counted_seconds" to 240),
            jdbc.queryForMap("SELECT user_id, song_id, visible_seconds, counted_seconds FROM score_views WHERE idempotency_key = 'gro04-key'"),
        )

        // 앱은 나노초까지 보낼 수 있다. 저장 정밀도(마이크로초)로 맞춘 뒤 같은 본문으로 본다.
        val retried = post(body())
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals("이미 저장된 체류 기록", json.readTree(retried.body()).path("message").asString())

        val conflict = post(body(countedSeconds = 200))
        assertEquals(409, conflict.statusCode(), conflict.body())
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", json.readTree(conflict.body()).path("result").path("errorCode").asString())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM score_views WHERE user_id = 990189", Int::class.java))
    }

    @Test
    fun `rejects requests without a login cookie and keeps nothing`() {
        val response = post(body(), cookie = null)
        assertEquals(401, response.statusCode(), response.body())
        assertEquals("UNAUTHENTICATED", json.readTree(response.body()).path("result").path("errorCode").asString())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM score_views WHERE user_id = 990189", Int::class.java))
    }

    @Test
    fun `returns 401 instead of 500 when the token member has no row`() {
        val response = post(body(), cookie = "access_token=${token(subject = "990188")}")
        assertEquals(401, response.statusCode(), response.body())
        assertEquals("UNAUTHENTICATED", json.readTree(response.body()).path("result").path("errorCode").asString())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM score_views WHERE user_id IN (990188, 990189)", Int::class.java))
    }

    @Test
    fun `returns 404 for a song without a row`() {
        val response = post(body(songId = "990188"))
        assertEquals(404, response.statusCode(), response.body())
        assertEquals("RESOURCE_NOT_FOUND", json.readTree(response.body()).path("result").path("errorCode").asString())
    }

    private fun body(songId: String = "990189", countedSeconds: Int = 240) =
        """{"songId":"$songId","startedAt":"2026-10-28T05:00:00.123456789Z","endedAt":"2026-10-28T05:05:00Z","visibleSeconds":300,"countedSeconds":$countedSeconds}"""

    private fun post(body: String, cookie: String? = "access_token=${token()}"): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me/score-views")).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json").header("Idempotency-Key", "gro04-key")
            .apply { if (cookie != null) header("Cookie", cookie) }
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun token(subject: String = "990189"): String = SignedJWT(
        JWSHeader(JWSAlgorithm.HS256),
        JWTClaimsSet.Builder().subject(subject).expirationTime(Date.from(Instant.now().plusSeconds(300))).build(),
    ).apply { sign(MACSigner(TEST_SECRET.toByteArray(Charsets.UTF_8))) }.serialize()

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfig {
        // 서버 수신 시각 검사(보인 시간 ≤ 수신 시각 − 시작 + 1초)를 고정한다.
        @Bean
        @Primary
        fun fixedScoreViewClock(): Clock = Clock.fixed(Instant.parse("2026-10-28T05:05:01Z"), ZoneOffset.UTC)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
