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
    private var ownsSong = false
    private val source = json.readTree(requireNotNull(javaClass.getResourceAsStream("/jangdan-playback/6.json")))

    @BeforeEach fun prepare() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE id=6", Int::class.java))
        val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java,
            source.path("score").path("instrument").asString())
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,
            score_version,note_count,is_scorable,status,source) VALUES(6,?,NULL,?,1,99,?,1,?,FALSE,'PUBLISHED',?)""",
            instrument, source.path("score").path("title").asString(), json.writeValueAsString(source),
            source.path("events").count { it.path("kind").asString() == "NOTE" }, source.path("score").path("sourceId").asString())
        ownsSong = true
    }

    @AfterEach fun cleanup() {
        if (ownsSong) jdbc.update("DELETE FROM songs WHERE id=6")
        client.close()
    }

    @Test fun `public CAT04 survives MySQL JSON normalization and preserves FK BPM and source notes`() {
        val result = get()
        assertEquals(source, result.path("score"))
        assertEquals(99, result.path("defaultBpm").intValue())
        assertEquals("QUARTER", result.path("bpmUnit").asString())
        assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=6", Long::class.java))
        val playback = result.path("jangdanPlayback")
        assertEquals("2", playback.path("jangdanId").asString())
        assertEquals(0L, playback.path("startTick").longValue())
        assertEquals(24L, playback.path("cycleTicks").longValue())
        assertEquals("더러러러", playback.path("pattern").path("beats").get(4).asString())
    }

    @Test fun `changed BPM source identity or catalog pattern removes only the unreviewed playback`() {
        jdbc.update("UPDATE songs SET default_bpm=100 WHERE id=6")
        assertTrue(get().path("jangdanPlayback").isNull)
        jdbc.update("UPDATE songs SET default_bpm=99,score_json=JSON_SET(score_json,'$.provenance.review','changed') WHERE id=6")
        assertTrue(get().path("jangdanPlayback").isNull)
        jdbc.update("UPDATE songs SET score_json=? WHERE id=6", json.writeValueAsString(source))
        assertFalse(get().path("jangdanPlayback").isNull)
        val pattern = jdbc.queryForObject("SELECT pattern FROM jangdans WHERE id=2", String::class.java)
        try {
            jdbc.update("UPDATE jangdans SET pattern=JSON_SET(pattern,'$.beats[0]','쿵') WHERE id=2")
            assertTrue(get().path("jangdanPlayback").isNull)
        } finally {
            jdbc.update("UPDATE jangdans SET pattern=? WHERE id=2", pattern)
        }
        assertFalse(get().path("jangdanPlayback").isNull)
    }

    private fun get(): JsonNode {
        val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/songs/6/score"))
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
