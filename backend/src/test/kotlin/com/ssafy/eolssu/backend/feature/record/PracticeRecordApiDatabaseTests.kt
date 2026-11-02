package com.ssafy.eolssu.backend.feature.record

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

private const val SECRET = "practice-record-test-signing-key-342"
private const val OWNER = 991342L
private const val OTHER = 991343L

// V6 시드 1번 곡 '아리랑'(악보 버전 1, 음표 61개)의 음표 16·17은 각 0, 18·19는 각 1이다(EVA-03 DB 검사와 같은 기준).
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PracticeRecordApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun fixtures() {
        cleanup()
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update(
                "INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','기록자',1,'HANGUL','ACTIVE')",
                id, "record$id@test.invalid",
            )
        }
        session(991342, OWNER, 16, 19, "DONE", scoreVersion = 1)
        session(991343, OWNER, 0, 60, "PENDING", scoreVersion = 1)
        session(991344, OTHER, 16, 19, "DONE", scoreVersion = 1)
        session(991345, OWNER, 16, 19, "DONE", scoreVersion = 1)
        session(991346, OWNER, 0, 60, "DONE", scoreVersion = 2)
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:05:00', record_deleted_at='2026-11-01 11:00:00' WHERE id=991345")
        for ((id, total) in listOf(991342L to 77, 991344L to 70, 991345L to 60, 991346L to 90)) {
            jdbc.update(
                "INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(?,?,?,?,?,?,'test','2026-11-01 10:01:00')",
                id, id, total, total, total, total,
            )
        }
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("DELETE FROM evaluations WHERE session_id BETWEEN 991342 AND 991346")
        jdbc.update("DELETE FROM practice_sessions WHERE id BETWEEN 991342 AND 991346")
        jdbc.update("DELETE FROM users WHERE id IN (991342,991343)")
    }

    @Test
    fun `first save keeps its time on later saves and the memo follows the body`() {
        val first = put(991342, OWNER, null)
        assertEquals(200, first.statusCode(), first.body())
        val saved = result(first)
        val savedAt = Instant.parse(saved.path("savedAt").asString())
        assertEquals(stored(991342, "saved_at"), savedAt)
        assertTrue(saved.path("memo").isNull)
        assertEquals(listOf("991342", "1", "아리랑"), listOf("sessionId", "songId", "songTitle").map { saved.path(it).asString() })
        assertEquals(listOf(0, 1, 77, 2876, 96), listOf("startColumnIdx", "endColumnIdx", "totalScore", "recordingDurationMs", "bpm").map { saved.path(it).asInt() })
        assertEquals(false, saved.path("isFullSong").asBoolean())
        assertEquals("2026-11-01T10:00:00Z", saved.path("startedAt").asString())

        val withMemo = result(put(991342, OWNER, """{"memo":"  3–4각에서 박자가 밀림 "}"""))
        assertEquals(savedAt, Instant.parse(withMemo.path("savedAt").asString()))
        assertEquals("3–4각에서 박자가 밀림", withMemo.path("memo").asString())
        assertEquals("3–4각에서 박자가 밀림", jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))

        assertEquals("3–4각에서 박자가 밀림", result(put(991342, OWNER, "{}")).path("memo").asString())
        assertTrue(result(put(991342, OWNER, """{"memo":null}""")).path("memo").isNull)
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))
        assertEquals(savedAt, stored(991342, "saved_at"))
    }

    @Test
    fun `full song record keeps column numbers empty when the score version changed`() {
        val record = result(put(991346, OWNER, null))
        assertEquals(true, record.path("isFullSong").asBoolean())
        assertTrue(record.path("startColumnIdx").isNull && record.path("endColumnIdx").isNull)
    }

    @Test
    fun `protects ownership, deletion and unfinished sessions without writing`() {
        assertEquals(401, put(991342, null, null).statusCode())
        for ((sessionId, expected) in listOf(991344L to 404, 991345L to 404, 999999L to 404, 991343L to 409)) {
            val response = put(sessionId, OWNER, """{"memo":"덮어쓰기"}""")
            assertEquals(expected, response.statusCode(), "$sessionId ${response.body()}")
        }
        assertEquals(400, put(0, OWNER, null).statusCode())
        assertEquals(400, put(991342, OWNER, """{"memo":"${"가".repeat(501)}"}""").statusCode())
        assertNull(jdbc.queryForObject("SELECT saved_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991344", String::class.java))
        assertNull(jdbc.queryForObject("SELECT saved_at FROM practice_sessions WHERE id=991343", LocalDateTime::class.java))
        assertEquals("2026-11-01T10:05:00Z", stored(991345, "saved_at").toString())
    }

    @Test
    fun `opening a saved record through the session lookup records the first view once`() {
        put(991342, OWNER, null)
        assertEquals(200, get(991342, OWNER).statusCode())
        val firstView = assertNotNull(jdbc.queryForObject("SELECT viewed_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertEquals(200, get(991342, OWNER).statusCode())
        assertEquals(firstView, jdbc.queryForObject("SELECT viewed_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertEquals(404, get(991345, OWNER).statusCode())
    }

    private fun session(id: Long, userId: Long, start: Int, end: Int, status: String, scoreVersion: Int) {
        jdbc.update(
            """INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,recording_url,recording_bytes,
               recording_duration_ms,recording_sample_rate,recording_channels,status,analysis_attempt,analysis_started_at,started_at,ended_at)
               VALUES(?,?,1,?,96,'DOTTED_QUARTER',?,?,?,1024,2876,44100,1,?,1,'2026-11-01 10:00:40','2026-11-01 10:00:00','2026-11-01 10:00:30')""",
            id, userId, scoreVersion, start, end, "recordings/$id/test.m4a", status,
        )
    }

    private fun stored(sessionId: Long, column: String): Instant =
        assertNotNull(jdbc.queryForObject("SELECT $column FROM practice_sessions WHERE id=?", LocalDateTime::class.java, sessionId)).toInstant(ZoneOffset.UTC)

    private fun result(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result")
    }

    private fun put(sessionId: Long, user: Long?, body: String?): HttpResponse<String> {
        val request = authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId/record"), user)
        if (body == null) request.PUT(HttpRequest.BodyPublishers.noBody())
        else request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body))
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(sessionId: Long, user: Long?): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun authorized(uri: URI, user: Long?): HttpRequest.Builder {
        val request = HttpRequest.newBuilder(uri)
        if (user != null) {
            val jwt = SignedJWT(
                JWSHeader(JWSAlgorithm.HS256),
                JWTClaimsSet.Builder().subject(user.toString()).expirationTime(Date.from(Instant.now().plusSeconds(60))).build(),
            )
            jwt.sign(MACSigner(SECRET.toByteArray()))
            request.header("Cookie", "access_token=${jwt.serialize()}")
        }
        return request
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
