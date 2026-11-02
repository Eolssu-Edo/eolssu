package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

private const val SECRET = "user-sheet-api-test-signing-key-390"
private const val OWNER = 991390L
private const val OTHER = 991391L

// V6 시드 1번 곡 '아리랑'(게시·채점 가능, 악보 버전 1)을 복사 원본으로 쓴다.
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSheetApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private var jangdanId = 0L

    @BeforeEach
    fun fixtures() {
        cleanup()
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update(
                "INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','악보 작성자',1,'HANGUL','ACTIVE')",
                id, "sheet$id@test.invalid",
            )
        }
        jangdanId = jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=1", Long::class.java)!!
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM songs WHERE id BETWEEN 993900 AND 993902")
    }

    @Test
    fun `blank sheet is stored once per key and only its owner can read it`() {
        val title = "가".repeat(49) + "🎵"
        val body = """{"title":" $title ","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":8}"""

        val created = create(OWNER, "blank-1", body)
        assertEquals(201, created.statusCode(), created.body())
        val result = json.readTree(created.body()).path("result")
        val sheetId = result.path("sheetId").asString()
        assertEquals("NEEDS_REVIEW", result.path("status").asString())
        assertEquals("1", result.path("scoreVersion").asString())
        assertEquals(0, result.path("events").size())

        val replay = create(OWNER, "blank-1", body.replace(" $title ", title))
        assertEquals(201, replay.statusCode(), replay.body())
        assertEquals(result, json.readTree(replay.body()).path("result"))
        assertEquals(409, create(OWNER, "blank-1", body.replace("\"bpm\":60", "\"bpm\":62")).statusCode())
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))

        val score = get(OWNER, "/api/users/me/sheets/$sheetId/score")
        assertEquals(200, score.statusCode(), score.body())
        val stored = json.readTree(score.body()).path("result")
        assertEquals(title, stored.path("title").asString())
        assertEquals(jangdanId.toString(), stored.path("jangdanId").asString())
        assertEquals(8, stored.path("columnCount").intValue())
        assertEquals("NEEDS_REVIEW", stored.path("reviewStatus").asString())
        assertTrue(stored.has("scoreData") && stored.path("scoreData").isNull)
        assertTrue(stored.has("source") && stored.path("source").isNull)
        assertEquals(0, stored.path("issues").size())
        assertEquals(result.path("updatedAt"), stored.path("updatedAt"))

        assertEquals(404, get(OTHER, "/api/users/me/sheets/$sheetId/score").statusCode())
        assertEquals(401, get(null, "/api/users/me/sheets/$sheetId/score").statusCode())
        assertEquals(401, create(null, "blank-2", body).statusCode())
    }

    @Test
    fun `copying a published song keeps its common score with the same timeline as CAT-04`() {
        val created = create(OWNER, "copy-song", """{"title":"아리랑 연습","startType":"COPY","bpm":72,"copyFromSongId":"1"}""")
        assertEquals(201, created.statusCode(), created.body())
        val result = json.readTree(created.body()).path("result")
        assertEquals("EVALUABLE", result.path("status").asString())

        val catalog = json.readTree(get(OWNER, "/api/songs/1/score").body()).path("result")
        assertEquals(catalog.path("events"), result.path("events"))
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/${result.path("sheetId").asString()}/score").body()).path("result")
        assertEquals(catalog.path("score"), stored.path("scoreData"))
        assertEquals(catalog.path("score").path("columns").size(), stored.path("columnCount").intValue())
        assertEquals(72, stored.path("bpm").intValue())
        assertEquals(catalog.path("noteCount").intValue(), count("SELECT note_count FROM user_sheets WHERE user_id=$OWNER"))
    }

    @Test
    fun `a newer song version restarts at version 1 and hidden songs or another member's sheet are not found`() {
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 993900,instrument_id,jangdan_id,title,difficulty,default_bpm,JSON_SET(score_json,'$.score.scoreVersion',3),3,note_count,FALSE,status FROM songs WHERE id=1""",
        )
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 993901,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,'HIDDEN' FROM songs WHERE id=1""",
        )
        val created = create(OWNER, "copy-v3", """{"title":"새 버전","startType":"COPY","bpm":72,"copyFromSongId":"993900","jangdanId":"$jangdanId"}""")
        assertEquals(201, created.statusCode(), created.body())
        val sheetId = json.readTree(created.body()).path("result").path("sheetId").asString()
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("NEEDS_REVIEW", stored.path("reviewStatus").asString())
        assertEquals(1, stored.path("scoreData").path("score").path("scoreVersion").intValue())
        val original = json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=993900", String::class.java)) as ObjectNode
        (original.path("score") as ObjectNode).put("scoreVersion", 1)
        assertEquals(original, stored.path("scoreData"))

        val again = create(OWNER, "copy-sheet", """{"title":"복사본","startType":"COPY","bpm":80,"copyFromSheetId":"$sheetId"}""")
        assertEquals(201, again.statusCode(), again.body())
        val againId = json.readTree(again.body()).path("result").path("sheetId").asString()
        assertEquals(stored.path("scoreData"), json.readTree(get(OWNER, "/api/users/me/sheets/$againId/score").body()).path("result").path("scoreData"))

        assertEquals(404, create(OWNER, "hidden", """{"title":"숨김","startType":"COPY","bpm":72,"copyFromSongId":"993901"}""").statusCode())
        assertEquals(404, create(OTHER, "theirs", """{"title":"남의 것","startType":"COPY","bpm":72,"copyFromSheetId":"$sheetId"}""").statusCode())
        val wrongJangdan = jdbc.queryForObject("SELECT MAX(id) FROM jangdans WHERE id<>?", Long::class.java, jangdanId)
        assertEquals(400, create(OWNER, "wrong", """{"title":"x","startType":"COPY","bpm":72,"copyFromSongId":"1","jangdanId":"$wrongJangdan"}""").statusCode())
        assertEquals(404, create(OWNER, "nojangdan", """{"title":"x","startType":"BLANK","bpm":72,"columnCount":4,"jangdanId":"999999"}""").statusCode())
    }

    @Test
    fun `a song without jangdan is copied with its score bpm unit and lists without a jangdan`() {
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 993902,instrument_id,NULL,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status FROM songs WHERE id=1""",
        )
        val unit = jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(score_json,'$.music.bpmUnit')) FROM songs WHERE id=1", String::class.java)

        val created = create(OWNER, "copy-nojangdan", """{"title":"장단 없음","startType":"COPY","bpm":72,"copyFromSongId":"993902"}""")
        assertEquals(201, created.statusCode(), created.body())
        val sheetId = json.readTree(created.body()).path("result").path("sheetId").asString()
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertTrue(stored.has("jangdanId") && stored.path("jangdanId").isNull)
        assertEquals(unit, stored.path("bpmUnit").asString())
        val item = json.readTree(get(OWNER, "/api/users/me/sheets").body()).path("result").path("items").get(0)
        assertTrue(item.path("jangdanId").isNull && item.path("jangdanName").isNull, item.toString())
        assertEquals(400, create(OWNER, "nojangdan-given", """{"title":"x","startType":"COPY","bpm":72,"copyFromSongId":"993902","jangdanId":"$jangdanId"}""").statusCode())
    }

    @Test
    fun `list pages only the owner's sheets newest first`() {
        for (index in 1..3) {
            create(OWNER, "list-$index", """{"title":"악보 $index","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":4}""")
        }
        create(OTHER, "list-other", """{"title":"남의 악보","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":4}""")
        jdbc.update("UPDATE user_sheets SET updated_at='2026-11-01 00:00:00' WHERE user_id=$OWNER")
        jdbc.update("UPDATE user_sheets SET updated_at='2026-11-02 00:00:00' WHERE user_id=$OWNER AND title='악보 1'")

        val first = json.readTree(get(OWNER, "/api/users/me/sheets?size=2").body()).path("result")
        assertEquals(listOf("악보 1", "악보 3"), first.path("items").titles())
        assertEquals(3, first.path("totalElements").intValue())
        assertEquals(2, first.path("totalPages").intValue())
        val item = first.path("items").get(0)
        val jangdanName = jdbc.queryForObject("SELECT name FROM jangdans WHERE id=?", String::class.java, jangdanId)
        assertEquals(jangdanName, item.path("jangdanName").asString())
        assertEquals("MANUAL", item.path("source").asString())
        assertEquals("2026-11-02T00:00:00Z", item.path("updatedAt").asString())
        assertTrue(item.has("sourcePageCount") && item.path("sourcePageCount").isNull)
        val second = json.readTree(get(OWNER, "/api/users/me/sheets?page=1&size=2").body()).path("result")
        assertEquals(listOf("악보 2"), second.path("items").titles())
        assertEquals(0, json.readTree(get(OWNER, "/api/users/me/sheets?kind=MIDI").body()).path("result").path("items").size())
        assertEquals(400, get(OWNER, "/api/users/me/sheets?size=101").statusCode())
    }

    @Test
    fun `concurrent requests with one key create one sheet`() {
        val body = """{"title":"동시","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":4}"""
        val responses = (1..8).map { CompletableFuture.supplyAsync { create(OWNER, "same-key", body) } }.map { it.join() }

        assertEquals(setOf(201), responses.map { it.statusCode() }.toSet(), responses.joinToString { it.body() })
        assertEquals(1, responses.map { json.readTree(it.body()).path("result").path("sheetId").asString() }.toSet().size)
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
    }

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!

    private fun create(user: Long?, key: String, body: String): HttpResponse<String> = client.send(
        authorized(URI("http://localhost:$port/api/users/me/sheets"), user)
            .header("Idempotency-Key", key).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun get(user: Long?, path: String): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port$path"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun authorized(uri: URI, user: Long?): HttpRequest.Builder {
        val request = HttpRequest.newBuilder(uri)
        if (user != null) {
            val jwt = SignedJWT(
                JWSHeader(JWSAlgorithm.HS256),
                JWTClaimsSet.Builder().subject(user.toString()).expirationTime(Date.from(Instant.now().plusSeconds(60))).build(),
            )
            jwt.sign(MACSigner(SECRET.toByteArray()))
            request.header("Cookie", "access_token=${jwt.serialize()}")
        }
        return request
    }

    private fun JsonNode.titles() = toList().map { it.path("title").asString() }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
