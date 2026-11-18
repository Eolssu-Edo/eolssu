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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val TEST_SECRET = "test-only-signing-key-32-bytes-1234567890"

/** 실제 HTTP·JWT 쿠키·MySQL로 GRO-01 summary, GRO-02 calendar, GRO-03 scores를 확인한다. 넣은 행은 끝에서 지운다. */
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
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (951, 'GRO_TEST', -2, '黃', '시험', -24)")
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
                "VALUES (951, 951, 951, '시험', 1, 60, '{}', 4, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)), " +
                "(952, 951, 951, '둘째', 1, 60, '{}', 2, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", // 음표 4개·2개
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
                "bestBySong":[],
                "metricGrowth":{"songId":null,"title":null,"bpm":null,"pitch":{"first":null,"last":null},
                "timing":{"first":null,"last":null},"duration":{"first":null,"last":null}}}}""",
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
    fun `accepts 366 inclusive days at the lowest year and rejects 367 days`() {
        // 시작일은 KST 1001-01-01, 직전 366일의 조회 하한은 UTC 0999-12-30 15:00:00이다.
        jdbc.update(
            "INSERT INTO score_views (user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds) VALUES " +
                "(951, 951, 'boundary-previous', '1000-01-01 00:00:00', '1000-01-01 00:01:00', 60, 20), " +
                "(951, 951, 'boundary-before', '1000-12-31 14:59:59', '1000-12-31 15:00:59', 60, 10), " +
                "(951, 951, 'boundary-start', '1000-12-31 15:00:00', '1000-12-31 15:01:00', 60, 60), " +
                "(951, 951, 'boundary-after', '1002-01-01 15:00:00', '1002-01-01 15:01:00', 60, 40)",
        )

        val growthPath = "/api/users/me/growth"
        val acceptedRange = "from=1001-01-01&to=1002-01-01"
        val summary = get("$growthPath/summary?$acceptedRange", subject = "951")
        assertEquals(200, summary.statusCode(), summary.body())
        val totals = json.readTree(summary.body()).path("result")
        assertEquals(60, totals.path("practiceSeconds").asInt(-1))
        assertEquals(30, totals.path("previousPeriod").path("practiceSeconds").asInt(-1))

        val calendar = get("$growthPath/calendar?$acceptedRange", subject = "951")
        assertEquals(200, calendar.statusCode(), calendar.body())
        val days = json.readTree(calendar.body()).path("result").path("items")
        assertEquals(366, days.size())
        assertEquals(json.readTree("""{"date":"1001-01-01","practiceSeconds":60,"practiceCount":0}"""), days.get(0))
        assertEquals(json.readTree("""{"date":"1002-01-01","practiceSeconds":0,"practiceCount":0}"""), days.get(365))

        for (endpoint in listOf("summary", "calendar")) {
            val rejected = get("$growthPath/$endpoint?from=1001-01-01&to=1002-01-02", subject = "951")
            assertEquals(400, rejected.statusCode(), "$endpoint: ${rejected.body()}")
            assertEquals("INVALID_REQUEST", json.readTree(rejected.body()).path("result").path("errorCode").asString(), endpoint)
        }
    }

    @Test
    fun `reports best scores and metric growth from full song plays of all time`() {
        // 곡 951은 음표 4개(0~3), 곡 952는 2개(0~1). 전체 곡 연주는 첫 음부터 끝 음까지다.
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, score_version, bpm, bpm_unit, start_note_idx, end_note_idx, status, fail_reason, started_at) VALUES " +
                "(961, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-01 03:00:00'), " + // 기준 조건의 첫 연주
                "(962, 951, 951, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-02 03:00:00'), " + // 구간 연습
                "(963, 951, 951, 1, 90, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-07-31 03:00:00'), " + // 조건이 다른 더 이른 연주: BPM
                "(969, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-07-30 03:00:00'), " + // 채점 모델
                "(970, 951, 951, 2, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-07-29 03:00:00'), " + // 악보 버전
                "(960, 951, 951, 1, 60, 'DOTTED_QUARTER', 0, 3, 'DONE', NULL, '2026-07-28 03:00:00'), " + // BPM 단위
                "(964, 951, 952, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-04 03:00:00'), " +
                "(965, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-05 03:00:00'), " + // 가장 최근 평가된 전체 곡 연주
                "(966, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'FAILED', 'NO_SOUND', '2026-08-06 03:00:00'), " + // 평가 행이 있어도 FAILED는 빠진다
                "(959, 951, 951, 1, 60, 'JEONGGAN', 1, 3, 'DONE', NULL, '2026-08-09 03:00:00'), " + // 끝 음까지 간 구간 연습
                "(967, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-07 03:00:00'), " + // 평가 없음
                "(968, 952, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-08 03:00:00')", // 다른 회원
        )
        jdbc.update(
            "INSERT INTO evaluations (session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES " +
                "(961, 70, 60, 61, 62, 'pyin-1'), (962, 99, 99, 99, 99, 'pyin-1'), (963, 75, 10, 10, 10, 'pyin-1'), " +
                "(964, 88, 50, 50, 50, 'pyin-1'), (965, 80, 70, 71, 72, 'pyin-1'), (968, 100, 100, 100, 100, 'pyin-1'), " +
                "(969, 20, 20, 20, 20, 'pyin-0'), (970, 30, 30, 30, 30, 'pyin-1'), (960, 40, 40, 40, 40, 'pyin-1'), " +
                "(966, 100, 100, 100, 100, 'pyin-1'), (959, 98, 98, 98, 98, 'pyin-1')",
        )
        // 회원이 지운 기록도 성취도에 남는다.
        jdbc.update("UPDATE practice_sessions SET record_deleted_at = '2026-08-10 00:00:00' WHERE id = 965")

        // 성취도는 from·to와 관계없다.
        val response = get("/api/users/me/growth/summary?from=2026-10-01&to=2026-10-31", subject = "951")
        assertEquals(200, response.statusCode(), response.body())
        val result = json.readTree(response.body()).path("result")
        assertEquals(
            json.readTree("""[{"songId":"951","title":"시험","bestTotalScore":80},{"songId":"952","title":"둘째","bestTotalScore":88}]"""),
            result.path("bestBySong"),
        )
        assertEquals(
            json.readTree(
                """{"songId":"951","title":"시험","bpm":60,"pitch":{"first":60,"last":70},"timing":{"first":61,"last":71},
                "duration":{"first":62,"last":72}}""",
            ),
            result.path("metricGrowth"),
        )
        assertEquals(0, result.path("practiceCount").asInt()) // 10월 기간 값은 그대로
    }

    @Test
    fun `shows the recent scores of one song by scope from every counted play of the member`() {
        // 곡 951은 음표 4개(0~3)이고 지금 악보(버전 1)에서 각 0·0·1·1에 있다. 곡 952는 음표 2개(0~1).
        jdbc.update(
            "UPDATE songs SET score_json = ? WHERE id = 951",
            """{"events":[{"kind":"NOTE","cellId":"c0-j0"},{"kind":"NOTE","cellId":"c0-j1"},""" +
                """{"kind":"NOTE","cellId":"c1-j0"},{"kind":"NOTE","cellId":"c1-j1"}]}""",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, score_version, bpm, bpm_unit, start_note_idx, end_note_idx, status, fail_reason, started_at, saved_at) VALUES " +
                "(980, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-07-30 03:00:00', NULL), " + // 가장 이른 전체 곡 연주
                "(971, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-01 03:00:00', '2026-08-01 03:10:00'), " + // 저장한 기록
                "(972, 951, 951, 1, 90, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-02 03:00:00', NULL), " + // BPM이 달라도 넣는다
                "(973, 951, 951, 2, 60, 'DOTTED_QUARTER', 0, 3, 'DONE', NULL, '2026-08-03 03:00:00.123456', NULL), " + // 옛 악보 버전·지운 기록
                "(974, 951, 951, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-04 03:00:00', NULL), " + // 구간 연습
                "(975, 951, 951, 1, 60, 'JEONGGAN', 1, 3, 'DONE', NULL, '2026-08-05 03:00:00', NULL), " + // 끝 음까지 간 구간 연습, 가장 최근
                "(976, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'FAILED', 'NO_SOUND', '2026-08-06 03:00:00', NULL), " + // 평가 행이 있어도 FAILED는 빠진다
                "(977, 951, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-07 03:00:00', NULL), " + // 평가 없음
                "(978, 952, 951, 1, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-08 03:00:00', NULL), " + // 다른 회원
                "(982, 951, 952, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-02 12:00:00', NULL), " + // 둘째 곡: 같은 조건 세 번
                "(983, 951, 952, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-02 12:00:00', NULL), " + // 982와 같은 시각
                "(979, 951, 952, 1, 60, 'JEONGGAN', 0, 1, 'DONE', NULL, '2026-08-03 12:00:00', NULL)",
        )
        jdbc.update(
            "INSERT INTO evaluations (session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES " +
                "(980, 60, 61, 62, 63, 'pyin-1'), (971, 70, 71, 72, 73, 'pyin-1'), (972, 75, 76, 77, 78, 'pyin-1'), " +
                "(973, 80, 81, 82, 83, 'pyin-2'), (974, 99, 99, 99, 99, 'pyin-1'), (975, 98, 98, 98, 98, 'pyin-1'), " +
                "(976, 100, 100, 100, 100, 'pyin-1'), (978, 100, 100, 100, 100, 'pyin-1'), (982, 84, 84, 84, 84, 'pyin-1'), " +
                "(983, 86, 86, 86, 86, 'pyin-1'), " +
                "(979, 88, 88, 88, 88, 'pyin-1')",
        )
        jdbc.update("UPDATE practice_sessions SET record_deleted_at = '2026-08-10 00:00:00' WHERE id = 973")
        val songs = json.readTree(
            """[{"songId":"951","title":"시험","lastPlayedAt":"2026-08-05T03:00:00Z","fullPlayCount":4,"sectionPlayCount":2},
            {"songId":"952","title":"둘째","lastPlayedAt":"2026-08-03T12:00:00Z","fullPlayCount":3,"sectionPlayCount":0}]""",
        )

        // 곡을 고르지 않으면 가장 최근에 연주한 곡(구간 연습이어도)의 전체 곡 연주를 오래된 순서로 준다.
        val latest = scores("/api/users/me/growth/scores", subject = "951")
        assertEquals("951", latest.path("songId").asString())
        assertEquals("FULL", latest.path("scope").asString())
        assertEquals(listOf("980", "971", "972", "973"), latest.sessionIds())
        assertEquals(
            json.readTree(
                """{"sessionId":"973","startedAt":"2026-08-03T03:00:00.123456Z","bpm":60,"bpmUnit":"DOTTED_QUARTER",
                "startNoteIdx":0,"endNoteIdx":3,"startColumnIdx":null,"endColumnIdx":null,"scoreVersion":2,"modelVersion":"pyin-2",
                "totalScore":80,"pitchScore":81,"timingScore":82,"durationScore":83}""",
            ),
            latest.path("items").toList().last(),
        )
        assertEquals(listOf(0, 1, 0, 1, 0, 1, -1, -1), latest.columns()) // 지금 악보 버전(1)의 0~3음은 0~1각, 옛 버전은 없음
        assertEquals(false, latest.path("comparable").asBoolean())
        assertEquals(songs, latest.path("songs"))

        // 최근 limit개만, 여전히 오래된 순서로
        assertEquals(listOf("972", "973"), scores("/api/users/me/growth/scores?limit=2", subject = "951").sessionIds())

        // 구간 연습은 따로 본다. 범위가 다르면 비교할 수 없다.
        val sections = scores("/api/users/me/growth/scores?scope=SECTION", subject = "951")
        assertEquals(listOf("974", "975"), sections.sessionIds())
        assertEquals(listOf(0, 0, 0, 1), sections.columns())
        assertEquals(false, sections.path("comparable").asBoolean())

        // 다른 곡: 같은 조건끼리는 비교할 수 있다. 같은 시각이면 세션 ID 순서다.
        val second = scores("/api/users/me/growth/scores?songId=952", subject = "951")
        assertEquals(listOf("982", "983", "979"), second.sessionIds())
        assertEquals(true, second.path("comparable").asBoolean())

        // 연주한 적 없는 곡
        val unknown = scores("/api/users/me/growth/scores?songId=953", subject = "951")
        assertEquals("953", unknown.path("songId").asString())
        assertEquals(0, unknown.path("items").size())
        assertEquals(songs, unknown.path("songs"))

        // 다른 회원에게는 그 회원의 연주만 보인다.
        val other = scores("/api/users/me/growth/scores", subject = "952")
        assertEquals(listOf("978"), other.sessionIds())
        assertEquals(1, other.path("songs").size())
    }

    private fun JsonNode.sessionIds() = path("items").toList().map { it.path("sessionId").asString() }

    /** 점마다 시작·끝 각을 이어 붙인다. 각이 없으면 0으로 읽히지 않게 -1로 둔다. */
    private fun JsonNode.columns() = path("items").toList().flatMap {
        listOf(it.path("startColumnIdx").asInt(-1), it.path("endColumnIdx").asInt(-1))
    }

    @Test
    fun `defaults to a song practiced only in sections and shows those sections separately`() {
        jdbc.update(
            "INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, note_count, is_scorable, status, created_at, updated_at) " +
                "VALUES (954, 951, 951, '구간만', 1, 60, '{}', 4, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, start_note_idx, end_note_idx, status, fail_reason, started_at) VALUES " +
                "(984, 951, 951, 60, 'JEONGGAN', 0, 3, 'DONE', NULL, '2026-08-01 03:00:00'), " +
                "(985, 951, 954, 60, 'JEONGGAN', 1, 2, 'DONE', NULL, '2026-08-02 03:00:00')", // 가장 최근 연주는 구간 연습뿐인 곡
        )
        jdbc.update(
            "INSERT INTO evaluations (session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES " +
                "(984, 70, 70, 70, 70, 'pyin-1'), (985, 90, 90, 90, 90, 'pyin-1')",
        )

        val full = scores("/api/users/me/growth/scores", subject = "951")
        assertEquals("954", full.path("songId").asString())
        assertEquals(0, full.path("items").size())
        assertEquals(
            json.readTree(
                """[{"songId":"954","title":"구간만","lastPlayedAt":"2026-08-02T03:00:00Z","fullPlayCount":0,"sectionPlayCount":1},
                {"songId":"951","title":"시험","lastPlayedAt":"2026-08-01T03:00:00Z","fullPlayCount":1,"sectionPlayCount":0}]""",
            ),
            full.path("songs"),
        )
        assertEquals(listOf("985"), scores("/api/users/me/growth/scores?scope=SECTION", subject = "951").sessionIds())
    }

    @Test
    fun `has no score trend before any counted play`() {
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, status, fail_reason, started_at) VALUES " +
                "(981, 952, 951, 60, 'JEONGGAN', 3, 'FAILED', 'NO_SOUND', '2026-08-01 03:00:00')",
        )

        val empty = scores("/api/users/me/growth/scores?scope=SECTION", subject = "952")

        assertEquals(json.readTree("""{"songId":null,"scope":"SECTION","items":[],"comparable":false,"songs":[]}"""), empty)
    }

    @Test
    fun `requires a valid access token`() {
        for (path in listOf("/api/users/me/growth/calendar?from=2026-09-07&to=2026-09-13", "/api/users/me/growth/scores")) {
            val response = get(path, subject = null)
            assertEquals(401, response.statusCode(), path)
            assertTrue(response.body().contains("\"errorCode\":\"UNAUTHENTICATED\""), response.body())
        }
    }

    private fun scores(path: String, subject: String) = get(path, subject).let { response ->
        assertEquals(200, response.statusCode(), response.body())
        json.readTree(response.body()).path("result")
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
        jdbc.update("DELETE FROM evaluations WHERE session_id BETWEEN 951 AND 990")
        jdbc.update("DELETE FROM practice_sessions WHERE id BETWEEN 951 AND 990")
        jdbc.update("DELETE FROM score_views WHERE user_id IN (951, 952)")
        jdbc.update("DELETE FROM songs WHERE id IN (951, 952, 954)")
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
