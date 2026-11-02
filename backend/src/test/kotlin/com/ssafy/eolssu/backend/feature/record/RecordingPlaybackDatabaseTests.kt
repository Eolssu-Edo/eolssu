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
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.test.assertEquals
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
import tools.jackson.databind.json.JsonMapper

private const val PLAYBACK_SECRET = "practice-recording-playback-test-key-342"
private const val LISTENER = 991360L
private const val RECORDING_KEY = "recordings/991360/00000000-0000-4000-8000-000000991360.m4a"

// 운영과 같은 S3 호환 저장소(R2)일 때의 다시 듣기와 삭제 실패. 서명은 로컬 계산이라 저장소에 연결하지 않고,
// 엔드포인트는 아무도 받지 않는 로컬 포트라 삭제는 저장소 연결 실패가 된다.
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordingPlaybackDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun fixtures() {
        cleanup()
        jdbc.update(
            "INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','재생자',1,'HANGUL','ACTIVE')",
            LISTENER, "playback$LISTENER@test.invalid",
        )
        jdbc.update(
            """INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,recording_url,recording_bytes,
               recording_duration_ms,recording_sample_rate,recording_channels,status,analysis_attempt,analysis_started_at,started_at,ended_at,saved_at,memo)
               VALUES(991360,?,1,1,96,'DOTTED_QUARTER',16,19,?,1024,2876,44100,1,'DONE',1,'2026-11-01 10:00:40','2026-11-01 10:00:00','2026-11-01 10:00:30',
               '2026-11-01 10:05:00','남길 메모')""",
            LISTENER, RECORDING_KEY,
        )
        jdbc.update(
            "INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(991360,991360,77,77,77,77,'test','2026-11-01 10:01:00')",
        )
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("DELETE FROM evaluations WHERE session_id=991360")
        jdbc.update("DELETE FROM practice_sessions WHERE id=991360")
        jdbc.update("DELETE FROM users WHERE id=?", LISTENER)
    }

    @Test
    fun `signs a fifteen minute address on the storage for the member's recording`() {
        val before = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val response = send("GET", "recording")
        val after = Instant.now()
        assertEquals(200, response.statusCode(), response.body())
        val playback = json.readTree(response.body()).path("result")
        val url = URI(playback.path("playbackUrl").asString())
        assertEquals("127.0.0.1:9", url.authority)
        assertEquals("/eolssu-recordings-test/$RECORDING_KEY", url.path)
        val query = url.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("900", query["X-Amz-Expires"])
        assertTrue(query["X-Amz-Signature"].orEmpty().isNotEmpty())
        val expiresAt = Instant.parse(playback.path("urlExpiresAt").asString())
        assertTrue(expiresAt >= before.plusSeconds(900) && expiresAt <= after.plusSeconds(900), "$expiresAt")
    }

    @Test
    fun `deletion keeps the record when the storage cannot be reached`() {
        val response = send("DELETE", "record")
        assertEquals(503, response.statusCode(), response.body())
        assertEquals("SERVICE_UNAVAILABLE", json.readTree(response.body()).path("result").path("errorCode").asString())
        assertEquals(
            listOf(RECORDING_KEY, "남길 메모"),
            listOf("recording_url", "memo").map { jdbc.queryForObject("SELECT $it FROM practice_sessions WHERE id=991360", String::class.java) },
        )
        assertNull(jdbc.queryForObject("SELECT record_deleted_at FROM practice_sessions WHERE id=991360", LocalDateTime::class.java))
    }

    private fun send(method: String, resource: String): HttpResponse<String> {
        val jwt = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder().subject(LISTENER.toString()).expirationTime(Date.from(Instant.now().plusSeconds(60))).build(),
        )
        jwt.sign(MACSigner(PLAYBACK_SECRET.toByteArray()))
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/991360/$resource"))
            .header("Cookie", "access_token=${jwt.serialize()}")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { PLAYBACK_SECRET }
            registry.add("eolssu.recording-storage.type") { "s3" }
            registry.add("eolssu.recording-storage.endpoint") { "http://127.0.0.1:9" }
            registry.add("eolssu.recording-storage.bucket") { "eolssu-recordings-test" }
            registry.add("eolssu.recording-storage.access-key-id") { "test-access-key" }
            registry.add("eolssu.recording-storage.secret-access-key") { "test-secret-key" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
