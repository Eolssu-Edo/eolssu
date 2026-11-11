package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.sheet.application.CopyPublishedSheet
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyFailure
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
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

// V6 시드 1번 곡 '아리랑'(게시·채점 가능, 악보 버전 1)의 악보를 내 악보 행에 넣어 쓴다.
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSheetApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var copyPublishedSheet: CopyPublishedSheet
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private var jangdanId = 0L
    private var keys = 0

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
        jdbc.update("DELETE FROM songs WHERE id=993902")
    }

    @Test
    fun `a blank sheet reads without score data and only its owner can read it`() {
        val title = "가".repeat(49) + "🎵"
        val sheetId = insertBlank(OWNER, title, columnCount = 8)

        val score = get(OWNER, "/api/users/me/sheets/$sheetId/score")
        assertEquals(200, score.statusCode(), score.body())
        val stored = json.readTree(score.body()).path("result")
        assertEquals(title, stored.path("title").asString())
        assertEquals(jangdanId.toString(), stored.path("jangdanId").asString())
        assertEquals(8, stored.path("columnCount").intValue())
        assertEquals("1", stored.path("scoreVersion").asString())
        assertEquals("NEEDS_REVIEW", stored.path("reviewStatus").asString())
        assertTrue(stored.has("scoreData") && stored.path("scoreData").isNull)
        assertEquals(0, stored.path("events").size())
        assertTrue(stored.has("source") && stored.path("source").isNull)
        assertEquals(0, stored.path("issues").size())

        assertEquals(404, get(OTHER, "/api/users/me/sheets/$sheetId/score").statusCode())
        assertEquals(401, get(null, "/api/users/me/sheets/$sheetId/score").statusCode())
    }

    @Test
    fun `a sheet holding a published song's score reads with the same timeline as CAT-04`() {
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")

        val catalog = json.readTree(get(OWNER, "/api/songs/1/score").body()).path("result")
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("EVALUABLE", stored.path("reviewStatus").asString())
        assertEquals(catalog.path("events"), stored.path("events"))
        assertEquals(catalog.path("score"), stored.path("scoreData"))
        assertEquals(catalog.path("score").path("columns").size(), stored.path("columnCount").intValue())
        assertEquals(72, stored.path("bpm").intValue())
    }

    @Test
    fun `a sheet without jangdan reads its score bpm unit and lists without a jangdan`() {
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT 993902,instrument_id,NULL,title,difficulty,default_bpm,JSON_SET(score_json,'$.music.bpmUnit','QUARTER'),score_version,note_count,is_scorable,status FROM songs WHERE id=1""",
        )
        // 1번 곡 장단의 단위와 다른 값이라, 장단을 다시 읽지 않고 행에 저장한 단위를 쓰는지 구별된다.
        assertTrue(jdbc.queryForObject("SELECT j.bpm_unit FROM songs s JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=1", String::class.java) != "QUARTER")

        val copied = copySong(OWNER, "no-jangdan", """"993902"""")
        assertEquals(201, copied.statusCode(), copied.body())
        val sheetId = json.readTree(copied.body()).path("result").path("sheetId").asString()
        val score = get(OWNER, "/api/users/me/sheets/$sheetId/score")
        assertEquals(200, score.statusCode(), score.body())
        val stored = json.readTree(score.body()).path("result")
        assertTrue(stored.has("jangdanId") && stored.path("jangdanId").isNull)
        assertEquals("QUARTER", stored.path("bpmUnit").asString())
        assertEquals(json.readTree(get(OWNER, "/api/songs/993902/score").body()).path("result").path("events"), stored.path("events"))
        val item = json.readTree(get(OWNER, "/api/users/me/sheets").body()).path("result").path("items").get(0)
        assertTrue(item.path("jangdanId").isNull && item.path("jangdanName").isNull, item.toString())
    }

    @Test
    fun `list pages only the owner's sheets newest first`() {
        for (index in 1..3) insertBlank(OWNER, "악보 $index")
        insertBlank(OTHER, "남의 악보")
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
    fun `the removed create endpoint answers 405 and makes no sheet`() {
        // 만들기(SHT-03)는 지웠다(S15P31E102-462). 같은 경로의 POST는 처리기가 없어 405다.
        val removed = send(OWNER, "POST", "/api/users/me/sheets", """{"title":"x","startType":"BLANK","jangdanId":"$jangdanId","bpm":60,"columnCount":4}""")

        assertEquals(405, removed.statusCode(), removed.body())
        assertEquals("METHOD_NOT_ALLOWED", json.readTree(removed.body()).path("result").path("errorCode").asString())
        assertEquals(0, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
    }

    @Test
    fun `copying a provided song produces an independently editable personal score`() {
        val original = json.readTree(get(null, "/api/songs/1/score").body()).path("result")
        val copied = copySong(OWNER, "provided-copy")
        assertEquals(201, copied.statusCode(), copied.body())
        val sheetId = json.readTree(copied.body()).path("result").path("sheetId").asString()
        val document = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("아리랑 (사본)", document.path("title").asString())
        assertEquals("1", document.path("scoreVersion").asString())
        assertEquals("EVALUABLE", document.path("reviewStatus").asString())
        assertEquals(original.path("events"), document.path("events"))
        assertEquals(404, get(OTHER, "/api/users/me/sheets/$sheetId/score").statusCode())

        val saved = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score",
            scoreBody("1", document.path("scoreData"), title = "내 아리랑"))
        assertEquals(200, saved.statusCode(), saved.body())
        val reopened = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("내 아리랑", reopened.path("title").asString())
        assertEquals("2", reopened.path("scoreVersion").asString())
        assertEquals(original, json.readTree(get(null, "/api/songs/1/score").body()).path("result"))

        val replayed = copySong(OWNER, "provided-copy")
        assertEquals(201, replayed.statusCode(), replayed.body())
        assertEquals(sheetId, json.readTree(replayed.body()).path("result").path("sheetId").asString())
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
        val conflict = copySong(OWNER, "provided-copy", """"2"""")
        assertEquals(409, conflict.statusCode(), conflict.body())
        assertEquals("IDEMPOTENCY_CONFLICT", json.readTree(conflict.body()).path("result").path("errorCode").asString())
    }

    @Test
    fun `copy requires a published song, valid input, authentication, and a free slot`() {
        assertEquals(401, copySong(null, "public").statusCode())
        assertEquals(400, copySong(OWNER, null).statusCode())
        assertEquals(400, copySong(OWNER, "numeric", "1").statusCode())
        assertEquals(404, copySong(OWNER, "unknown", """"99999999"""").statusCode())
        assertEquals(413, copySong(OWNER, "large", "1" + " ".repeat(1 shl 20)).statusCode())
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
                SELECT 993902,instrument_id,jangdan_id,title,difficulty,180,score_json,score_version,note_count,is_scorable,'DRAFT' FROM songs WHERE id=1""",
        )
        assertEquals(404, copySong(OWNER, "unpublished", """"993902"""").statusCode())
        jdbc.update("UPDATE songs SET status='PUBLISHED' WHERE id=993902")
        val tooFast = copySong(OWNER, "too-fast", """"993902"""")
        assertEquals(409, tooFast.statusCode(), tooFast.body())
        assertEquals("STATE_CONFLICT", json.readTree(tooFast.body()).path("result").path("errorCode").asString())
        repeat(10) { insertBlank(OWNER, "기존 악보 $it") }
        val full = copySong(OWNER, "full")
        assertEquals(409, full.statusCode(), full.body())
        assertEquals("SHEET_LIMIT_EXCEEDED", json.readTree(full.body()).path("result").path("errorCode").asString())
        assertEquals(10, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
    }

    @Test
    fun `a published score with no columns cannot be copied`() {
        jdbc.update(
            """INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
                SELECT 993902,instrument_id,jangdan_id,title,difficulty,default_bpm,
                JSON_SET(score_json,'$.columns',JSON_ARRAY(),'$.events',JSON_ARRAY()),score_version,0,FALSE,'PUBLISHED'
                FROM songs WHERE id=1""",
        )

        val response = copySong(OWNER, "empty-source", """"993902"""")

        assertEquals(409, response.statusCode(), response.body())
        assertEquals("STATE_CONFLICT", json.readTree(response.body()).path("result").path("errorCode").asString())
        assertEquals(0, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
    }

    @Test
    fun `a member who withdrew after authentication cannot create a copy`() {
        assertEquals(200, get(OWNER, "/api/users/me/sheets").statusCode())
        jdbc.update(
            """UPDATE users SET status='WITHDRAWAL_PENDING', withdrawal_requested_at=UTC_TIMESTAMP(6),
                purge_scheduled_at=UTC_TIMESTAMP(6) + INTERVAL 30 DAY WHERE id=?""", OWNER,
        )

        // 인증 필터를 이미 지난 요청의 회원 ID를 사용해 탈퇴 후 실행 순서를 재현한다.
        val result = copyPublishedSheet.copy(OWNER, 1, "withdrawn-before-copy-lock")

        assertEquals(SheetCopyResult.Rejected(SheetCopyFailure.NotFound), result)
        assertEquals(0, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
    }

    @Test
    fun `autosave keeps the version and list order, then saving the whole score bumps it with the CAT-04 timeline`() {
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")
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
        // 서버가 DATA 스키마의 provenance를 채운다(S15P31E102-447).
        val provenance = stored.path("scoreData").path("provenance")
        assertEquals(stored.path("scoreData").path("score").path("sourceId"), provenance.path("sourceId"))
        assertEquals("inferred" to "app-editor-1", provenance.path("review").asString() to provenance.path("converter").asString())
        assertTrue(Regex("[0-9a-f]{64}").matches(provenance.path("jgbSha256").asString()))
        assertTrue(stored.has("draft") && stored.path("draft").isNull)
        assertEquals(0, count("SELECT draft_revision FROM user_sheets WHERE id=$sheetId"))

        assertEquals(409, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", scoreData)).statusCode())
        assertEquals(404, send(OTHER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("2", scoreData)).statusCode())
        assertEquals(404, send(OTHER, "PUT", "/api/users/me/sheets/$sheetId/score/draft", draftBody("2", 0, scoreData)).statusCode())
    }

    @Test
    fun `a score whose jangdan or ticks do not match is rejected and nothing changes`() {
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")
        val scoreData = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result").path("scoreData") as ObjectNode
        val otherJangdan = jdbc.queryForObject("SELECT id FROM jangdans WHERE id <> ? ORDER BY id LIMIT 1", Long::class.java, jangdanId)!!
        val brokenTicks = scoreData.deepCopy().also { (it.path("events").path(0) as ObjectNode).put("durationTick", 1) }

        val wrongJangdan = send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", scoreData, jangdan = otherJangdan))
        assertEquals(400, wrongJangdan.statusCode(), wrongJangdan.body())
        assertEquals(400, send(OWNER, "PUT", "/api/users/me/sheets/$sheetId/score", scoreBody("1", brokenTicks)).statusCode())
        assertEquals(1, count("SELECT score_version FROM user_sheets WHERE id=$sheetId"))
    }

    @Test
    fun `new saves allow first rest but reject name-only and incomplete final jangdan cycles`() {
        val id = insertBlank(OWNER, "장단 시작 검사")
        val four = json.readTree(requireNotNull(javaClass.getResourceAsStream("/jangdan-playback/authoring-772.json"))) as ObjectNode
        val fourId = jdbc.queryForObject("SELECT id FROM jangdans WHERE name='4박 기본 반주'", Long::class.java)!!
        val nameOnly = json.readTree(scoreBody("1", four, jangdan = fourId)) as ObjectNode
        nameOnly.putNull("jangdanId")
        assertEquals(400, send(OWNER, "PUT", "/api/users/me/sheets/$id/score", json.writeValueAsString(nameOnly)).statusCode())
        val short = four.deepCopy()
        val last = short.path("columns").path(1).path("cells") as tools.jackson.databind.node.ArrayNode
        last.remove(3)
        val shortEvents = short.path("events") as tools.jackson.databind.node.ArrayNode
        shortEvents.remove(shortEvents.size() - 1)
        assertEquals(400, send(OWNER, "PUT", "/api/users/me/sheets/$id/score", scoreBody("1", short, jangdan = fourId)).statusCode())
        assertEquals(1, count("SELECT score_version FROM user_sheets WHERE id=$id"))

        val saved = send(OWNER, "PUT", "/api/users/me/sheets/$id/score", scoreBody("1", four, jangdan = fourId))
        assertEquals(200, saved.statusCode(), saved.body())
        val timeline = json.readTree(saved.body()).path("result").path("events")
        assertEquals("REST", timeline.path(0).path("kind").asString())
        assertEquals(1.5, timeline.first { it.path("kind").asString() == "NOTE" }.path("onsetBeat").doubleValue())
        val stored = json.readTree(get(OWNER, "/api/users/me/sheets/$id/score").body()).path("result").path("scoreData")
        assertEquals(four.path("events"), stored.path("events"))
        assertEquals(four.path("columns"), stored.path("columns"))
        val none = four.deepCopy().also { (it.path("music") as ObjectNode).put("jangdan", "") }
        val without = json.readTree(scoreBody("2", none, jangdan = fourId)) as ObjectNode
        without.putNull("jangdanId")
        assertEquals(200, send(OWNER, "PUT", "/api/users/me/sheets/$id/score", json.writeValueAsString(without)).statusCode())
        assertEquals(3, count("SELECT score_version FROM user_sheets WHERE id=$id"))
    }

    @Test
    fun `the draft columns cannot hold a draft without its base version`() {
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")

        val rejected = runCatching {
            jdbc.update("UPDATE user_sheets SET draft_json='{}', draft_base_version=NULL, draft_saved_at=NOW(6) WHERE id=$sheetId")
        }.exceptionOrNull()
        // 3819: Check constraint is violated. 다른 오류(문법·권한)로 실패해도 통과하지 않게 원인을 본다.
        val sql = generateSequence(rejected) { it.cause }.filterIsInstance<java.sql.SQLException>().firstOrNull()
        assertEquals(3819, sql?.errorCode, rejected.toString())
        // 3819는 어느 CHECK든 같다. 초안 제약이 막았는지 이름으로 확인한다.
        assertTrue("chk_user_sheets_draft" in sql!!.message.orEmpty(), sql.message)
    }

    @Test
    fun `a body over 1 MiB is 413 before parsing, and an anonymous one is still 401`() {
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")
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
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")
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
        val sheetId = insertSongCopy(OWNER, "아리랑 연습")
        val otherId = insertBlank(OTHER, "남의 악보")

        assertEquals(404, send(OWNER, "DELETE", "/api/users/me/sheets/$otherId", null).statusCode())
        val deleted = send(OWNER, "DELETE", "/api/users/me/sheets/$sheetId", null)
        assertEquals(204, deleted.statusCode(), deleted.body())
        assertEquals("", deleted.body())
        assertEquals(404, get(OWNER, "/api/users/me/sheets/$sheetId/score").statusCode())
        assertEquals(404, send(OWNER, "DELETE", "/api/users/me/sheets/$sheetId", null).statusCode())
        assertEquals(401, send(null, "DELETE", "/api/users/me/sheets/$otherId", null).statusCode())
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OTHER"))
    }

    @Test
    fun `confirming imported issues changes review status only when all cells are resolved`() {
        val id=insertSongCopy(OWNER,"인식 악보")
        jdbc.update("UPDATE user_sheets SET source='STAFF_OMR',pending_cell_count=1 WHERE id=?",id)
        jdbc.update("""INSERT INTO sheet_conversions(sheet_id,user_id,notation,instrument_id,status,page_count,accepted_at,finished_at,result_json)
            VALUES(?,?,'STAFF',1,'DONE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'{"issues":[{"issueId":"i1","cellId":"c0-j0"},{"issueId":"i2","cellId":"c0-j0"}],"notices":[],"pages":[]}')""",id,OWNER)
        val score=json.readTree(get(OWNER,"/api/users/me/sheets/$id/score").body()).path("result").path("scoreData")
        val first=json.readTree(scoreBody("1",score)) as ObjectNode
        first.putArray("resolvedIssueIds").add("i1")
        val saved=send(OWNER,"PUT","/api/users/me/sheets/$id/score",json.writeValueAsString(first))
        assertEquals(200,saved.statusCode(),saved.body())
        assertEquals("NEEDS_REVIEW",json.readTree(saved.body()).path("result").path("reviewStatus").asString())
        val second=json.readTree(scoreBody("2",score)) as ObjectNode
        second.putArray("resolvedIssueIds").add("i2")
        val confirmed=send(OWNER,"PUT","/api/users/me/sheets/$id/score",json.writeValueAsString(second))
        assertEquals(200,confirmed.statusCode(),confirmed.body())
        assertEquals("EVALUABLE",json.readTree(confirmed.body()).path("result").path("reviewStatus").asString())
        assertEquals(0,json.readTree(get(OWNER,"/api/users/me/sheets/$id/score").body()).path("result").path("issues").size())
    }

    // 만들기 API(SHT-03)는 S15P31E102-462에서 지웠다. 그 API가 만들던 MANUAL 행과 같은 값으로 바로 넣는다.
    private fun insertBlank(user: Long, title: String, columnCount: Int = 4): String {
        val key = "blank-${keys++}"
        jdbc.update(
            """INSERT INTO user_sheets (user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,idempotency_key,request_hash,created_at,updated_at)
            SELECT ?,?,'JEONGGANBO','MANUAL','NEEDS_REVIEW',id,60,bpm_unit,?,?,SHA2(?,256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM jangdans WHERE id=?""",
            user, title, columnCount, key, key, jangdanId,
        )
        return sheetIdOf(user, key)
    }

    /** 게시 곡의 악보를 악보 버전 1로 옮긴 사본. 장단이 없는 곡은 악보의 music.bpmUnit을 쓴다(CAT-04와 같다). */
    private fun insertSongCopy(user: Long, title: String, songId: Long = 1, bpm: Int = 72): String {
        val key = "copy-${keys++}"
        jdbc.update(
            """INSERT INTO user_sheets (user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,score_json,note_count,
                idempotency_key,request_hash,created_at,updated_at)
            SELECT ?,?,'JEONGGANBO','MANUAL',IF(s.is_scorable,'EVALUABLE','NEEDS_REVIEW'),s.jangdan_id,?,
                COALESCE(j.bpm_unit,JSON_UNQUOTE(JSON_EXTRACT(s.score_json,'$.music.bpmUnit'))),JSON_LENGTH(s.score_json,'$.columns'),
                JSON_SET(s.score_json,'$.score.scoreVersion',1),s.note_count,?,SHA2(?,256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
            FROM songs s LEFT JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=?""",
            user, title, bpm, key, key, songId,
        )
        return sheetIdOf(user, key)
    }

    private fun sheetIdOf(user: Long, key: String) =
        jdbc.queryForObject("SELECT id FROM user_sheets WHERE user_id=? AND idempotency_key=?", Long::class.java, user, key)!!.toString()

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

    private fun copySong(user: Long?, key: String?, songId: String = """"1""""): HttpResponse<String> {
        val builder = authorized(URI("http://localhost:$port/api/users/me/sheets/copies"), user)
            .header("Content-Type", "application/json")
        key?.let { builder.header("Idempotency-Key", it) }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString("""{"songId":$songId}""")).build(),
            HttpResponse.BodyHandlers.ofString())
    }

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!

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
