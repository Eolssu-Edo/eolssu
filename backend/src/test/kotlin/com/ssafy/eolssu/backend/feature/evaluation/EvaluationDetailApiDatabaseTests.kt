package com.ssafy.eolssu.backend.feature.evaluation

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.*
import java.net.URI
import java.net.http.*
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import tools.jackson.databind.json.JsonMapper

private const val SECRET="evaluation-test-signing-key-307-long"
@Tag("database") @ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvaluationDetailApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port=0
    private val client=HttpClient.newHttpClient()
    private val json=JsonMapper.builder().build()
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM note_results WHERE evaluation_id=991307")
        jdbc.update("DELETE FROM evaluations WHERE id=991307")
        jdbc.update("DELETE FROM practice_sessions WHERE id=991307")
        jdbc.update("DELETE FROM users WHERE id IN (991307,991308)")
        client.close()
    }

    // V6 시드 1번 곡(악보 버전 1)의 음표 16·17은 c0-j11, 18은 c1-j0, 19는 c1-j1이다.
    @Test fun `real HTTP reads stored verdicts and groups them by seeded score cells`() {
        for(id in listOf(991307,991308)) jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','조회자',1,'HANGUL','ACTIVE')",id,"detail$id@test.invalid")
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,started_at)
            VALUES(991307,991307,1,1,96,'DOTTED_QUARTER',16,19,'PENDING',CURRENT_TIMESTAMP)""")
        assertEquals(401,get(null,"evaluation/notes").statusCode())
        assertEquals(404,get(991308,"weak-sections").statusCode())
        assertEquals(409,get(991307,"evaluation/notes").statusCode())
        jdbc.update("UPDATE practice_sessions SET status='DONE',ended_at=CURRENT_TIMESTAMP WHERE id=991307")
        jdbc.update("INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(991307,991307,40,40,40,40,'test',CURRENT_TIMESTAMP)")
        for ((idx, verdict) in listOf(19 to "PITCH", 16 to "PITCH", 17 to "TIMING", 18 to "MISSED"))
            jdbc.update("INSERT INTO note_results(evaluation_id,note_idx,expected_pitch_id,detected_pitch_id,pitch_error_cent,verdict) VALUES(991307,?,1,?,?,?)",
                idx, if (verdict == "MISSED") null else 1, if (verdict == "MISSED") null else 30, verdict)

        val notes=result(get(991307,"evaluation/notes"))
        assertEquals("991307",notes.path("evaluationId").asText())
        assertEquals(listOf(16,17,18,19),notes.path("items").toList().map { it.path("noteIdx").asInt() })
        assertEquals("1",notes.path("items").path(0).path("expectedPitchId").asText())
        assertEquals(30,notes.path("items").path(0).path("pitchErrorCent").asInt())
        assertEquals(true,notes.path("items").path(2).path("detectedPitchId").isNull)
        assertEquals(4,notes.path("errorCount").asInt())
        assertEquals(listOf(2,1,0,1),listOf("pitch","timing","duration","missed").map { notes.path("errorCounts").path(it).asInt() })
        val ranged=result(get(991307,"evaluation/notes?startNoteIdx=18&endNoteIdx=19&errorsOnly=true"))
        assertEquals(listOf(18,19),ranged.path("items").toList().map { it.path("noteIdx").asInt() })
        assertEquals(400,get(991307,"evaluation/notes?startNoteIdx=15").statusCode())

        val weak=result(get(991307,"weak-sections"))
        assertEquals(3,weak.path("totalErrorCellCount").asInt())
        val item=weak.path("items").path(0)
        assertEquals(listOf(16,19,0,1,3),listOf("startNoteIdx","endNoteIdx","startColumnIdx","endColumnIdx","errorCount").map { item.path(it).asInt() })
        assertEquals("PITCH",item.path("dominantError").asText())
        assertEquals(400,get(991307,"weak-sections?limit=0").statusCode())

        jdbc.update("UPDATE practice_sessions SET score_version=2 WHERE id=991307")
        assertEquals(409,get(991307,"weak-sections").statusCode())
        jdbc.update("UPDATE practice_sessions SET record_deleted_at=CURRENT_TIMESTAMP WHERE id=991307")
        assertEquals(404,get(991307,"evaluation/notes").statusCode())
    }

    private fun result(response: HttpResponse<String>) = json.readTree(response.body()).also { assertEquals(200,response.statusCode(),response.body()) }.path("result")
    private fun get(user: Int?, path: String):HttpResponse<String> {
        val request=HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/991307/$path"))
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
