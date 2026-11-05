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
private const val NOW="2026-11-01 12:00:00.000000"
@Tag("database") @ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectionComparisonApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port=0
    private val client=HttpClient.newHttpClient()
    private val json=JsonMapper.builder().build()
    private val sessions=listOf(991311,991310,991312,991313,991314,991315,991316,991317)
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
        users()
        session(991310,null,"m1",68,mapOf(16 to "PITCH",17 to "CORRECT",18 to "MISSED",19 to "TIMING"),bpm=60)
        session(991311,991310,"m1",85,mapOf(16 to "CORRECT",17 to "CORRECT",18 to "CORRECT",19 to "PITCH"))
        assertEquals(401,get(null,"").statusCode())
        assertEquals(404,get(991311,"").statusCode())

        val result=result(get(991310,""))
        assertEquals("991310",result.path("baselineSessionId").asText())
        assertEquals("PARENT",result.path("baselineSource").asText())
        assertEquals(false,result.path("baselineStartedAt").isNull)
        assertEquals(false,result.path("isFullSong").asBoolean(true))
        assertEquals(false,result.path("scoreComparable").asBoolean(true))
        assertEquals(true,result.path("errorComparable").asBoolean())
        assertEquals(listOf("NO_NOTE_SCORES"),result.path("notComparableReasons").toList().map { it.asText() })
        assertEquals(true,result.path("bpmChanged").asBoolean())
        assertEquals(listOf(16,19,0,1),listOf("startNoteIdx","endNoteIdx","startColumnIdx","endColumnIdx").map { result.path(it).asInt() })
        assertEquals(true,result.path("before").path("totalScore").isNull)
        assertEquals(3,result.path("before").path("errorCount").asInt())
        assertEquals(60,result.path("before").path("bpm").asInt())
        assertEquals(85,result.path("after").path("totalScore").asInt())
        assertEquals(1,result.path("after").path("errorCount").asInt())
        assertEquals(48,result.path("after").path("bpm").asInt())
        assertEquals(true,result.path("delta").path("totalScore").isNull)
        assertEquals(-2,result.path("delta").path("errorCount").asInt())
        assertEquals(listOf(16,18),result.path("correctedNoteIndices").toList().map { it.asInt() })
        assertEquals(19,result.path("remainingErrors").path(0).path("noteIdx").asInt())

        jdbc.update("UPDATE evaluations SET model_version='m2' WHERE id=991311")
        val otherModel=result(get(991310,"?baselineSessionId=991310"))
        assertEquals("QUERY",otherModel.path("baselineSource").asText())
        assertEquals(true,otherModel.path("errorComparable").asBoolean())
        assertEquals(listOf("MODEL_CHANGED","NO_NOTE_SCORES"),otherModel.path("notComparableReasons").toList().map { it.asText() })
        assertEquals(-2,otherModel.path("delta").path("errorCount").asInt())

        jdbc.update("UPDATE practice_sessions SET status='PENDING' WHERE id=991310")
        assertEquals(409,get(991310,"").statusCode())
        jdbc.update("UPDATE practice_sessions SET record_deleted_at=CURRENT_TIMESTAMP WHERE id=991310")
        // 기록을 지운 parent는 사용자가 지정한 세션이 아니므로 직전 세션으로 넘어간다. 직접 지정한 세션은 404다.
        assertEquals(404,get(991310,"?baselineSessionId=991310").statusCode())
        val noPrevious=result(get(991310,""))
        assertEquals(true,noPrevious.path("baselineSessionId").isNull)
        assertEquals(true,noPrevious.path("baselineSource").isNull)
        assertEquals(true,noPrevious.path("before").isNull)
        session(991312,null,"m1",60,mapOf(16 to "PITCH",17 to "CORRECT",18 to "CORRECT",19 to "PITCH"),startedAt="2026-11-01 09:00:00.000000")
        val fallback=result(get(991310,""))
        assertEquals("991312",fallback.path("baselineSessionId").asText())
        assertEquals("PREVIOUS",fallback.path("baselineSource").asText())
        assertEquals(2,fallback.path("before").path("errorCount").asInt())
    }

    @Test fun `real HTTP without a parent picks the latest earlier session holding the section`() {
        users()
        val correct=mapOf(16 to "CORRECT",17 to "CORRECT",18 to "CORRECT",19 to "CORRECT")
        session(991311,null,"m1",85,correct,startedAt=NOW)
        val first=result(get(991310,""))
        assertEquals(true,first.path("baselineSessionId").isNull)
        assertEquals(true,first.path("baselineStartedAt").isNull)
        assertEquals(true,first.path("baselineSource").isNull)
        assertEquals(true,first.path("before").isNull)
        assertEquals(true,first.path("delta").isNull)
        assertEquals(false,first.path("errorComparable").asBoolean(true))
        assertEquals(85,first.path("after").path("totalScore").asInt())

        session(991310,null,"m1",70,mapOf(16 to "PITCH",17 to "CORRECT",18 to "CORRECT",19 to "CORRECT"),startedAt="2026-11-01 09:00:00.000000",start=0,end=30)
        session(991312,null,"m1",71,mapOf(17 to "CORRECT"),startedAt="2026-11-01 11:00:00.000000",start=17,end=18)
        session(991313,null,"m1",72,correct,startedAt="2026-11-01 11:10:00.000000",status="PENDING")
        session(991314,null,"m1",73,correct,startedAt="2026-11-01 11:20:00.000000")
        jdbc.update("UPDATE practice_sessions SET record_deleted_at=CURRENT_TIMESTAMP WHERE id=991314")
        session(991315,null,"m1",74,correct,startedAt="2026-11-01 12:30:00.000000")
        session(991316,null,"m1",75,correct,startedAt="2026-11-01 11:30:00.000000",user=991311)
        val picked=result(get(991310,""))
        assertEquals("991310",picked.path("baselineSessionId").asText())
        assertEquals("PREVIOUS",picked.path("baselineSource").asText())
        assertEquals("2026-11-01T09:00:00Z",picked.path("baselineStartedAt").asText())
        assertEquals(1,picked.path("before").path("errorCount").asInt())
        assertEquals(listOf(16),picked.path("correctedNoteIndices").toList().map { it.asInt() })

        // 시작 시각이 같으면 sessionId가 작은 쪽을 먼저 시작한 세션으로 본다.
        jdbc.update("UPDATE practice_sessions SET started_at=?,ended_at=? WHERE id IN (991310,991315)",NOW,NOW)
        assertEquals("991310",result(get(991310,"")).path("baselineSessionId").asText())

        // 다른 악보 버전의 구간 번호는 다른 음을 가리키므로 포함 여부를 보지 않고, 오류 수도 비교하지 않는다.
        jdbc.update("UPDATE practice_sessions SET started_at='2026-11-01 09:00:00.000000',ended_at='2026-11-01 09:00:00.000000' WHERE id=991310")
        session(991317,null,"m1",76,mapOf(0 to "PITCH"),startedAt="2026-11-01 11:59:00.000000",start=0,end=1,scoreVersion=2)
        val oldScore=result(get(991310,""))
        assertEquals("991317",oldScore.path("baselineSessionId").asText())
        assertEquals(false,oldScore.path("errorComparable").asBoolean(true))
        assertEquals(listOf("SCORE_VERSION_CHANGED","NO_NOTE_SCORES"),oldScore.path("notComparableReasons").toList().map { it.asText() })
        assertEquals(true,oldScore.path("before").path("errorCount").isNull)
        assertEquals(true,oldScore.path("delta").path("errorCount").isNull)
        assertEquals(0,oldScore.path("correctedNoteIndices").size())
    }

    private fun users() {
        for(id in listOf(991310,991311)) jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','비교자',1,'HANGUL','ACTIVE')",id,"compare$id@test.invalid")
    }
    private fun session(id: Int, parent: Int?, model: String, total: Int, verdicts: Map<Int,String>, bpm: Int=48, startedAt: String?=null, start: Int=16, end: Int=19,
        status: String="DONE", user: Int=991310, scoreVersion: Int=1) {
        jdbc.update("""INSERT INTO practice_sessions(id,user_id,song_id,parent_session_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,started_at,ended_at)
            VALUES(?,?,1,?,?,?,'DOTTED_QUARTER',?,?,?,COALESCE(?,CURRENT_TIMESTAMP),COALESCE(?,CURRENT_TIMESTAMP))""",id,user,parent,scoreVersion,bpm,start,end,status,startedAt,startedAt)
        if(status!="DONE") return
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
