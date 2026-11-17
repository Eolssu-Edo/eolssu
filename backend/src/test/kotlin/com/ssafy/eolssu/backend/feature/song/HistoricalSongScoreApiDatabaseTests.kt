package com.ssafy.eolssu.backend.feature.song

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.ScoreNotesException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
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
import tools.jackson.databind.node.ObjectNode

private const val SONG = 992707L
private const val OWNER = 992707L
private const val OTHER = 992708L
private const val SECRET = "historical-score-707-test-signing-key"
private const val ARCHIVE_COLUMNS = "song_id,score_version,instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,umulsai_text,note_count,source,is_scorable,status,created_at,updated_at,deleted_at"
private const val SOURCE_COLUMNS = "id,score_version,instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,umulsai_text,note_count,source,is_scorable,status,created_at,updated_at,deleted_at"

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalSongScoreApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var analyses: PracticeAnalysisRepository
    @Autowired lateinit var scoreNotes: PracticeScoreNotes
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val oldScore = json.readTree("""{"schemaVersion":1,
        "score":{"title":"원래 제목","sourceId":"historical-score-707","instrument":"DANSO","scoreVersion":1},
        "music":{"hwangHz":311.13,"jangdan":"","bpmUnit":"QUARTER","ticksPerBeat":4,"ticksPerJeonggan":6},
        "columns":[{"index":0,"cells":[
            {"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"황","eventId":"c0-j0-r0-u0"}]},
            {"id":"c0-j1","index":1,"units":[{"row":0,"col":0,"symbol":"황","eventId":"c0-j1-r0-u0"}]},
            {"id":"c0-j2","index":2,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j1-r0-u0"}]},
            {"id":"c0-j3","index":3,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j1-r0-u0"}]}]}],
        "events":[
            {"id":"c0-j0-r0-u0","kind":"NOTE","yul":"황","octave":0,"startTick":0,"durationTick":6,"cellId":"c0-j0","breathAfter":false,"sigimsae":[]},
            {"id":"c0-j1-r0-u0","kind":"NOTE","yul":"황","octave":0,"startTick":6,"durationTick":18,"cellId":"c0-j1","breathAfter":false,"sigimsae":[]}],
        "provenance":{"sourceId":"historical-score-707","review":"fixture","converter":"fixture"}}""")

    @BeforeEach fun prepare() {
        for (user in listOf(OWNER, OTHER)) jdbc.update("""INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status)
            VALUES(?,?,'fixture','판본 조회자',1,'HANGUL','ACTIVE')""", user, "history$user@test.invalid")
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,
            score_version,note_count,source,is_scorable,status,umulsai_text)
            VALUES(?,1,NULL,'원래 제목','원래 설명',1,90,?,1,2,'historical-score-707',FALSE,'PUBLISHED','원래 출처 표기')""", SONG, oldScore.toString())
        jdbc.update("INSERT INTO song_score_versions($ARCHIVE_COLUMNS) SELECT $SOURCE_COLUMNS FROM songs WHERE id=?", SONG)
        val current = oldScore.deepCopy() as ObjectNode
        (current.path("score") as ObjectNode).put("title", "새 제목").put("instrument", "SOGEUM").put("scoreVersion", 2)
        (current.path("music") as ObjectNode).put("ticksPerBeat", 6).put("jangdan", "단모리")
        jdbc.update("""UPDATE songs SET instrument_id=2,jangdan_id=7,title='새 제목',description='새 설명',difficulty=3,
            default_bpm=77,score_json=?,score_version=2,is_scorable=TRUE,umulsai_text='새 출처 표기' WHERE id=?""", current.toString(), SONG)
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE n FROM note_results n JOIN evaluations e ON e.id=n.evaluation_id JOIN practice_sessions s ON s.id=e.session_id WHERE s.user_id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE e FROM evaluations e JOIN practice_sessions s ON s.id=e.session_id WHERE s.user_id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM practice_sessions WHERE user_id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM songs WHERE id=?", SONG)
        client.close()
    }

    @Test fun `public current and exact historical edition keep their own metadata and original timeline`() {
        val current = result(get("/api/songs/$SONG"))
        assertEquals(2, current.path("scoreVersion").intValue())
        assertEquals(77, current.path("defaultBpm").intValue())
        assertEquals("새 제목", current.path("title").asString())
        val previous = result(get("/api/songs/$SONG?scoreVersion=1"))
        assertEquals(oldScore, previous.path("score"))
        assertEquals("1", previous.path("instrumentId").asString())
        assertEquals("원래 제목", previous.path("title").asString())
        assertEquals("원래 설명", previous.path("description").asString())
        assertEquals(1, previous.path("difficulty").intValue())
        assertEquals(90, previous.path("defaultBpm").intValue())
        assertFalse(previous.path("isScorable").booleanValue())
        assertTrue(previous.path("jangdan").isNull)
        val oldTimeline = result(get("/api/songs/$SONG/score?scoreVersion=1"))
        assertEquals(oldScore, oldTimeline.path("score"))
        assertEquals(1.5, oldTimeline.path("events").path(1).path("onsetBeat").doubleValue())
        assertEquals(4.5, oldTimeline.path("events").path(1).path("durationBeat").doubleValue())
        val newTimeline = result(get("/api/songs/$SONG/score?scoreVersion=2"))
        assertEquals(1.0, newTimeline.path("events").path(1).path("onsetBeat").doubleValue())
        assertEquals(3.0, newTimeline.path("events").path(1).path("durationBeat").doubleValue())
        for (path in listOf("/api/songs/$SONG", "/api/songs/$SONG/score")) {
            assertEquals(404, get("$path?scoreVersion=99").statusCode())
            for (invalid in listOf("0", "-1", "01", "bad", "2147483648")) assertEquals(400, get("$path?scoreVersion=$invalid").statusCode())
        }
        val listed = result(get("/api/songs")).path("items").filter { it.path("songId").asString() == SONG.toString() }
        assertEquals(1, listed.size)
        assertEquals(2, listed.single().path("scoreVersion").intValue())
    }

    @Test fun `each edition returns its own sigimsae slots on held cells`() {
        for ((table, key, cell) in listOf(Triple("songs", "id", "c0-j3"), Triple("song_score_versions", "song_id", "c0-j2"))) {
            jdbc.update("""UPDATE $table SET score_json=JSON_SET(score_json,'$.events[1].sigimsae',JSON_ARRAY('농음')),
                sigimsae_placements_json=? WHERE $key=?""", """[{"cellId":"$cell","row":0,"col":0,"symbols":["농음"]}]""", SONG)
        }
        for ((path, cell) in listOf("/api/songs/$SONG/score" to "c0-j3", "/api/songs/$SONG/score?scoreVersion=2" to "c0-j3",
            "/api/songs/$SONG/score?scoreVersion=1" to "c0-j2")) {
            val placements = result(get(path)).path("sigimsaePlacements")
            assertEquals(1, placements.size(), path)
            assertEquals(cell, placements.path(0).path("cellId").asString(), path)
            assertEquals("농음", placements.path(0).path("symbols").path(0).asString(), path)
        }
        jdbc.update("UPDATE songs SET sigimsae_placements_json=NULL WHERE id=?", SONG)
        assertTrue(result(get("/api/songs/$SONG/score")).path("sigimsaePlacements").isNull)
    }

    @Test fun `historical public reads do not expose hidden deleted or never published editions`() {
        for (change in listOf("status='HIDDEN'", "deleted_at=CURRENT_TIMESTAMP")) {
            jdbc.update("UPDATE songs SET $change WHERE id=?", SONG)
            for (path in listOf("/api/songs/$SONG?scoreVersion=1", "/api/songs/$SONG/score?scoreVersion=1")) assertEquals(404, get(path).statusCode())
            jdbc.update("UPDATE songs SET status='PUBLISHED',deleted_at=NULL WHERE id=?", SONG)
        }
        jdbc.update("UPDATE song_score_versions SET status='DRAFT' WHERE song_id=?", SONG)
        assertEquals(404, get("/api/songs/$SONG?scoreVersion=1").statusCode())
        assertEquals(404, get("/api/songs/$SONG/score?scoreVersion=1").statusCode())
    }

    @Test fun `a pending old analysis reads its archived beat scale and never substitutes the current score`() {
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,
            started_at,analysis_attempt,analysis_started_at,recording_url) VALUES(?,?,?,1,90,'QUARTER',0,1,'PENDING',
            CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'https://example.invalid/fixture-not-fetched')""", SONG, OWNER, SONG)
        val pending = assertNotNull(analyses.findPending(SONG, LocalDateTime.of(2000, 1, 1, 0, 0)))
        val notes = scoreNotes.notesFor(pending)
        assertEquals(1, pending.scoreVersion)
        assertEquals(90, pending.bpm)
        assertEquals(listOf(0.0, 1.5), notes.map { it.startBeat })
        assertEquals(listOf(1.5, 4.5), notes.map { it.beats })
        jdbc.update("DELETE FROM song_score_versions WHERE song_id=?", SONG)
        assertFailsWith<ScoreNotesException> { scoreNotes.notesFor(pending) }
    }

    @Test fun `old completed evaluations keep owned weak sections and comparison after current edition changes`() {
        done(SONG, 1)
        done(SONG + 1, 1, parent = SONG)
        val prefix = "/api/practice-sessions/${SONG + 1}"
        val evaluation = result(get("$prefix/evaluation", OWNER))
        assertEquals(1, evaluation.path("scoreVersion").intValue())
        assertEquals(90, evaluation.path("bpm").intValue())
        assertEquals(80, evaluation.path("totalScore").intValue())
        val detail = result(get(prefix, OWNER))
        assertEquals(1, detail.path("scoreVersion").intValue())
        assertEquals("원래 제목", detail.path("songTitle").asString())
        for (change in listOf("status='HIDDEN'", "deleted_at=CURRENT_TIMESTAMP")) {
            jdbc.update("UPDATE songs SET $change WHERE id=?", SONG)
            assertEquals("원래 제목", result(get(prefix, OWNER)).path("songTitle").asString())
            assertEquals(200, get("$prefix/evaluation", OWNER).statusCode())
            jdbc.update("UPDATE songs SET status='PUBLISHED',deleted_at=NULL WHERE id=?", SONG)
        }
        result(get("$prefix/weak-sections", OWNER))
        val comparison = result(get("$prefix/comparison?baselineSessionId=$SONG", OWNER))
        assertTrue(comparison.path("errorComparable").booleanValue())
        assertEquals(SONG.toString(), comparison.path("baselineSessionId").asString())
        assertEquals(404, get("$prefix/weak-sections", OTHER).statusCode())
        assertEquals(401, get("$prefix/comparison").statusCode())
        jdbc.update("DELETE FROM song_score_versions WHERE song_id=?", SONG)
        assertEquals(409, get("$prefix/weak-sections", OWNER).statusCode())
        assertEquals(409, get("$prefix/comparison", OWNER).statusCode())
        assertEquals(200, get("$prefix/evaluation", OWNER).statusCode())
        assertEquals("새 제목", result(get(prefix, OWNER)).path("songTitle").asString())
    }

    @Test fun `record save and list expose captured version and archived song metadata`() {
        done(SONG, 1)
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/$SONG/record"))
            .header("Content-Type", "application/json").header("Cookie", "access_token=${jwt(OWNER)}")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"memo":"기존 판본 메모"}""")).build()
        val saved = result(client.send(request, HttpResponse.BodyHandlers.ofString()))
        assertEquals(1, saved.path("scoreVersion").intValue())
        assertEquals("원래 제목", saved.path("songTitle").asString())
        assertEquals("기존 판본 메모", saved.path("memo").asString())
        val item = result(get("/api/users/me/practice-records?songId=$SONG", OWNER)).path("items").single()
        assertEquals(1, item.path("scoreVersion").intValue())
        assertEquals("원래 제목", item.path("title").asString())
        assertEquals(0, item.path("startColumnIdx").intValue())
        assertEquals(0, item.path("endColumnIdx").intValue())
    }

    private fun done(id: Long, version: Int, parent: Long? = null) {
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,parent_session_id,score_version,bpm,bpm_unit,
            start_note_idx,end_note_idx,status,started_at,ended_at) VALUES(?,?,?,?,?,90,'QUARTER',0,1,'DONE',?,CURRENT_TIMESTAMP)""",
            id, OWNER, SONG, parent, version, LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(id - SONG))
        jdbc.update("""INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at)
            VALUES(?,?,80,80,80,80,'fixture',CURRENT_TIMESTAMP)""", id, id)
        val pitch = jdbc.queryForObject("SELECT id FROM pitches WHERE yul='황' AND octave=0", Long::class.java)
        for ((idx, verdict) in listOf(0 to "CORRECT", 1 to "PITCH")) jdbc.update("""INSERT INTO note_results(evaluation_id,note_idx,
            expected_pitch_id,detected_pitch_id,pitch_error_cent,verdict) VALUES(?,?,?,?,0,?)""", id, idx, pitch, pitch, verdict)
    }

    private fun result(response: HttpResponse<String>): JsonNode {
        assertTrue(response.statusCode() in listOf(200, 201), response.body())
        return json.readTree(response.body()).path("result")
    }

    private fun jwt(user: Long): String {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().subject(user.toString())
            .expirationTime(Date.from(Instant.now().plusSeconds(60))).build())
        token.sign(MACSigner(SECRET.toByteArray()))
        return token.serialize()
    }

    private fun get(path: String, user: Long? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        user?.let { request.header("Cookie", "access_token=${jwt(it)}") }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("eolssu.security.jwt-secret") { SECRET }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
