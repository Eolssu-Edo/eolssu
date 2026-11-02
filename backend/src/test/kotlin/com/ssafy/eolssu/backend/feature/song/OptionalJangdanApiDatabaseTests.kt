package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OptionalJangdanApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var songs: SongMapper
    @Autowired lateinit var practice: PracticeSessionMapper
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val songId = 991339L

    // Exercise the proposed DDL on TEST_DB only until its Flyway version is assigned.
    @BeforeAll fun allowOptionalJangdan() {
        jdbc.execute(Files.readString(Path.of("../infra/catalog/proposals/optional_jangdan.sql")))
    }

    @BeforeEach fun prepare() {
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT ?,instrument_id,NULL,'장단 없는 곡',difficulty,default_bpm,
                JSON_SET(score_json,'$.music.jangdan','','$.music.bpmUnit','QUARTER'),score_version,note_count,TRUE,'PUBLISHED'
            FROM songs WHERE id=1""", songId)
        jdbc.update("""INSERT INTO song_model_audios(song_id,score_version,audio_url,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status)
            SELECT id,score_version,'https://media.example.test/optional-jangdan.m4a',default_bpm,'QUARTER',53050,125,'audio/mp4',?,'PUBLISHED'
            FROM songs WHERE id=?""", "b".repeat(64), songId)
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM song_model_audios WHERE song_id=?", songId)
        jdbc.update("DELETE FROM songs WHERE id=?", songId)
    }

    @AfterAll fun restoreSchema() {
        jdbc.execute("ALTER TABLE songs MODIFY COLUMN jangdan_id BIGINT NOT NULL")
        client.close()
    }

    @Test fun `published song without jangdan keeps canonical timing across catalog and practice queries`() {
        val detail = get("/api/songs/$songId")
        assertEquals(200, detail.statusCode(), detail.body())
        val item = json.readTree(detail.body()).path("result")
        assertTrue(item.path("jangdan").isNull)
        assertEquals("1", item.path("instrumentId").asString())
        assertTrue(item.path("difficulty").asInt() in 1..5)
        assertNull(assertNotNull(songs.findById(songId)).jangdanId)
        val list = json.readTree(get("/api/songs").body()).path("result").path("items")
        assertTrue(list.single { it.path("songId").asString() == songId.toString() }.path("jangdan").isNull)
        val score = get("/api/songs/$songId/score")
        assertEquals(200, score.statusCode(), score.body())
        val scoreResult = json.readTree(score.body()).path("result")
        assertEquals("QUARTER", scoreResult.path("bpmUnit").asString())
        assertEquals(item.path("score"), scoreResult.path("score"))
        val audio = get("/api/songs/$songId/model-audio")
        assertEquals(200, audio.statusCode(), audio.body())
        assertEquals("QUARTER", json.readTree(audio.body()).path("result").path("bpmUnit").asString())
        assertEquals("QUARTER", assertNotNull(practice.findPlayableSong(songId)).bpmUnit)
    }

    @Test fun `draft song without jangdan is absent from every public catalog path`() {
        jdbc.update("UPDATE songs SET status='DRAFT' WHERE id=?", songId)
        for (suffix in listOf("", "/score", "/model-audio")) {
            assertEquals(404, get("/api/songs/$songId$suffix").statusCode(), suffix)
        }
        val list = json.readTree(get("/api/songs").body()).path("result").path("items")
        assertFalse(list.any { it.path("songId").asString() == songId.toString() })
        assertNull(practice.findPlayableSong(songId))
        assertNull(assertNotNull(songs.findById(songId)).jangdanId)
    }

    @Test fun `missing or unsupported canonical timing is not invented when jangdan is absent`() {
        for (expression in listOf("JSON_REMOVE(score_json,'$.music.bpmUnit')", "JSON_SET(score_json,'$.music.bpmUnit','UNKNOWN')",
            "JSON_SET(score_json,'$.music.bpmUnit','quarter')")) {
            jdbc.update("UPDATE songs SET score_json=$expression WHERE id=?", songId)
            assertEquals(409, get("/api/songs/$songId/score").statusCode(), expression)
            assertEquals(404, get("/api/songs/$songId/model-audio").statusCode(), expression)
            assertNull(practice.findPlayableSong(songId))
        }
    }

    @Test fun `optional jangdan preserves required instrument difficulty and reference integrity`() {
        for (change in listOf("instrument_id=NULL", "difficulty=NULL", "difficulty=0", "difficulty=6", "jangdan_id=999999")) {
            assertFails(change) { jdbc.update("UPDATE songs SET $change WHERE id=?", songId) }
        }
    }

    private fun get(path: String) = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString())

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
