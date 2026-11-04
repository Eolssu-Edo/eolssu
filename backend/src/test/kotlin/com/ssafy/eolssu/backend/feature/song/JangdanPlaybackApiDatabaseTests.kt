package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JangdanPlaybackApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newHttpClient()
    private val ownedIds = mutableListOf<Long>()
    private val fixtureIds = listOf(6L, 22L, 81L, 19L, 29L, 9L, 63L, 44L)
    private val manifest = json.readTree(requireNotNull(javaClass.getResourceAsStream("/catalog/jangdan-playback.json")))

    @BeforeEach fun prepare() {
        for (id in fixtureIds) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE id=?", Int::class.java, id))
            val source = source(id)
            val review = manifest.path("scores").first { it.path("songId").asString() == id.toString() }
            val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java,
                source.path("score").path("instrument").asString())
            jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,
                score_version,note_count,is_scorable,status,source) VALUES(?,?,NULL,?,1,?,?,?,?,FALSE,'PUBLISHED',?)""",
                id, instrument, source.path("score").path("title").asString(), review.path("defaultBpm").intValue(),
                json.writeValueAsString(source), review.path("scoreVersion").intValue(),
                source.path("events").count { it.path("kind").asString() == "NOTE" }, review.path("sourceId").asString())
            ownedIds += id
        }
    }

    @AfterEach fun cleanup() {
        ownedIds.forEach { jdbc.update("DELETE FROM songs WHERE id=?", it) }
        client.close()
    }

    @Test fun `public CAT04 survives MySQL JSON normalization and preserves FK BPM and source notes`() {
        for (id in fixtureIds) {
            val result = get(id)
            assertEquals(source(id), result.path("score"))
            assertEquals("QUARTER", result.path("bpmUnit").asString())
            assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=?", Long::class.java, id))
            if (id == 63L) {
                assertTrue(result.path("jangdanPlayback").isNull)
            } else {
                val expected = if (id in listOf(6L, 22L, 44L)) "2" else if (id in listOf(19L, 81L)) "3" else "1"
                assertEquals(expected, result.path("jangdanPlayback").path("jangdanId").asString())
                assertEquals(0L, result.path("jangdanPlayback").path("startTick").longValue())
            }
        }
        assertEquals(24L, get(6).path("jangdanPlayback").path("cycleTicks").longValue())
        assertEquals(24L, get(22).path("jangdanPlayback").path("cycleTicks").longValue())
        assertEquals(48L, get(19).path("jangdanPlayback").path("cycleTicks").longValue())
        assertEquals(24L, get(81).path("jangdanPlayback").path("cycleTicks").longValue())
    }

    @Test fun `changed BPM source identity or catalog pattern removes only the unreviewed playback`() {
        jdbc.update("UPDATE songs SET default_bpm=100 WHERE id=6")
        assertTrue(get(6).path("jangdanPlayback").isNull)
        jdbc.update("UPDATE songs SET default_bpm=99,score_json=JSON_SET(score_json,'$.provenance.review','changed') WHERE id=6")
        assertTrue(get(6).path("jangdanPlayback").isNull)
        val pattern = jdbc.queryForObject("SELECT pattern FROM jangdans WHERE id=2", String::class.java)
        try {
            jdbc.update("UPDATE jangdans SET pattern=JSON_SET(pattern,'$.beats[0]','쿵') WHERE id=2")
            assertTrue(get(22).path("jangdanPlayback").isNull)
            assertFalse(get(81).path("jangdanPlayback").isNull)
        } finally {
            jdbc.update("UPDATE jangdans SET pattern=? WHERE id=2", pattern)
        }
    }

    private fun source(id: Long): JsonNode = json.readTree(requireNotNull(javaClass.getResourceAsStream("/jangdan-playback/$id.json")))
    private fun get(id: Long): JsonNode {
        val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/songs/$id/score"))
            .header("Cookie", "access_token=malformed").GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result")
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
