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
        jdbc.update("DELETE FROM songs WHERE id BETWEEN 993900 AND 993903")
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

        // 저장한 음표 수가 악보와 다른 곡은 CAT-04처럼 409이고 악보를 만들지 않는다.
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 993903,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count+1,is_scorable,status FROM songs WHERE id=1""",
        )
        assertEquals(409, get(OWNER, "/api/songs/993903/score").statusCode())
        val mismatched = create(OWNER, "mismatch", """{"title":"손상","startType":"COPY","bpm":72,"copyFromSongId":"993903"}""")
        assertEquals(409, mismatched.statusCode(), mismatched.body())
        assertEquals("STATE_CONFLICT", json.readTree(mismatched.body()).path("result").path("errorCode").asString())
        assertEquals(2, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))

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
            SELECT 993902,instrument_id,NULL,title,difficulty,default_bpm,JSON_SET(score_json,'$.music.bpmUnit','QUARTER'),score_version,note_count,is_scorable,status FROM songs WHERE id=1""",
        )
        // 1번 곡 장단의 단위와 다른 값이라, 장단이 아니라 원본 악보의 단위를 쓰는지 구별된다.
        assertTrue(jdbc.queryForObject("SELECT j.bpm_unit FROM songs s JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=1", String::class.java) != "QUARTER")

        val created = create(OWNER, "copy-nojangdan", """{"title":"장단 없음","startType":"COPY","bpm":72,"copyFromSongId":"993902"}""")
        assertEquals(201, created.statusCode(), created.body())
        val sheetId = json.readTree(created.body()).path("result").path("sheetId").asString()
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertTrue(stored.has("jangdanId") && stored.path("jangdanId").isNull)
        assertEquals("QUARTER", stored.path("bpmUnit").asString())
        assertEquals("QUARTER", jdbc.queryForObject("SELECT bpm_unit FROM user_sheets WHERE id=?", String::class.java, sheetId.toLong()))
        val item = json.readTree(get(OWNER, "/api/users/me/sheets").body()).path("result").path("items").get(0)
        assertTrue(item.path("jangdanId").isNull && item.path("jangdanName").isNull, item.toString())
        val again = create(OWNER, "copy-nojangdan-sheet", """{"title":"다시 복사","startType":"COPY","bpm":80,"copyFromSheetId":"$sheetId"}""")
        assertEquals(201, again.statusCode(), again.body())
        val againResult = json.readTree(again.body()).path("result")
        assertEquals(json.readTree(created.body()).path("result").path("events"), againResult.path("events"))
        val againStored = json.readTree(get(OWNER, "/api/users/me/sheets/${againResult.path("sheetId").asString()}/score").body()).path("result")
        assertTrue(againStored.path("jangdanId").isNull)
        assertEquals("QUARTER", againStored.path("bpmUnit").asString())
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

    @Test
    fun `autosave keeps the version and list order, then saving the whole score bumps it with the CAT-04 timeline`() {
        val sheetId = copySong()
        val scoreData = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result").path("scoreData") as ObjectNode
        val updatedAt = jdbc.queryForObject("SELECT updated_at FROM user_sheets WHERE id=$sheetId", String::class.java)
        val partial = scoreData.deepCopy()
        val emptied = partial.path("columns").path(0).path("cells").path(0) as ObjectNode
        emptied.putArray("units")

        val draft = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score/draft", draftBody("1", 0, partial))
        assertEquals(200, draft.statusCode(), draft.body())
        assertEquals(1, json.readTree(draft.body()).path("result").path("draftRevision").intValue())
        assertEquals(1, json.readTree(draft.body()).path("result").path("unfilledCellCount").intValue())
        assertEquals(409, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score/draft", draftBody("1", 0, partial)).statusCode())
        assertEquals(updatedAt, jdbc.queryForObject("SELECT updated_at FROM user_sheets WHERE id=$sheetId", String::class.java))
        val listed = json.readTree(get(OWNER, "/api/users/me/sheets").body()).path("result").path("items").path(0)
        assertTrue(listed.path("hasDraft").booleanValue())
        val reopened = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result").path("draft")
        assertEquals(partial, reopened.path("score"))
        assertEquals("고치는 중", reopened.path("title").asString())

        val unfilled = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", partial))
        assertEquals(400, unfilled.statusCode(), unfilled.body())
        assertEquals("UNFILLED_CELLS", json.readTree(unfilled.body()).path("result").path("errorCode").asString())
        assertEquals("c0-j0", json.readTree(unfilled.body()).path("result").path("details").path("cellIds").path(0).asString())

        val saved = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", scoreData, title = "아리랑 (내 편곡)"))
        assertEquals(200, saved.statusCode(), saved.body())
        val result = json.readTree(saved.body()).path("result")
        assertEquals("2", result.path("scoreVersion").asString())
        assertEquals("EVALUABLE", result.path("reviewStatus").asString())
        val catalog = json.readTree(get(OWNER, "/api/songs/1/score").body()).path("result")
        assertEquals(catalog.path("events"), result.path("events"))
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("아리랑 (내 편곡)", stored.path("title").asString())
        assertEquals(2, stored.path("scoreData").path("score").path("scoreVersion").intValue())
        assertTrue(stored.has("draft") && stored.path("draft").isNull)
        assertEquals(0, count("SELECT draft_revision FROM user_sheets WHERE id=$sheetId"))

        assertEquals(409, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", scoreData)).statusCode())
        assertEquals(404, send(OTHER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("2", scoreData)).statusCode())
        assertEquals(404, send(OTHER, "PUT", "/api/users/me/sheets/$sheetId/score/draft", draftBody("2", 0, scoreData)).statusCode())
    }

    @Test
    fun `a score whose jangdan or ticks do not match is rejected and nothing changes`() {
        val sheetId = copySong()
        val scoreData = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result").path("scoreData") as ObjectNode
        val otherJangdan = jdbc.queryForObject("SELECT id FROM jangdans WHERE id <> ? ORDER BY id LIMIT 1", Long::class.java, jangdanId)!!
        val brokenTicks = scoreData.deepCopy().also { (it.path("events").path(0) as ObjectNode).put("durationTick", 1) }

        val wrongJangdan = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", scoreData, jangdan = otherJangdan))
        assertEquals(400, wrongJangdan.statusCode(), wrongJangdan.body())
        assertEquals(400, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", brokenTicks)).statusCode())
        assertEquals(1, count("SELECT score_version FROM user_sheets WHERE id=$sheetId"))
    }

    @Test
    fun `the draft columns cannot hold a draft without its base version`() {
        val sheetId = copySong()

        val rejected = runCatching {
            jdbc.update("UPDATE user_sheets SET draft_json='{}', draft_base_version=NULL, draft_saved_at=NOW(6) WHERE id=$sheetId")
        }.exceptionOrNull()
        // 3819: Check constraint is violated. 다른 오류(문법·권한)로 실패해도 통과하지 않게 원인을 본다.
        assertEquals(3819, generateSequence(rejected) { it.cause }.filterIsInstance<java.sql.SQLException>().firstOrNull()?.errorCode, rejected.toString())
    }

    @Test
    fun `a body over 1 MiB is 413 before parsing, and an anonymous one is still 401`() {
        val sheetId = copySong()
        // 공백으로 부풀려 다시 직렬화하면 작아지는 본문도 받은 크기로 잰다.
        val padded = """{"baseScoreVersion":"1","bpm":72,"score":{}${" ".repeat(1024 * 1024)}}"""

        val tooLarge = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", padded)
        assertEquals(413, tooLarge.statusCode(), tooLarge.body())
        assertEquals("PAYLOAD_TOO_LARGE", json.readTree(tooLarge.body()).path("result").path("errorCode").asString())
        assertEquals(413, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score/draft", padded).statusCode())
        assertEquals(401, send(null, "PUT", "/api/users/me/sheets/$sheetId/score", padded).statusCode())
        // 길이를 알리지 않는(chunked) 본문도 받은 바이트로 자른다.
        val chunked = client.send(
            authorized(URI("http://localhost:$port/api/users/me/sheets/$sheetId/score"), OWNER).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofInputStream { padded.byteInputStream() }).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(413, chunked.statusCode(), chunked.body())
        assertEquals(1, count("SELECT score_version FROM user_sheets WHERE id=$sheetId"))
    }

    @Test
    fun `an encoded path that reaches the score routes is limited the same way`() {
        val sheetId = copySong()
        val padded = """{"baseScoreVersion":"1","bpm":72,"score":{}${" ".repeat(1024 * 1024)}}"""

        // %73 = s, %64 = d. Spring MVC는 디코딩한 경로로 컨트롤러를 찾으므로 필터도 같은 경로로 판단해야 한다.
        for (path in listOf("/api/users/me/sheets/$sheetId/%73core", "/api/users/me/sheets/$sheetId/score/%64raft", "/api/users/me/%73heets/$sheetId/score")) {
            val response = send(OWNER, "PUT", path, padded)
            assertEquals(413, response.statusCode(), "$path ${response.body().take(200)}")
        }
        assertEquals(1, count("SELECT score_version FROM user_sheets WHERE id=$sheetId"))
    }

    @Test
    fun `delete removes only the owner's sheet`() {
        val sheetId = copySong()
        val others = create(OTHER, "others", """{"title":"남의 악보","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":4}""")
        val otherId = json.readTree(others.body()).path("result").path("sheetId").asString()

        assertEquals(404, send(OWNER, "DELETE", "/api/users/me/sheets/$otherId", null).statusCode())
        val deleted = send(OWNER, "DELETE", "/api/users/me/sheets/$sheetId", null)
        assertEquals(204, deleted.statusCode(), deleted.body())
        assertEquals("", deleted.body())
        assertEquals(404, get(OWNER, "/api/users/me/sheets/$sheetId/score").statusCode())
        assertEquals(404, send(OWNER, "DELETE", "/api/users/me/sheets/$sheetId", null).statusCode())
        assertEquals(401, send(null, "DELETE", "/api/users/me/sheets/$otherId", null).statusCode())
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OTHER"))
    }

    private fun copySong(): String {
        val created = create(OWNER, "copy-for-edit", """{"title":"아리랑 연습","startType":"COPY","bpm":72,"copyFromSongId":"1"}""")
        assertEquals(201, created.statusCode(), created.body())
        return json.readTree(created.body()).path("result").path("sheetId").asString()
    }

    private fun scoreBody(base: String, score: JsonNode, title: String? = null, jangdan: Long = jangdanId): String {
        val body = json.createObjectNode().put("baseScoreVersion", base).put("jangdanId", jangdan.toString()).put("bpm", 72)
        if (title != null) body.put("title", title)
        body.set("score", score)
        return json.writeValueAsString(body)
    }

    private fun draftBody(base: String, revision: Int, score: JsonNode): String {
        val body = json.createObjectNode().put("baseScoreVersion", base).put("draftRevision", revision).put("title", "고치는 중")
            .put("jangdanId", jangdanId.toString()).put("bpm", 72)
        body.set("score", score)
        return json.writeValueAsString(body)
    }

    private fun send(user: Long?, method: String, path: String, body: String?): HttpResponse<String> = client.send(
        authorized(URI("http://localhost:$port$path"), user).header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

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
