package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
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
class ModelAudioApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val songId = 991288L
    private val objectKey = "model-audio/drafts/${"b".repeat(64)}/cases/danso-001-p1/danso.m4a"

    @BeforeEach fun prepare() {
        jdbc.update("""INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT ?,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status FROM songs WHERE id=1""", songId)
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM song_model_audios WHERE song_id=?",songId)
        jdbc.update("DELETE FROM songs WHERE id=?",songId)
        client.close()
    }

    @Test fun `only current published media can be retrieved anonymously`() {
        assertEquals(404, get().statusCode())
        jdbc.update("""INSERT INTO song_model_audios(song_id,score_version,object_key,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256)
            SELECT s.id,s.score_version,?,s.default_bpm,j.bpm_unit,53050,125,'audio/mp4',?
            FROM songs s JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=?""", objectKey,"a".repeat(64),songId)
        assertEquals(404,get().statusCode()) // DRAFT defaults are not public
        jdbc.update("UPDATE song_model_audios SET status='PUBLISHED' WHERE song_id=?",songId)
        val response=get()
        assertEquals(200,response.statusCode(),response.body())
        val result=json.readTree(response.body()).path("result")
        assertEquals(songId.toString(),result.path("songId").asString())
        assertEquals(53050,result.path("durationMs").asInt())
        assertEquals(125,result.path("startOffsetMs").asInt())
        assertEquals("audio/mp4",result.path("contentType").asString())
        assertEquals("a".repeat(64),result.path("sha256").asString())
        assertEquals("/api/songs/$songId/model-audio/play",result.path("audioUrl").asString())
        assertEquals(503,get("/play").statusCode()) // no storage settings in isolated DB tests

        for (change in listOf("score_version=score_version+1", "default_bpm=default_bpm+1", "status='HIDDEN'", "deleted_at=CURRENT_TIMESTAMP",
            "score_json=JSON_SET(score_json,'$.score.scoreVersion',999)",
            "score_json=JSON_SET(score_json,'$.music.bpmUnit','QUARTER')")) {
            jdbc.update("UPDATE songs SET $change WHERE id=?",songId)
            assertEquals(404,get().statusCode(),change)
            assertEquals(404,get("/play").statusCode(),change)
            assertEquals(404,get("/performance").statusCode(),change)
            jdbc.update("""UPDATE songs s JOIN songs original ON original.id=1
                SET s.score_version=original.score_version,s.score_json=original.score_json,s.default_bpm=original.default_bpm,s.status='PUBLISHED',s.deleted_at=NULL WHERE s.id=?""",songId)
        }
        jdbc.update("UPDATE song_model_audios SET bpm_unit='QUARTER' WHERE song_id=?",songId)
        assertEquals(404,get().statusCode())
    }

    @Test fun `database rejects invalid duration and unavailable song remains 404`() {
        assertFails { jdbc.update("""INSERT INTO song_model_audios(song_id,score_version,object_key,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256)
            VALUES(?,1,?,96,'DOTTED_QUARTER',0,0,'audio/mp4',?)""",songId,objectKey,"a".repeat(64)) }
        assertEquals(404,get().statusCode())
    }

    private fun get(suffix: String="") = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/songs/$songId/model-audio$suffix"))
        .header("Cookie","access_token=invalid-public-cookie").GET().build(),HttpResponse.BodyHandlers.ofString())
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
