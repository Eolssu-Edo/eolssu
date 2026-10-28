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
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper

private const val TEST_SECRET = "test-only-signing-key-32-bytes-1234567890"

/** 실제 HTTP·JWT 쿠키·MySQL로 GRO-01 summary와 GRO-02 calendar를 확인한다. 넣은 행은 끝에서 지운다. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.security.jwt-secret=$TEST_SECRET"])
class GrowthApiDatabaseTests {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Value("\${local.server.port}")
    private var port = 0

    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun insertMembers() {
        deleteRows()
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (951, '황', 0, '黃', '황', 0)")
        jdbc.update(
            "INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active, created_at, updated_at) " +
                "VALUES (951, 'GROWTH_TEST', '시험', 951, 951, 50, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source, created_at, updated_at) " +
                "VALUES (951, '시험', '{}', 60, 'JEONGGAN', '시험', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        for (id in listOf(951, 952)) {
            jdbc.update(
                "INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'hash', '시험', 951, 'HANJA', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                id, "growth$id@example.test",
            )
        }
        jdbc.update(
            "INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, note_count, is_scorable, status, created_at, updated_at) " +
                "VALUES (951, 951, 951, '시험', 1, 60, '{}', 1, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
    }

    @AfterEach
    fun cleanUp() {
        deleteRows()
        client.close()
    }

    @Test
    fun `summarizes and lists practice of the signed in member by KST date`() {
        // 2026-09-07(월)~09-13(일) 주와 지난주(08-31~09-06). 시각은 UTC로 저장한다.
        jdbc.update(
            "INSERT INTO score_views (user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds) VALUES " +
                "(951, 951, 'sunday-before', '2026-09-06 14:59:59.999999', '2026-09-06 15:30:00', 1800, 100), " + // KST 09-06 23:59 → 지난주
                "(951, 951, 'monday-midnight', '2026-09-06 15:00:00', '2026-09-06 15:10:00', 600, 200), " + // KST 09-07 00:00
                "(951, 951, 'sunday-late', '2026-09-13 14:50:00', '2026-09-13 15:20:00', 1800, 1800), " + // KST 09-13 23:50, 30분
                "(951, 951, 'short-day', '2026-09-08 03:00:00', '2026-09-08 03:01:00', 59, 59), " + // 1분 미만이라 연습한 날 아님
                "(952, 951, 'other-member', '2026-09-08 03:00:00', '2026-09-08 04:00:00', 3600, 3600)",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, status, fail_reason, started_at) VALUES " +
                "(951, 951, 951, 60, 'JEONGGAN', 0, 'DONE', NULL, '2026-09-07 03:00:00'), " +
                "(952, 951, 951, 60, 'JEONGGAN', 0, 'DONE', NULL, '2026-09-07 04:00:00'), " +
                "(953, 951, 951, 60, 'JEONGGAN', 0, 'DONE', NULL, '2026-09-08 03:00:00'), " + // 평가 없음: 횟수에만
                "(954, 951, 951, 60, 'JEONGGAN', 0, 'FAILED', 'NO_SOUND', '2026-09-08 03:00:00'), " +
                "(955, 951, 951, 60, 'JEONGGAN', 0, 'DONE', NULL, '2026-09-01 03:00:00'), " + // 지난주
                "(956, 952, 951, 60, 'JEONGGAN', 0, 'DONE', NULL, '2026-09-08 03:00:00')",
        )
        jdbc.update(
            "INSERT INTO evaluations (session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES " +
                "(951, 85, 85, 85, 85, 'test'), (952, 86, 86, 86, 86, 'test'), (954, 10, 10, 10, 10, 'test'), " +
                "(955, 70, 70, 70, 70, 'test'), (956, 99, 99, 99, 99, 'test')",
        )

        val summary = get("/api/users/me/growth/summary?from=2026-09-07&to=2026-09-13", subject = "951")
        assertEquals(200, summary.statusCode(), summary.body())
        assertEquals(
            json.readTree(
                """{"httpStatus":"OK","isSuccess":true,"message":"연습 기록지 요약 조회 성공","code":200,"result":{
                "from":"2026-09-07","to":"2026-09-13","practiceCount":3,"practiceSeconds":2059,"practiceDays":2,"currentStreakDays":0,
                "averageTotalScore":86,"previousPeriod":{"practiceSeconds":100,"practiceCount":1,"averageTotalScore":70},
                "completedMissionCount":null,"totalMissionCount":null}}""",
            ),
            json.readTree(summary.body()),
        )

        val calendar = get("/api/users/me/growth/calendar?from=2026-09-06&to=2026-09-08", subject = "951")
        assertEquals(200, calendar.statusCode(), calendar.body())
        assertEquals(
            json.readTree(
                """[{"date":"2026-09-06","practiceSeconds":100,"practiceCount":0},{"date":"2026-09-07","practiceSeconds":200,"practiceCount":2},
                {"date":"2026-09-08","practiceSeconds":59,"practiceCount":1}]""",
            ),
            json.readTree(calendar.body()).path("result").path("items"),
        )
    }

    @Test
    fun `requires a valid access token`() {
        val response = get("/api/users/me/growth/calendar?from=2026-09-07&to=2026-09-13", subject = null)
        assertEquals(401, response.statusCode())
        assertTrue(response.body().contains("\"errorCode\":\"UNAUTHENTICATED\""), response.body())
    }

    private fun get(path: String, subject: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json").GET()
        if (subject != null) builder.header("Cookie", "access_token=${token(subject)}")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun token(subject: String): String = SignedJWT(
        JWSHeader(JWSAlgorithm.HS256),
        JWTClaimsSet.Builder().subject(subject).issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build(),
    ).apply { sign(MACSigner(TEST_SECRET.toByteArray(Charsets.UTF_8))) }.serialize()

    private fun deleteRows() {
        jdbc.update("DELETE FROM evaluations WHERE session_id BETWEEN 951 AND 956")
        jdbc.update("DELETE FROM practice_sessions WHERE id BETWEEN 951 AND 956")
        jdbc.update("DELETE FROM score_views WHERE user_id IN (951, 952)")
        jdbc.update("DELETE FROM songs WHERE id = 951")
        jdbc.update("DELETE FROM users WHERE id IN (951, 952)")
        jdbc.update("DELETE FROM jangdans WHERE id = 951")
        jdbc.update("DELETE FROM instruments WHERE id = 951")
        jdbc.update("DELETE FROM pitches WHERE id = 951")
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
