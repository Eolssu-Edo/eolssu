package com.ssafy.eolssu.backend.feature.practice

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.*
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

private const val INACTIVE_CRITERIA = "eolssu.note-practice.criteria.NP493_INACTIVE"

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "$INACTIVE_CRITERIA.criteria-version=fixture", "$INACTIVE_CRITERIA.pitch-tolerance-cent=50", "$INACTIVE_CRITERIA.hold-ms=300",
    "$INACTIVE_CRITERIA.min-confidence=0.8", "$INACTIVE_CRITERIA.min-level-dbfs=-45", "$INACTIVE_CRITERIA.octave-must-match=true",
    "$INACTIVE_CRITERIA.tuning=NONE", "$INACTIVE_CRITERIA.max-tuning-offset-cent=50", "$INACTIVE_CRITERIA.sigimsae-mode=SKIP",
    "$INACTIVE_CRITERIA.hint-after-ms=8000",
])
class NotePracticeCriteriaApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = JsonMapper.builder().build()

    @BeforeEach fun fixtures() {
        cleanupRows()
        jdbc.update("INSERT INTO pitches(id,yul,octave,hanja,hangul,semitone) VALUES (994930,'NP493',0,'黃','기준',60)")
        for ((id, code, active) in listOf(Triple(UNREGISTERED, "NP493_UNREGISTERED", true), Triple(INACTIVE, "NP493_INACTIVE", false))) {
            jdbc.update("INSERT INTO instruments(id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active) " +
                "VALUES (?,?,'기준',994930,994930,50,?)", id, code, active)
        }
    }

    @AfterEach fun cleanup() { client.close(); cleanupRows() }

    private fun cleanupRows() {
        jdbc.update("DELETE FROM instruments WHERE id IN (?,?)", UNREGISTERED, INACTIVE)
        jdbc.update("DELETE FROM pitches WHERE id=994930")
    }

    @Test fun `each seeded instrument gets the draft through real HTTP even with a stale login cookie`() {
        val codes = listOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM")
        for (code in codes) {
            val id = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java, code)
            val response = get("/api/note-practice/criteria?instrumentId=$id", "access_token=expired.invalid.token")
            assertEquals(200, response.statusCode(), code)
            // 소금만 초안 그대로이고 단소(S15P31E102-666)·두 대금(S15P31E102-866)은 완화한 값이다.
            val relaxed = code != "SOGEUM"
            assertTrue(response.body().contains("\"pitchToleranceCent\":${if (relaxed) 70 else 50},"), response.body())
            assertTrue(response.body().contains("\"minLevelDbfs\":-45,"), response.body())
            val result = json.readTree(response.body()).path("result")
            assertEquals(id.toString(), result.path("instrumentId").asString())
            assertEquals(if (relaxed) "note-step-2" else "note-step-1", result.path("criteriaVersion").asString())
            assertEquals(if (relaxed) 200 else 300, result.path("holdMs").asInt())
            assertEquals(0.8, result.path("minConfidence").asDouble())
            assertTrue(result.path("octaveMustMatch").asBoolean())
            assertEquals("FIRST_NOTE", result.path("tuning").asString())
            assertEquals(if (relaxed) 100 else 50, result.path("maxTuningOffsetCent").asInt())
            assertEquals("MAIN_PITCH", result.path("sigimsaeMode").asString())
            assertEquals(8000, result.path("hintAfterMs").asInt())
        }
    }

    @Test fun `missing malformed and unavailable instruments use the common errors`() {
        for ((path, status, code) in listOf(
            Triple("/api/note-practice/criteria", 400, "INVALID_REQUEST"),
            Triple("/api/note-practice/criteria?instrumentId=abc", 400, "INVALID_REQUEST"),
            Triple("/api/note-practice/criteria?instrumentId=994939", 404, "RESOURCE_NOT_FOUND"),
            Triple("/api/note-practice/criteria?instrumentId=$UNREGISTERED", 404, "RESOURCE_NOT_FOUND"),
            Triple("/api/note-practice/criteria?instrumentId=$INACTIVE", 404, "RESOURCE_NOT_FOUND"),
        )) {
            val response = get(path)
            assertEquals(status, response.statusCode(), path)
            assertEquals(code, json.readTree(response.body()).path("result").path("errorCode").asString(), path)
        }
    }

    private fun get(path: String, cookie: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().timeout(Duration.ofSeconds(10))
        cookie?.let { request.header("Cookie", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private companion object {
        const val UNREGISTERED = 994931L
        const val INACTIVE = 994932L

        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
