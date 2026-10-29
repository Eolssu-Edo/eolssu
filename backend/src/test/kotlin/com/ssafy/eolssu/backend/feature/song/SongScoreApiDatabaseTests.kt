package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
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

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SongScoreApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM songs WHERE id BETWEEN 992880 AND 992883")
        client.close()
    }

    @Test fun `four seeded scores preserve JSON notes and tick timing over public HTTP`() {
        var noteCount = 0
        var breaths = 0
        for (id in 1..4) {
            val response = get("/api/songs/$id/score", "access_token=malformed")
            assertEquals(200, response.statusCode(), response.body())
            val result = json.readTree(response.body()).path("result")
            val source = json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=?", String::class.java, id))
            assertEquals(source, result.path("score"))
            assertEquals(source.path("score").path("scoreVersion").intValue(), result.path("scoreVersion").intValue())
            val ticksPerBeat = source.path("music").path("ticksPerBeat").intValue()
            var noteIdx = 0
            source.path("events").forEachIndexed { index, original ->
                val projected = result.path("events").get(index)
                assertEquals(original.path("id").asString(), projected.path("eventId").asString())
                assertEquals(original.path("startTick"), projected.path("startTick"))
                assertEquals(original.path("durationTick"), projected.path("durationTick"))
                assertEquals(original.path("startTick").intValue().toDouble() / ticksPerBeat, projected.path("onsetBeat").doubleValue())
                assertEquals(original.path("durationTick").intValue().toDouble() / ticksPerBeat, projected.path("durationBeat").doubleValue())
                if (original.path("kind").asString() == "NOTE") {
                    assertEquals(noteIdx++, projected.path("noteIdx").intValue())
                    val pitchId = jdbc.queryForObject("SELECT id FROM pitches WHERE yul=? AND octave=?", Long::class.java,
                        original.path("yul").asString(), original.path("octave").intValue())
                    assertEquals(pitchId.toString(), projected.path("pitchId").asString())
                } else {
                    assertTrue(projected.path("noteIdx").isNull)
                    assertTrue(projected.path("pitchId").isNull)
                }
                if (original.path("breathAfter").asBoolean()) breaths++
            }
            assertEquals(noteIdx, result.path("noteCount").intValue())
            noteCount += noteIdx
        }
        assertEquals(305, noteCount)
        assertEquals(17, breaths)
    }

    @Test fun `hidden deleted and absent scores stay unavailable`() {
        for ((id, state, deleted) in listOf(Triple(992880, "HIDDEN", "NULL"), Triple(992881, "PUBLISHED", "CURRENT_TIMESTAMP"))) {
            jdbc.update("""INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status,deleted_at)
                SELECT ?,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,?,$deleted FROM songs WHERE id=1""", id, state)
        }
        for (id in listOf(992880, 992881, 992883)) {
            val response = get("/api/songs/$id/score")
            assertEquals(404, response.statusCode(), response.body())
            assertEquals("RESOURCE_NOT_FOUND", json.readTree(response.body()).path("result").path("errorCode").asString())
        }
    }

    @Test fun `stored version or note count mismatch is a conflict and invalid input is 400`() {
        jdbc.update("""INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 992882,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version+1,note_count,is_scorable,status FROM songs WHERE id=1""")
        assertEquals(409, get("/api/songs/992882/score").statusCode())
        jdbc.update("UPDATE songs SET score_version=1,note_count=0 WHERE id=992882")
        assertEquals(409, get("/api/songs/992882/score").statusCode())
        for (id in listOf("0", "-1", "01", "99999999999999999999")) assertEquals(400, get("/api/songs/$id/score").statusCode())
    }

    private fun get(path: String, cookie: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET()
        if (cookie != null) request.header("Cookie", cookie)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
