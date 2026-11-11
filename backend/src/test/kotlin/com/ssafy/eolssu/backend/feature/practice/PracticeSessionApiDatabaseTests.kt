package com.ssafy.eolssu.backend.feature.practice

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.*
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
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

private const val JWT_SECRET = "practice-session-test-signing-key-217"
private const val OWNER = 991701L
private const val OTHER = 991702L
private const val SONG = 991701L
private const val HIDDEN_SONG = 991702L
private const val OTHER_SONG = 991703L

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PracticeSessionApiDatabaseTests.Fixtures::class)
class PracticeSessionApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var creation: com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionCreationRepository
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun before() {
        cleanup()
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (991701, 'TEST217', -2, '黃', '시험', -24)")
        jdbc.update("""INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active)
            VALUES (991701, 'TEST217', '테스트 악기', 991701, 991701, 50, FALSE)""")
        jdbc.update("""INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source)
            VALUES (991701, '테스트장단217', '{"beats":["덩"],"group":[1]}', 90, 'DOTTED_QUARTER', '테스트')""")
        for ((id, scorable, status) in listOf(Triple(SONG, true, "PUBLISHED"), Triple(HIDDEN_SONG, true, "HIDDEN"), Triple(OTHER_SONG, true, "PUBLISHED"))) {
            jdbc.update("""INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, score_version, note_count, is_scorable, status)
                VALUES (?, 991701, 991701, '테스트 곡', 1, 90, '{"notes":[]}', 3, 72, ?, ?)""", id, scorable, status)
        }
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update("""INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status)
                VALUES (?, ?, 'test-fixture-hash', '연습자', 991701, 'HANGUL', 'ACTIVE')""", id, "u$id@practice217.test")
        }
    }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM practice_sessions WHERE user_id IN (?, ?)", OWNER, OTHER)
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", OWNER, OTHER)
        jdbc.update("DELETE FROM songs WHERE id IN (?, ?, ?)", SONG, HIDDEN_SONG, OTHER_SONG)
        jdbc.update("DELETE FROM jangdans WHERE id = 991701")
        jdbc.update("DELETE FROM instruments WHERE id = 991701")
        jdbc.update("DELETE FROM pitches WHERE id = 991701")
    }

    @Test
    fun `creates a recording session with defaults from the song`() {
        val response = create("key-1", """{"songId":"$SONG","bpm":96}""")
        assertEquals(201, response.statusCode(), response.body())
        val body = tree(response)
        assertEquals("CREATED", body.path("httpStatus").asString())
        assertTrue(body.path("isSuccess").asBoolean())
        assertEquals(201, body.path("code").asInt())
        val result = body.path("result")
        val id = result.path("sessionId").asString().toLong()
        assertEquals(SONG.toString(), result.path("songId").asString())
        assertEquals("RECORDING", result.path("status").asString())
        assertEquals(96, result.path("bpm").asInt())
        assertEquals("DOTTED_QUARTER", result.path("bpmUnit").asString())
        assertEquals(0, result.path("startNoteIdx").asInt())
        assertEquals(71, result.path("endNoteIdx").asInt())
        assertEquals("2026-10-28T03:00:00.123456Z", result.path("startedAt").asString())
        val row = jdbc.queryForMap("SELECT user_id, song_id, status, score_version, bpm_unit, idempotency_key, request_hash, started_at, parent_session_id FROM practice_sessions WHERE id = ?", id)
        assertEquals(OWNER, (row["user_id"] as Number).toLong())
        assertEquals("RECORDING", row["status"])
        assertEquals(3, (row["score_version"] as Number).toInt())
        assertEquals("DOTTED_QUARTER", row["bpm_unit"])
        assertEquals("key-1", row["idempotency_key"])
        assertTrue((row["request_hash"] as String).matches(Regex("[0-9a-f]{64}")))
        assertEquals(LocalDateTime.parse("2026-10-28T03:00:00.123456"), row["started_at"])
        assertNull(row["parent_session_id"])
    }

    @Test
    fun `archive requires recorded version while legacy successful key still replays`() {
        val legacy = create("legacy", """{"songId":"$SONG","bpm":96}""")
        assertEquals(201, legacy.statusCode(), legacy.body())
        archiveAndPublish()
        for (field in listOf("", ",\"expectedScoreVersion\":null", ",\"expectedScoreVersion\":3")) {
            assertError(create("new-$field", """{"songId":"$SONG","bpm":96$field}"""), 409, "STATE_CONFLICT")
        }
        assertEquals(1, count(OWNER))
        val replay = create("legacy", """{"songId":"$SONG","bpm":96,"expectedScoreVersion":null}""")
        assertEquals(201, replay.statusCode(), replay.body())
        assertEquals(tree(legacy).path("result"), tree(replay).path("result"))
        val current = create("current", """{"songId":"$SONG","bpm":96,"expectedScoreVersion":4}""")
        assertEquals(201, current.statusCode(), current.body())
        assertEquals(2, count(OWNER))
    }

    @Test
    fun `rollback to a lower current score still requires an explicit recorded version`() {
        archiveAndPublish() // v3을 보관한 뒤 v4 공개
        jdbc.update("UPDATE songs SET score_version = 2 WHERE id = ?", SONG)
        assertTrue(creation.findPlayableSong(SONG)!!.hasArchivedScore)
        assertError(create("rollback-missing", """{"songId":"$SONG","bpm":96}"""), 409, "STATE_CONFLICT")
        assertError(create("rollback-null", """{"songId":"$SONG","bpm":96,"expectedScoreVersion":null}"""), 409, "STATE_CONFLICT")
        assertEquals(0, count(OWNER))
        assertEquals(201, create("rollback-current", """{"songId":"$SONG","bpm":96,"expectedScoreVersion":2}""").statusCode())
    }

    @Test
    fun `old parent score cannot start a new section`() {
        val parent = session(OWNER, SONG, "DONE")
        archiveAndPublish()
        assertError(create("old-parent", """{"songId":"$SONG","bpm":96,"expectedScoreVersion":4,"parentSessionId":"$parent"}"""), 409, "STATE_CONFLICT")
        assertEquals(1, count(OWNER))
    }

    @Test
    fun `insert does not persist the stale version after publication wins`() {
        val read = creation.findPlayableSong(SONG)!!
        assertFalse(read.hasArchivedScore)
        archiveAndPublish()
        assertTrue(creation.findPlayableSong(SONG)!!.hasArchivedScore)
        val inserted = creation.insert(com.ssafy.eolssu.backend.feature.practice.domain.NewPracticeSession(
            OWNER, SONG, null, read.scoreVersion, 96, read.bpmUnit, "racing", "hash", 0, 71, Instant.now(),
        ))
        assertNull(inserted)
        assertEquals(0, count(OWNER))
    }

    private fun archiveAndPublish() {
        jdbc.update("""INSERT INTO song_score_versions
            (song_id, score_version, instrument_id, jangdan_id, title, description, difficulty, default_bpm,
             score_json, umulsai_text, note_count, source, is_scorable, status, created_at, updated_at, deleted_at)
            SELECT id, score_version, instrument_id, jangdan_id, title, description, difficulty, default_bpm,
                   score_json, umulsai_text, note_count, source, is_scorable, status, created_at, updated_at, deleted_at
            FROM songs WHERE id = ?""", SONG)
        jdbc.update("UPDATE songs SET score_version = 4 WHERE id = ?", SONG)
    }

    @Test
    fun `same key replays the first session and a different body conflicts`() {
        val first = tree(create("key-1", """{"songId":"$SONG","bpm":96,"startNoteIdx":2}"""))
        val replay = create("key-1", """{ "startNoteIdx": 2, "jangdanSoundOn": false, "bpm": 96, "songId": "$SONG" }""")
        assertEquals(201, replay.statusCode(), replay.body())
        assertEquals(first.path("result"), tree(replay).path("result"))
        assertError(create("key-1", """{"songId":"$SONG","bpm":97,"startNoteIdx":2}"""), 409, "IDEMPOTENCY_CONFLICT")
        assertEquals(201, create("key-1", """{"songId":"$SONG","bpm":96,"startNoteIdx":2}""", OTHER).statusCode())
        assertEquals(1, count(OWNER))
        assertEquals(1, count(OTHER))
    }

    @Test
    fun `concurrent requests with the same key store one session`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val responses = (1..4).map { pool.submit<HttpResponse<String>> { create("race", """{"songId":"$SONG","bpm":96}""") } }.map { it.get(30, TimeUnit.SECONDS) }
            assertTrue(responses.all { it.statusCode() == 201 }, responses.joinToString { it.body() })
            assertEquals(1, responses.map { tree(it).path("result").path("sessionId").asString() }.distinct().size)
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, count(OWNER))
    }

    @Test
    fun `rejects missing login, header and malformed body without writing`() {
        assertError(post("""{"songId":"$SONG","bpm":96}""", mapOf("Idempotency-Key" to "k")), 401, "UNAUTHENTICATED")
        assertError(post("""{"songId":"$SONG","bpm":96}""", mapOf("Cookie" to "access_token=${token(OWNER)}")), 400, "INVALID_REQUEST")
        // 비ASCII 키는 Java HTTP 클라이언트가 보내기 전에 막아 여기서는 공백·길이만 확인한다(정규식 단위는 출력 가능 ASCII).
        for (key in listOf("x".repeat(101), "has space")) assertError(create(key, """{"songId":"$SONG","bpm":96}"""), 400, "INVALID_REQUEST")
        val bodies = listOf(
            "{}", "{", """{"songId":"$SONG"}""", """{"bpm":96}""", """{"songId":"abc","bpm":96}""", """{"songId":"0","bpm":96}""",
            """{"songId":"$SONG","bpm":19}""", """{"songId":"$SONG","bpm":241}""", """{"songId":"$SONG","bpm":96,"bpmUnit":"WHOLE"}""",
            """{"songId":"$SONG","bpm":96,"startNoteIdx":-1}""", """{"songId":"$SONG","bpm":96,"parentSessionId":"x"}""",
        )
        for ((index, body) in bodies.withIndex()) assertError(create("bad-$index", body), 400, "INVALID_REQUEST")
        assertEquals(0, count(OWNER))
        assertEquals(201, create("~".repeat(100), """{"songId":"$SONG","bpm":20}""").statusCode())
    }

    @Test
    fun `song, parent session and section rules`() {
        assertError(create("a", """{"songId":"$HIDDEN_SONG","bpm":96}"""), 404, "RESOURCE_NOT_FOUND")
        assertError(create("b", """{"songId":"999999999","bpm":96}"""), 404, "RESOURCE_NOT_FOUND")
        // 미션은 구현하지 않기로 했다. 예전 클라이언트가 보낸 missionId는 무시하고, 같은 키의 재전송에도 영향을 주지 않는다.
        val withMission = create("c", """{"songId":"$SONG","bpm":96,"missionId":"1"}""")
        assertEquals(201, withMission.statusCode(), withMission.body())
        val withoutMission = create("c", """{"songId":"$SONG","bpm":96}""")
        assertEquals(201, withoutMission.statusCode(), withoutMission.body())
        assertEquals(tree(withMission).path("result").path("sessionId"), tree(withoutMission).path("result").path("sessionId"))
        assertError(create("d", """{"songId":"$SONG","bpm":96,"startNoteIdx":10,"endNoteIdx":72}"""), 400, "INVALID_REQUEST")

        val own = session(OWNER, SONG, "DONE")
        val pending = session(OWNER, SONG, "PENDING")
        val otherSong = session(OWNER, OTHER_SONG, "DONE")
        val others = session(OTHER, SONG, "DONE")
        assertError(create("e", """{"songId":"$SONG","bpm":96,"parentSessionId":"$others"}"""), 404, "RESOURCE_NOT_FOUND")
        assertError(create("f", """{"songId":"$SONG","bpm":96,"parentSessionId":"$otherSong"}"""), 400, "INVALID_REQUEST")
        assertError(create("g", """{"songId":"$SONG","bpm":96,"parentSessionId":"$pending"}"""), 409, "STATE_CONFLICT")
        val created = create("h", """{"songId":"$SONG","bpm":96,"bpmUnit":"QUARTER","startNoteIdx":3,"endNoteIdx":3,"parentSessionId":"$own"}""")
        assertEquals(201, created.statusCode(), created.body())
        val id = tree(created).path("result").path("sessionId").asString().toLong()
        assertEquals(own, jdbc.queryForObject("SELECT parent_session_id FROM practice_sessions WHERE id = ?", Long::class.java, id))
        assertEquals("QUARTER", jdbc.queryForObject("SELECT bpm_unit FROM practice_sessions WHERE id = ?", String::class.java, id))
    }

    private fun session(userId: Long, songId: Long, status: String): Long {
        jdbc.update("""INSERT INTO practice_sessions (user_id, song_id, score_version, bpm, bpm_unit, end_note_idx, status, started_at)
            VALUES (?, ?, 3, 90, 'DOTTED_QUARTER', 71, ?, '2026-10-27 03:00:00')""", userId, songId, status)
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun count(userId: Long) = jdbc.queryForObject("SELECT COUNT(*) FROM practice_sessions WHERE user_id = ?", Int::class.java, userId)
    private fun create(key: String, body: String, userId: Long = OWNER) =
        post(body, mapOf("Idempotency-Key" to key, "Cookie" to "access_token=${token(userId)}"))

    private fun post(body: String, headers: Map<String, String>): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun tree(response: HttpResponse<String>): JsonNode = json.readTree(response.body())
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
        @Bean @Primary fun practiceSessionTestClock(): Clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00.123456789Z"), ZoneOffset.UTC)
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
