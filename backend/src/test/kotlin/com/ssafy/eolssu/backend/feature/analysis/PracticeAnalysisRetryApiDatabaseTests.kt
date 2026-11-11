package com.ssafy.eolssu.backend.feature.analysis

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.analysis.application.AnalysisOnUpload
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val JWT_SECRET = "analysis-retry-test-signing-key-252"
private const val OWNER = 991801L
private const val OTHER = 991802L
private const val SONG = 991801L

/**
 * 분석 다시 시도 API를 실제 앱(보안 필터·Bean 연결·헤더 검증·MySQL)으로 부른다(S15P31E102-252).
 * 분석 실행기는 넘겨받은 세션만 적어 두는 대역으로 바꿔 AI를 부르지 않는다.
 */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PracticeAnalysisRetryApiDatabaseTests.Fixtures::class)
class PracticeAnalysisRetryApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var fixtures: Fixtures
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun before() {
        cleanup()
        fixtures.submitted.clear()
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (991801, 'TEST252', -2, '黃', '시험', -24)")
        jdbc.update("""INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active)
            VALUES (991801, 'TEST252', '테스트 악기', 991801, 991801, 50, FALSE)""")
        jdbc.update("""INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source)
            VALUES (991801, '테스트장단252', '{"beats":["덩"],"group":[1]}', 90, 'DOTTED_QUARTER', '테스트')""")
        jdbc.update("""INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, score_version, note_count, is_scorable, status)
            VALUES (?, 991801, 991801, '테스트 곡', 1, 90, '{"notes":[]}', 1, 72, TRUE, 'PUBLISHED')""", SONG)
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update("""INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status)
                VALUES (?, ?, 'test-fixture-hash', '연습자', 991801, 'HANGUL', 'ACTIVE')""", id, "u$id@retry252.test")
        }
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM practice_sessions WHERE user_id IN (?, ?)", OWNER, OTHER)
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", OWNER, OTHER)
        jdbc.update("DELETE FROM songs WHERE id = ?", SONG)
        jdbc.update("DELETE FROM jangdans WHERE id = 991801")
        jdbc.update("DELETE FROM instruments WHERE id = 991801")
        jdbc.update("DELETE FROM pitches WHERE id = 991801")
    }

    @Test
    fun `starts a new attempt of a timed out analysis and hands it to the analysis executor`() {
        val id = session(OWNER, "FAILED", failReason = "TIMEOUT", attempt = 1, startedAt = utcNow().minusMinutes(5))
        val before = utcNow()
        val response = retry(id)
        val after = utcNow()

        assertAcceptedPending(response, id)
        val row = row(id)
        assertEquals("PENDING", row["status"])
        assertNull(row["fail_reason"])
        assertEquals(2, (row["analysis_attempt"] as Number).toInt())
        val startedAt = row["analysis_started_at"] as LocalDateTime
        // 시도 시작은 분석의 마감 판정과 같은 서버 UTC 시계다. DB는 마이크로초까지 저장하므로 범위를 그만큼 넓혀 본다.
        assertTrue(startedAt in before.minusNanos(1_000)..after.plusNanos(1_000), "$startedAt not within $before..$after")
        assertEquals(listOf(id), fixtures.submitted)
    }

    @Test
    fun `answers an already pending session with the same 202 and no new attempt`() {
        val id = session(OWNER, "PENDING", attempt = 1, startedAt = utcNow())
        assertAcceptedPending(retry(id), id)
        assertEquals(1, (row(id)["analysis_attempt"] as Number).toInt())
        assertEquals(emptyList(), fixtures.submitted)
    }

    @Test
    fun `duplicate retry and owned status report the timing ID persisted for the accepted attempt`() {
        val id = session(OWNER, "FAILED", failReason = "TIMEOUT", attempt = 1, startedAt = utcNow().minusMinutes(5))
        val acceptedId = "b23ba41a-7e5e-4938-8b3b-93ce5ac25c95"
        val headers = mapOf("Idempotency-Key" to "retry-1", "Cookie" to "access_token=${token(OWNER)}")
        assertAcceptedPending(post(id.toString(), headers + (EvaluationTimingId.HEADER to acceptedId)), id)
        val duplicate = post(id.toString(), headers + (EvaluationTimingId.HEADER to "354505d7-5e9c-41fa-a4ba-3d0695f56cdc"))
        assertAcceptedPending(duplicate, id)
        assertEquals("already-pending", duplicate.headers().firstValue(EvaluationTimingId.RETRY_STATUS_HEADER).orElse(null))
        assertEquals(acceptedId, duplicate.headers().firstValue(EvaluationTimingId.HEADER).orElse(null))
        assertEquals(acceptedId, row(id)["analysis_timing_id"])
        assertEquals(listOf(id), fixtures.submitted)

        fun status(member: Long): HttpResponse<String> {
            val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/$id"))
                .timeout(Duration.ofSeconds(15)).header("Cookie", "access_token=${token(member)}").GET().build()
            return client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val owned = status(OWNER)
        assertEquals(200, owned.statusCode())
        assertEquals(acceptedId, owned.headers().firstValue(EvaluationTimingId.HEADER).orElse(null))
        val other = status(OTHER)
        assertEquals(404, other.statusCode())
        assertTrue(other.headers().firstValue(EvaluationTimingId.HEADER).isEmpty)

        // 구버전 서버는 진단 컬럼을 모르는 상태로 시도 번호만 올린다.
        jdbc.update("UPDATE practice_sessions SET analysis_attempt = analysis_attempt + 1 WHERE id = ?", id)
        assertTrue(status(OWNER).headers().firstValue(EvaluationTimingId.HEADER).isEmpty)
    }

    @Test
    fun `rejects sessions that cannot be retried and other members' sessions without changing them`() {
        val noSound = session(OWNER, "FAILED", failReason = "NO_SOUND", attempt = 1, startedAt = utcNow().minusMinutes(5))
        val done = session(OWNER, "DONE", attempt = 1, startedAt = utcNow().minusMinutes(5))
        val recording = session(OWNER, "RECORDING", recorded = false)
        for (id in listOf(noSound, done, recording)) assertError(retry(id), 409, "STATE_CONFLICT")

        val others = session(OTHER, "FAILED", failReason = "TIMEOUT", attempt = 1, startedAt = utcNow().minusMinutes(5))
        assertError(retry(others), 404, "RESOURCE_NOT_FOUND")
        assertError(retry(999_999_999), 404, "RESOURCE_NOT_FOUND")

        assertEquals(listOf("FAILED", "DONE", "RECORDING", "FAILED"), listOf(noSound, done, recording, others).map { row(it)["status"] })
        assertEquals(1, (row(others)["analysis_attempt"] as Number).toInt())
        assertEquals(emptyList(), fixtures.submitted)
    }

    @Test
    fun `requires login, a valid Idempotency-Key and a positive session id`() {
        val id = session(OWNER, "FAILED", failReason = "TIMEOUT", attempt = 1, startedAt = utcNow().minusMinutes(5))
        assertError(post(id.toString(), mapOf("Idempotency-Key" to "k")), 401, "UNAUTHENTICATED")
        assertError(post(id.toString(), mapOf("Cookie" to "access_token=${token(OWNER)}")), 400, "INVALID_REQUEST")
        for (key in listOf("x".repeat(101), "has space")) assertError(retry(id, key), 400, "INVALID_REQUEST")
        for (path in listOf("0", "-1", "abc")) {
            assertError(post(path, mapOf("Idempotency-Key" to "k", "Cookie" to "access_token=${token(OWNER)}")), 400, "INVALID_REQUEST")
        }
        assertEquals(1, (row(id)["analysis_attempt"] as Number).toInt())
        assertEquals(emptyList(), fixtures.submitted)
    }

    private fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private fun session(
        userId: Long, status: String, failReason: String? = null, attempt: Int = 0, startedAt: LocalDateTime? = null, recorded: Boolean = true,
    ): Long {
        jdbc.update(
            """INSERT INTO practice_sessions (user_id, song_id, bpm, bpm_unit, end_note_idx, status, fail_reason, analysis_attempt, analysis_started_at,
                recording_url, started_at)
            VALUES (?, ?, 90, 'DOTTED_QUARTER', 71, ?, ?, ?, ?, ?, '2026-10-29 00:00:00')""",
            userId, SONG, status, failReason, attempt, startedAt, if (recorded) "recordings/test-252.m4a" else null,
        )
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun row(id: Long) =
        jdbc.queryForMap("SELECT status, fail_reason, analysis_attempt, analysis_started_at, analysis_timing_id FROM practice_sessions WHERE id = ?", id)

    private fun retry(id: Long, key: String = "retry-1") =
        post(id.toString(), mapOf("Idempotency-Key" to key, "Cookie" to "access_token=${token(OWNER)}"))

    // 본문 없는 POST. Content-Type이 없어도 상태 변경 요청 필터를 통과해야 한다.
    private fun post(sessionId: String, headers: Map<String, String>): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/$sessionId/retry")).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.noBody())
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun tree(response: HttpResponse<String>): JsonNode = json.readTree(response.body())

    private fun assertAcceptedPending(response: HttpResponse<String>, id: Long) {
        assertEquals(202, response.statusCode(), response.body())
        val body = tree(response)
        assertEquals("ACCEPTED", body.path("httpStatus").asString())
        assertTrue(body.path("isSuccess").asBoolean())
        assertEquals(202, body.path("code").asInt())
        assertEquals(id.toString(), body.path("result").path("sessionId").asString())
        assertEquals("PENDING", body.path("result").path("status").asString())
        assertFalse(body.path("result").path("retryable").asBoolean())
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertFalse(tree(response).path("isSuccess").asBoolean())
        assertEquals(code, tree(response).path("result").path("errorCode").asString())
    }

    private fun token(userId: Long): String = SignedJWT(
        JWSHeader(JWSAlgorithm.HS256),
        JWTClaimsSet.Builder().subject(userId.toString()).issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build(),
    ).apply { sign(MACSigner(JWT_SECRET.toByteArray())) }.serialize()

    class Fixtures {
        val submitted: MutableList<Long> = Collections.synchronizedList(mutableListOf())

        // 다시 시도가 새 시도를 넘기는 실행기. 실제 분석(AI 호출) 대신 넘겨받은 세션만 적는다.
        @Bean @Primary fun recordingAnalysisOnUpload() = AnalysisOnUpload({ sessionId, _, _ -> submitted += sessionId }, { it.run() })
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { JWT_SECRET }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
