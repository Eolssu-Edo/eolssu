package com.ssafy.eolssu.backend.feature.evaluation

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.*
import java.net.URI
import java.net.http.*
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import tools.jackson.databind.json.JsonMapper

private const val SECRET="evaluation-test-signing-key-257-long"
@Tag("database") @ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PracticeEvaluationApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port=0
    private val client=HttpClient.newHttpClient()
    private val json=JsonMapper.builder().build()
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM note_results WHERE evaluation_id=991257")
        jdbc.update("DELETE FROM evaluations WHERE id=991257")
        jdbc.update("DELETE FROM practice_sessions WHERE id=991257")
        jdbc.update("DELETE FROM users WHERE id IN (991257,991258)")
        client.close()
    }
    @Test fun `real HTTP protects ownership and deletion and keeps note order and scores`() {
        for(id in listOf(991257,991258)) jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','조회자',1,'HANGUL','ACTIVE')",id,"read$id@test.invalid")
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,started_at)
            VALUES(991257,991257,1,1,96,'DOTTED_QUARTER',0,1,'RECORDING',CURRENT_TIMESTAMP)""")
        assertEquals(401,get(null).statusCode())
        assertEquals(404,get(991258).statusCode())
        assertEquals(409,get(991257).statusCode())
        jdbc.update("UPDATE practice_sessions SET status='DONE',ended_at='2026-10-29 00:01:02',recording_bytes=1024,recording_duration_ms=2876,recording_sample_rate=44100,recording_channels=1 WHERE id=991257")
        jdbc.update("INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(991257,991257,84,88,80,84,'test','2026-10-29 00:02:03')")
        for (idx in listOf(1,0)) jdbc.update("INSERT INTO note_results(evaluation_id,note_idx,expected_pitch_id,verdict) VALUES(991257,?,1,'CORRECT')",idx)
        jdbc.update("UPDATE note_results SET verdict='TIMING',pitch_error_cent=-12,onset_error_ms=150,duration_error_ms=20 WHERE evaluation_id=991257 AND note_idx=1")
        val response=get(991257);assertEquals(200,response.statusCode(),response.body())
        val result=json.readTree(response.body()).path("result")
        assertEquals(84,result.path("totalScore").asInt())
        assertEquals(88,result.path("pitchScore").asInt())
        assertEquals(80,result.path("timingScore").asInt())
        assertEquals(84,result.path("durationScore").asInt())
        assertEquals("991257",result.path("sessionId").asText())
        assertEquals("991257",result.path("evaluationId").asText())
        assertEquals("1",result.path("songId").asText())
        assertEquals(1,result.path("scoreVersion").asInt())
        assertEquals("2026-10-29T00:01:02Z",result.path("endedAt").asText())
        assertEquals("2026-10-29T00:02:03Z",result.path("evaluatedAt").asText())
        assertEquals(2876,result.path("recordingDurationMs").asInt())
        assertFalse(result.has("recordingUrl"))
        assertFalse(result.has("feedback"))
        assertEquals(0,result.path("notes").path(0).path("noteIdx").asInt())
        assertEquals(1,result.path("notes").path(1).path("noteIdx").asInt())
        assertEquals("TIMING",result.path("notes").path(1).path("verdict").asText())
        assertEquals(-12,result.path("notes").path(1).path("pitchErrorCent").asInt())
        assertEquals(150,result.path("notes").path(1).path("onsetErrorMs").asInt())
        assertEquals(20,result.path("notes").path(1).path("durationErrorMs").asInt())
        jdbc.update("UPDATE practice_sessions SET record_deleted_at=CURRENT_TIMESTAMP WHERE id=991257")
        assertEquals(404,get(991257).statusCode())
    }
    private fun get(user: Int?):HttpResponse<String> {
        val request=HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/991257/evaluation"))
        if(user!=null) {
            val jwt=SignedJWT(JWSHeader(JWSAlgorithm.HS256),JWTClaimsSet.Builder().subject(user.toString()).expirationTime(Date.from(Instant.now().plusSeconds(60))).build())
            jwt.sign(MACSigner(SECRET.toByteArray()));request.header("Cookie","access_token=${jwt.serialize()}")
        }
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString())
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r:DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled"){"false"};r.add("eolssu.security.jwt-secret"){SECRET}
            r.add("spring.datasource.url"){requireNotNull(System.getenv("TEST_DB_URL"))}
            r.add("spring.datasource.username"){requireNotNull(System.getenv("TEST_DB_USERNAME"))}
            r.add("spring.datasource.password"){requireNotNull(System.getenv("TEST_DB_PASSWORD"))}
        }
    }
}
