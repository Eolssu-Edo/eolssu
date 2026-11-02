package com.ssafy.eolssu.backend.feature.lesson

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.apache.ibatis.session.SqlSessionFactory
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

private const val TEST_SECRET = "test-only-signing-key-32-bytes-1234567890"

/** 실제 HTTP·JWT 쿠키·MySQL과 V13 시드로 LRN-01·02를 확인한다. 넣은 행은 끝에서 지운다. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.security.jwt-secret=$TEST_SECRET"])
class LessonApiDatabaseTests {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var sqlSessionFactory: SqlSessionFactory

    @Value("\${local.server.port}")
    private var port = 0

    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun insertRows() {
        deleteRows()
        for (id in listOf(961, 962)) {
            jdbc.update(
                "INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status) VALUES (?, ?, 'hash', '시험', 1, 'HANJA', 'ACTIVE')",
                id, "lesson$id@example.test",
            )
        }
        // V13 시드 id: 단소 1~9, 소금 10~18, 정악대금 19~27, 산조대금 28~36. 9단계(id 9)는 선택 단계다.
        jdbc.update(
            "INSERT INTO lesson_progress (user_id, lesson_id, completed_at) VALUES " +
                "(961, 1, '2026-11-01 03:00:00.123456'), (961, 9, '2026-11-01 04:00:00'), (962, 2, '2026-11-01 05:00:00'), (962, 10, '2026-11-01 06:00:00')",
        )
        jdbc.update(
            "INSERT INTO lessons (id, track, seq, title, judge_type, is_optional, content_json, status) " +
                "VALUES (9601, 'DANSO', 10, '초안 단계', 'READ', FALSE, '{}', 'DRAFT')",
        )
    }

    @AfterEach
    fun cleanUp() {
        deleteRows()
        client.close()
    }

    @Test
    fun `lists the published stages of each track with only the signed in member's marks`() {
        val danso = result(get("/api/lessons?track=DANSO", "961"))
        assertEquals("DANSO", danso["track"].asString())
        assertEquals(1, danso["completedCount"].asInt())
        assertEquals(8, danso["requiredCount"].asInt())
        assertEquals("2", danso["nextLessonId"].asString())
        val items = danso["items"].toList()
        assertEquals((1..9).map { it.toString() }, items.map { it["lessonId"].asString() })
        assertEquals((1..9).toList(), items.map { it["seq"].asInt() })
        assertEquals("2026-11-01T03:00:00.123456Z", items[0]["completedAt"].asString())
        assertTrue(items[1]["completedAt"].isNull, "회원 962의 표시가 보이면 안 된다")
        assertEquals(listOf("READ", "READ", "PITCH", "PITCH", "PITCH", "PITCH", "BEAT", "BEAT", "LISTEN"), items.map { it["judgeType"].asString() })
        assertEquals(listOf(9), items.filter { it["optional"].asBoolean() }.map { it["seq"].asInt() })
        assertEquals("2026-11-01T04:00:00Z", items[8]["completedAt"].asString())
        assertFalse(items[0].has("thumbnailUrl"))

        val sogeum = result(get("/api/lessons?track=SOGEUM", "962"))
        assertEquals((10..18).map { it.toString() }, sogeum["items"].values().map { it["lessonId"].asString() })
        assertEquals(1, sogeum["completedCount"].asInt())
        assertEquals("11", sogeum["nextLessonId"].asString())

        val untouched = result(get("/api/lessons?track=DAEGEUM_SANJO", "961"))
        assertEquals(0, untouched["completedCount"].asInt())
        assertEquals("28", untouched["nextLessonId"].asString())
    }

    @Test
    fun `returns the full stage content of each track`() {
        val basic = result(get("/api/lessons/4", "961"))
        assertEquals("DANSO", basic["track"].asString())
        assertEquals("기본음", basic["title"].asString())
        assertEquals(listOf("汰", "潢", "無", "林", "仲"), basic["pitchNames"].values().map { it.asString() })
        assertTrue(basic["pitchNote"]["text"].asString().startsWith("구멍을 다 연 汰(태)부터"))
        assertEquals("음마다 2초 이상 유지", basic["passCriteria"]["title"].asString())
        assertEquals(0, basic["tasks"].size())
        assertTrue(basic["completedAt"].isNull)

        val sogeumParts = result(get("/api/lessons/10", "961"))
        assertEquals(listOf("취구", "지공 6개"), sogeumParts["parts"].values().map { it.asString() })
        assertTrue(sogeumParts["passCriteria"].isNull)

        val sanjoHold = result(get("/api/lessons/29", "961"))
        assertEquals("DAEGEUM_SANJO", sanjoHold["track"].asString())
        assertEquals("악기 잡기", sanjoHold["tasks"][1]["title"].asString())
        assertEquals("가로로 오른쪽을 향해 잡아요. 기본 운지틀은 정악대금과 같아요.", sanjoHold["tasks"][1]["description"].asString())

        val jeongakRanges = result(get("/api/lessons/24", "961"))
        assertEquals(listOf("HIGH" to "淋", "LOW" to "㑣"), jeongakRanges["ranges"].values().map { it["blow"].asString() to it["firstPitch"].asString() })

        val songs = result(get("/api/lessons/8", "961"))
        assertEquals(listOf("비행기", "새야새야", "아리랑", "도라지타령", "천안삼거리", "풍년가"), songs["songs"].values().map { it["title"].asString() })
        assertEquals(listOf("仲", "林", "無", "潢", "汰"), songs["pitchNames"].values().map { it.asString() })

        val optional = result(get("/api/lessons/9", "961"))
        assertTrue(optional["optional"].asBoolean())
        assertTrue(optional["tasks"][0]["description"].isNull)
        assertEquals("2026-11-01T04:00:00Z", optional["completedAt"].asString())
    }

    @Test
    fun `hides draft and missing stages and rejects invalid requests`() {
        for (path in listOf("/api/lessons/9601", "/api/lessons/999999", "/api/lessons/0")) {
            assertError(get(path, "961"), 404, "RESOURCE_NOT_FOUND")
        }
        assertError(get("/api/lessons/abc", "961"), 400, "INVALID_REQUEST")
        assertError(get("/api/lessons?track=danso", "961"), 400, "INVALID_REQUEST")
        assertError(get("/api/lessons", "961"), 400, "INVALID_REQUEST")
        assertError(get("/api/lessons?track=DANSO", null), 401, "UNAUTHENTICATED")
        assertError(get("/api/lessons/1", null), 401, "UNAUTHENTICATED")
    }

    @Test
    fun `reads a track through the two unique indexes without sorting`() {
        val statement = sqlSessionFactory.configuration
            .getMappedStatement("com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonMapper.findPublishedSteps")
        val parameters: Map<String, Any> = mapOf("userId" to 961L, "track" to "DANSO")
        val bound = statement.getBoundSql(parameters)
        val arguments: List<Any> = bound.parameterMappings.map { parameters.getValue(it.property) }
        val plan = jdbc.queryForList("EXPLAIN ${bound.sql}", *arguments.toTypedArray()).associateBy { it["table"] }

        assertEquals("uq_lessons_track_seq", plan.getValue("l")["key"])
        assertFalse((plan.getValue("l")["Extra"] as String?).orEmpty().contains("filesort"), plan.toString())
        assertEquals("uq_lesson_progress_user_lesson", plan.getValue("p")["key"])
    }

    private fun get(path: String, subject: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json").GET()
        if (subject != null) builder.header("Cookie", "access_token=${token(subject)}")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun result(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertTrue(body["isSuccess"].asBoolean())
        return body["result"]
    }

    private fun assertError(response: HttpResponse<String>, status: Int, errorCode: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(errorCode, json.readTree(response.body())["result"]["errorCode"].asString())
    }

    private fun token(subject: String): String = SignedJWT(
        JWSHeader(JWSAlgorithm.HS256),
        JWTClaimsSet.Builder().subject(subject).issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build(),
    ).apply { sign(MACSigner(TEST_SECRET.toByteArray(Charsets.UTF_8))) }.serialize()

    private fun deleteRows() {
        jdbc.update("DELETE FROM lesson_progress WHERE user_id IN (961, 962)")
        jdbc.update("DELETE FROM lessons WHERE id = 9601")
        jdbc.update("DELETE FROM users WHERE id IN (961, 962)")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
