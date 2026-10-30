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

private const val SECRET="evaluation-test-signing-key-310-long"
@Tag("database") @ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectionComparisonApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port=0
    private val client=HttpClient.newHttpClient()
    private val json=JsonMapper.builder().build()
    private val sessions=listOf(991311,991310)
    @AfterEach fun cleanup() {
        for (id in sessions) {
            jdbc.update("DELETE FROM note_results WHERE evaluation_id=?",id)
            jdbc.update("DELETE FROM evaluations WHERE id=?",id)
            jdbc.update("DELETE FROM practice_sessions WHERE id=?",id)
        }
        jdbc.update("DELETE FROM users WHERE id IN (991310,991311)")
        client.close()
    }

    // V6 시드 1번 곡(악보 버전 1)의 음표 16·17은 0각, 18·19는 1각이다.
    @Test fun `real HTTP compares a section retake with its parent session`() {
        for(id in listOf(991310,991311)) jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','비교자',1,'HANGUL','ACTIVE')",id,"compare$id@test.invalid")
        session(991310,null,"m1",68,mapOf(16 to "PITCH",17 to "CORRECT",18 to "MISSED",19 to "TIMING"))
        session(991311,991310,"m1",85,mapOf(16 to "CORRECT",17 to "CORRECT",18 to "CORRECT",19 to "PITCH"))
        assertEquals(401,get(null,"").statusCode())
        assertEquals(404,get(991311,"").statusCode())

        val result=result(get(991310,""))
        assertEquals("991310",result.path("baselineSessionId").asText())
        assertEquals(true,result.path("comparable").asBoolean())
        assertEquals(listOf(16,19,0,1),listOf("startNoteIdx","endNoteIdx","startColumnIdx","endColumnIdx").map { result.path(it).asInt() })
        assertEquals(68,result.path("before").path("totalScore").asInt())
        assertEquals(3,result.path("before").path("errorCount").asInt())
        assertEquals(85,result.path("after").path("totalScore").asInt())
        assertEquals(1,result.path("after").path("errorCount").asInt())
        assertEquals(17,result.path("delta").path("totalScore").asInt())
        assertEquals(-2,result.path("delta").path("errorCount").asInt())
        assertEquals(listOf(16,18),result.path("correctedNoteIndices").toList().map { it.asInt() })
        assertEquals(19,result.path("remainingErrors").path(0).path("noteIdx").asInt())

        jdbc.update("UPDATE evaluations SET model_version='m2' WHERE id=991311")
        val otherModel=result(get(991310,"?baselineSessionId=991310"))
        assertEquals(false,otherModel.path("comparable").asBoolean())
        assertEquals(true,otherModel.path("delta").isNull)

        jdbc.update("UPDATE practice_sessions SET status='PENDING' WHERE id=991310")
        assertEquals(409,get(991310,"").statusCode())
        jdbc.update("UPDATE practice_sessions SET record_deleted_at=CURRENT_TIMESTAMP WHERE id=991310")
        assertEquals(404,get(991310,"").statusCode())
    }

    private fun session(id: Int, parent: Int?, model: String, total: Int, verdicts: Map<Int,String>) {
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,parent_session_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,started_at,ended_at)
            VALUES(?,991310,1,?,1,48,'DOTTED_QUARTER',16,19,'DONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",id,parent)
        jdbc.update("INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,id,total,total,total,total,model)
        for ((idx,verdict) in verdicts) jdbc.update("INSERT INTO note_results(evaluation_id,note_idx,expected_pitch_id,verdict) VALUES(?,?,1,?)",id,idx,verdict)
    }
    private fun result(response: HttpResponse<String>) = json.readTree(response.body()).also { assertEquals(200,response.statusCode(),response.body()) }.path("result")
    // 사용자 991310이 본인 세션 991311을 조회한다. 991311 사용자는 세션이 없어 404다.
    private fun get(user: Int?, query: String):HttpResponse<String> {
        val request=HttpRequest.newBuilder(URI("http://localhost:$port/api/practice-sessions/991311/comparison$query"))
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
