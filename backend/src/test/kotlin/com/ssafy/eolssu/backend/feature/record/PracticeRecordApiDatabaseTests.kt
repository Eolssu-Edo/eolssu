package com.ssafy.eolssu.backend.feature.record

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val SECRET = "practice-record-test-signing-key-342"
private const val OWNER = 991342L
private const val OTHER = 991343L

// V6 시드 1번 곡 '아리랑'(악보 버전 1, 음표 61개)의 음표 16·17은 각 0, 18·19는 각 1이다(EVA-03 DB 검사와 같은 기준).
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PracticeRecordApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var records: PracticeRecords
    @Autowired private lateinit var manager: PlatformTransactionManager
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun fixtures() {
        cleanup()
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update(
                "INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','기록자',1,'HANGUL','ACTIVE')",
                id, "record$id@test.invalid",
            )
        }
        session(991342, OWNER, 16, 19, "DONE", scoreVersion = 1)
        session(991343, OWNER, 0, 60, "PENDING", scoreVersion = 1)
        session(991344, OTHER, 16, 19, "DONE", scoreVersion = 1)
        session(991345, OWNER, 16, 19, "DONE", scoreVersion = 1)
        session(991346, OWNER, 0, 60, "DONE", scoreVersion = 2)
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:05:00', record_deleted_at='2026-11-01 11:00:00' WHERE id=991345")
        for ((id, total) in listOf(991342L to 77, 991344L to 70, 991345L to 60, 991346L to 90)) evaluation(id, total)
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("DELETE FROM evaluations WHERE session_id BETWEEN 991342 AND 991359")
        jdbc.update("DELETE FROM practice_sessions WHERE id BETWEEN 991342 AND 991359")
        jdbc.update("DELETE FROM users WHERE id IN (991342,991343)")
        recordingRoot.toFile().listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test
    fun `first save keeps its time on later saves and the memo follows the body`() {
        val first = put(991342, OWNER, null)
        assertEquals(200, first.statusCode(), first.body())
        val saved = result(first)
        val savedAt = Instant.parse(saved.path("savedAt").asString())
        assertEquals(stored(991342, "saved_at"), savedAt)
        assertTrue(saved.path("memo").isNull)
        assertEquals(listOf("991342", "1", "아리랑"), listOf("sessionId", "songId", "songTitle").map { saved.path(it).asString() })
        assertEquals(listOf(0, 1, 77, 2876, 96), listOf("startColumnIdx", "endColumnIdx", "totalScore", "recordingDurationMs", "bpm").map { saved.path(it).asInt() })
        assertEquals(false, saved.path("isFullSong").asBoolean())
        assertEquals("2026-11-01T10:00:00Z", saved.path("startedAt").asString())

        val withMemo = result(put(991342, OWNER, """{"memo":"  3–4각에서 박자가 밀림 "}"""))
        assertEquals(savedAt, Instant.parse(withMemo.path("savedAt").asString()))
        assertEquals("3–4각에서 박자가 밀림", withMemo.path("memo").asString())
        assertEquals("3–4각에서 박자가 밀림", jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))

        assertEquals("3–4각에서 박자가 밀림", result(put(991342, OWNER, "{}")).path("memo").asString())
        assertTrue(result(put(991342, OWNER, """{"memo":null}""")).path("memo").isNull)
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))
        assertEquals(savedAt, stored(991342, "saved_at"))
    }

    @Test
    fun `full song record keeps column numbers empty when the score version changed`() {
        val record = result(put(991346, OWNER, null))
        assertEquals(true, record.path("isFullSong").asBoolean())
        assertTrue(record.path("startColumnIdx").isNull && record.path("endColumnIdx").isNull)
    }

    @Test
    fun `protects ownership, deletion and unfinished sessions without writing`() {
        assertEquals(401, put(991342, null, null).statusCode())
        for ((sessionId, expected) in listOf(991344L to 404, 991345L to 404, 999999L to 404, 991343L to 409)) {
            val response = put(sessionId, OWNER, """{"memo":"덮어쓰기"}""")
            assertEquals(expected, response.statusCode(), "$sessionId ${response.body()}")
        }
        assertEquals(400, put(0, OWNER, null).statusCode())
        assertEquals(400, put(991342, OWNER, """{"memo":"${"가".repeat(501)}"}""").statusCode())
        assertNull(jdbc.queryForObject("SELECT saved_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991344", String::class.java))
        assertNull(jdbc.queryForObject("SELECT saved_at FROM practice_sessions WHERE id=991343", LocalDateTime::class.java))
        assertEquals("2026-11-01T10:05:00Z", stored(991345, "saved_at").toString())
    }

    @Test
    fun `opening a saved record through the session lookup records the first view once`() {
        put(991342, OWNER, null)
        assertEquals(200, get(991342, OWNER).statusCode())
        val firstView = assertNotNull(jdbc.queryForObject("SELECT viewed_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertEquals(200, get(991342, OWNER).statusCode())
        assertEquals(firstView, jdbc.queryForObject("SELECT viewed_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertEquals(404, get(991345, OWNER).statusCode())
    }

    // 1번 곡: 991346·991342(같은 10:00 시작), 991347(09:00). 2번 곡 991348과 3번 곡 991350은 같은 11:00에 저장했다.
    // 지운 991345, 다른 회원 991344(가장 늦게 저장), 저장 안 한 991349(최고점 99), 분석 중 991343은 나오면 안 된다.
    @Test
    fun `lists only saved records grouped by song with order, paging and the first view mark`() {
        session(991347, OWNER, 16, 17, "DONE", scoreVersion = 1, startedAt = "2026-11-01 09:00:00")
        session(991348, OWNER, 0, 90, "DONE", scoreVersion = 1, songId = 2, startedAt = "2026-10-31 08:00:00")
        session(991349, OWNER, 0, 90, "DONE", scoreVersion = 1, songId = 2, startedAt = "2026-10-31 09:00:00")
        session(991350, OWNER, 0, 84, "DONE", scoreVersion = 1, songId = 3, startedAt = "2026-10-30 08:00:00")
        for ((id, total) in listOf(991347L to 50, 991348L to 65, 991349L to 99, 991350L to 80)) evaluation(id, total)
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:05:00' WHERE id IN (991342,991347)")
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:06:00', viewed_at='2026-11-01 10:07:00', recording_url=NULL WHERE id=991346")
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 11:00:00' WHERE id IN (991348,991350)")
        jdbc.update("UPDATE practice_sessions SET memo='끝까지 숨이 부족함' WHERE id=991350")
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 12:00:00' WHERE id=991344")

        val latest = result(list(OWNER, ""))
        assertEquals(5, latest.path("totalRecordCount").asInt())
        assertEquals(
            listOf("3 밀양아리랑 1 2026-11-01T11:00:00Z 80", "2 도라지타령 1 2026-11-01T11:00:00Z 65", "1 아리랑 3 2026-11-01T10:06:00Z 90"),
            latest.path("songs").values().map { song -> listOf("songId", "title", "recordCount", "lastSavedAt", "bestScore").joinToString(" ") { song.path(it).asString() } },
        )
        assertEquals("3", latest.path("songId").asString())
        assertEquals(listOf("991350"), ids(latest))
        assertEquals("끝까지 숨이 부족함", latest.path("items").path(0).path("memo").asString())
        assertEquals(listOf(0, 20, 1, 1), listOf("page", "size", "totalElements", "totalPages").map { latest.path(it).asInt() })

        val newest = result(list(OWNER, "?songId=1"))
        assertEquals(listOf("991346", "991342", "991347"), ids(newest))
        assertEquals(
            json.readTree(
                """{"sessionId":"991346","songId":"1","title":"아리랑","isFullSong":true,"startColumnIdx":null,"endColumnIdx":null,
                   "recordingDurationMs":2876,"bpm":96,"bpmUnit":"DOTTED_QUARTER","totalScore":90,"startNoteIdx":0,"endNoteIdx":60,
                   "startedAt":"2026-11-01T10:00:00Z","recordingAvailable":false,"memo":null,"isNew":false}""",
            ),
            newest.path("items").path(0),
        )
        assertEquals(
            json.readTree(
                """{"sessionId":"991342","songId":"1","title":"아리랑","isFullSong":false,"startColumnIdx":0,"endColumnIdx":1,
                   "recordingDurationMs":2876,"bpm":96,"bpmUnit":"DOTTED_QUARTER","totalScore":77,"startNoteIdx":16,"endNoteIdx":19,
                   "startedAt":"2026-11-01T10:00:00Z","recordingAvailable":true,"memo":null,"isNew":true}""",
            ),
            newest.path("items").path(1),
        )
        assertEquals(listOf(0, 0), listOf("startColumnIdx", "endColumnIdx").map { newest.path("items").path(2).path(it).asInt() })
        assertEquals(listOf("991347", "991342", "991346"), ids(result(list(OWNER, "?songId=1&sort=OLDEST"))))
        assertEquals(listOf("991348"), ids(result(list(OWNER, "?songId=2"))))

        val secondPage = result(list(OWNER, "?songId=1&size=2&page=1"))
        assertEquals(listOf("991347"), ids(secondPage))
        assertEquals(listOf(1, 2, 3, 2), listOf("page", "size", "totalElements", "totalPages").map { secondPage.path(it).asInt() })
        assertEquals(emptyList<String>(), ids(result(list(OWNER, "?songId=1&size=2&page=2"))))
        val noRecords = result(list(OWNER, "?songId=4"))
        assertEquals(listOf("4", "0", "0", "3"), listOf(noRecords.path("songId").asString(), noRecords.path("totalElements").asString(),
            noRecords.path("totalPages").asString(), noRecords.path("songs").size().toString()))
        assertEquals(emptyList<String>(), ids(noRecords))

        // 목록은 NEW 표시를 끄지 않고, 기록 상세(PRA-03)를 처음 열면 꺼진다
        assertNull(jdbc.queryForObject("SELECT viewed_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertEquals(200, get(991342, OWNER).statusCode())
        assertEquals(false, result(list(OWNER, "?songId=1")).path("items").path(1).path("isNew").asBoolean())

        val other = result(list(OTHER, ""))
        assertEquals(listOf("1", "991344"), listOf(other.path("totalRecordCount").asString()) + ids(other))
        assertEquals(401, list(null, "").statusCode())
        for (query in listOf("?size=101", "?page=-1", "?songId=0", "?songId=abc", "?sort=newest")) {
            val response = list(OWNER, query)
            assertEquals(400, response.statusCode(), query)
            assertEquals("INVALID_REQUEST", json.readTree(response.body()).path("result").path("errorCode").asString(), query)
        }
    }

    // 저장 본문은 생략과 null을 구분하려고 JSON 노드로 받는다. 문서에는 그 내부 대신 실제 본문 형식이 보여야 한다.
    @Test
    fun `the API document shows the save body as an optional memo object`() {
        val doc = json.readTree(
            client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString()).body(),
        )
        val body = doc.path("paths").path("/api/practice-sessions/{sessionId}/record").path("put").path("requestBody")
        assertFalse(body.path("required").asBoolean())
        val schema = body.path("content").path("application/json").path("schema")
        assertTrue(schema.path("\$ref").isMissingNode, schema.toString())
        assertEquals(listOf("memo"), schema.path("properties").propertyNames().toList())
        val memo = schema.path("properties").path("memo")
        assertEquals(listOf("string", "true", "500"), listOf("type", "nullable", "maxLength").map { memo.path(it).asString() })
    }

    // 이 서버는 로컬 녹음 저장소를 쓴다. 그 읽기 주소는 AI용 내부 주소라 앱에 주지 않는다(infra/storage/README.md).
    // S3 호환 저장소의 주소 발급은 RecordingPlaybackDatabaseTests가 본다.
    @Test
    fun `local recording storage never hands out its internal read address for playback`() {
        val unavailable = recording(991342, OWNER)
        assertEquals(503, unavailable.statusCode())
        assertEquals("SERVICE_UNAVAILABLE", json.readTree(unavailable.body()).path("result").path("errorCode").asString())
        assertFalse("recordings.test" in unavailable.body())

        // 다시 들을 녹음이 없으면 저장소와 관계없이 404다.
        jdbc.update("UPDATE practice_sessions SET recording_url=NULL WHERE id=991346")
        for (sessionId in listOf(991344L, 991345L, 991346L, 999999L)) assertEquals(404, recording(sessionId, OWNER).statusCode(), "$sessionId")
        assertEquals(401, recording(991342, null).statusCode())
        assertEquals(400, recording(0, OWNER).statusCode())
    }

    // 991342: 저장·메모·첫 조회까지 한 기록(녹음 파일 있음). 991346: 녹음 없이 저장한 기록. 991347: 저장하지 않은 세션(파일 있음).
    @Test
    fun `deleting a record removes its recording and hides it everywhere while growth keeps the score`() {
        session(991347, OWNER, 16, 17, "DONE", scoreVersion = 1)
        evaluation(991347, 50)
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:05:00', memo='지울 메모', viewed_at='2026-11-01 10:07:00' WHERE id=991342")
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:06:00', recording_url=NULL WHERE id=991346")
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 12:00:00' WHERE id=991344")
        val files = listOf(991342L, 991344L, 991345L, 991347L).associateWith { id ->
            recordingRoot.resolve(recordingKey(id)).also { Files.createDirectories(it.parent); Files.write(it, "m4a-$id".toByteArray()) }
        }
        val growthBefore = result(growth(OWNER))
        assertEquals(200, evaluationOf(991342, OWNER).statusCode())

        // 저장하지 않은 세션, 다른 회원의 기록, 이미 지운 기록, 없는 세션은 파일과 DB를 건드리지 않고 404다.
        for (sessionId in listOf(991347L, 991344L, 991345L, 999999L)) assertEquals(404, deleteRecord(sessionId, OWNER).statusCode(), "$sessionId")
        assertTrue(files.values.all { Files.exists(it) })
        assertEquals(401, deleteRecord(991342, null).statusCode())
        assertEquals(400, deleteRecord(0, OWNER).statusCode())

        val before = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val deleted = result(deleteRecord(991342, OWNER))
        assertEquals(listOf("991342", "true", "true"), listOf("sessionId", "deleted", "recordingDeleted").map { deleted.path(it).asString() })
        val deletedAt = Instant.parse(deleted.path("deletedAt").asString())
        assertTrue(deletedAt >= before && deletedAt <= Instant.now(), "$deletedAt")
        assertEquals(deletedAt, stored(991342, "record_deleted_at"))
        assertFalse(Files.exists(files.getValue(991342)))
        assertTrue(Files.exists(files.getValue(991344)) && Files.exists(files.getValue(991347)))
        // 녹음 경로·메모만 비우고 저장 시각·첫 조회 시각·평가는 남긴다.
        assertNull(jdbc.queryForObject("SELECT recording_url FROM practice_sessions WHERE id=991342", String::class.java))
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))
        assertEquals(listOf("2026-11-01T10:05:00Z", "2026-11-01T10:07:00Z"), listOf("saved_at", "viewed_at").map { stored(991342, it).toString() })
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluations WHERE session_id=991342", Int::class.java))

        // 지운 뒤에는 기록 상세·평가·저장·재생·다시 지우기가 모두 404이고 목록에서 빠진다. 성장 요약은 그대로다.
        for (response in listOf(get(991342, OWNER), evaluationOf(991342, OWNER), put(991342, OWNER, null), recording(991342, OWNER), deleteRecord(991342, OWNER))) {
            assertEquals(404, response.statusCode(), response.uri().toString())
        }
        assertEquals(listOf("991346"), ids(result(list(OWNER, "?songId=1"))))
        assertEquals(growthBefore, result(growth(OWNER)))

        // 녹음 없이 저장한 기록도 지운다.
        assertEquals("false", result(deleteRecord(991346, OWNER)).path("recordingDeleted").asString())

        // 녹음을 지우지 못하면(로컬 저장소가 받지 않는 옛 키) 503이고 기록은 그대로다.
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:08:00', memo='남길 메모', recording_url='legacy/991347.m4a' WHERE id=991347")
        val unavailable = deleteRecord(991347, OWNER)
        assertEquals(503, unavailable.statusCode())
        assertEquals("SERVICE_UNAVAILABLE", json.readTree(unavailable.body()).path("result").path("errorCode").asString())
        assertEquals(
            listOf("legacy/991347.m4a", "남길 메모"),
            listOf("recording_url", "memo").map { jdbc.queryForObject("SELECT $it FROM practice_sessions WHERE id=991347", String::class.java) },
        )
        assertNull(jdbc.queryForObject("SELECT record_deleted_at FROM practice_sessions WHERE id=991347", LocalDateTime::class.java))
    }

    // 저장(REC-01)은 세션 행을 잠그고 상태를 읽은 뒤 갱신하고 다시 읽는다. 그사이 온 삭제가 잠금을 기다리지 않으면
    // 저장이 방금 지워진 기록을 다시 읽지 못해 500이 된다.
    @Test
    fun `a deletion arriving during a save waits for the session row lock`() {
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 10:05:00' WHERE id=991342")
        val now = LocalDateTime.parse("2026-11-02T04:00:00")
        var deleting: CompletableFuture<Boolean>? = null
        try {
            TransactionTemplate(manager).executeWithoutResult {
                assertEquals("DONE", records.lockStatus(991342, OWNER))
                deleting = CompletableFuture.supplyAsync { records.markDeleted(991342, OWNER, now) }
                awaitSessionLock(991342, checkNotNull(deleting))
                records.save(991342, OWNER, now, MemoChange.Replace("저장 중 메모"))
                assertEquals("저장 중 메모", records.findSession(991342, OWNER)?.memo)
            }
            // 저장이 끝난 뒤에 삭제가 이어서 지운다.
            assertTrue(checkNotNull(deleting).get(15, TimeUnit.SECONDS))
            assertNotNull(jdbc.queryForObject("SELECT record_deleted_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
            assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991342", String::class.java))
        } finally {
            // 실패해도 삭제 작업이 끝난 뒤 정리해야 서로 겹치지 않는다.
            deleting?.get(15, TimeUnit.SECONDS)
        }
    }

    // 확인과 갱신 사이에 다른 요청이 먼저 지우거나 저장한 경우는 마지막 조건부 갱신이 막는다. 저장소를 직접 불러 확인한다.
    @Test
    fun `final conditional updates leave records deleted or never saved meanwhile untouched`() {
        jdbc.update("UPDATE practice_sessions SET saved_at='2026-11-01 12:00:00' WHERE id=991344")
        val now = LocalDateTime.parse("2026-11-02T04:00:00")
        assertEquals(listOf(false, false, false), listOf(991345L, 991342L, 991344L).map { records.markDeleted(it, OWNER, now) })
        assertEquals("2026-11-01T11:00:00Z", stored(991345, "record_deleted_at").toString())
        assertNotNull(jdbc.queryForObject("SELECT recording_url FROM practice_sessions WHERE id=991345", String::class.java))
        assertNull(jdbc.queryForObject("SELECT record_deleted_at FROM practice_sessions WHERE id=991342", LocalDateTime::class.java))
        assertNull(jdbc.queryForObject("SELECT record_deleted_at FROM practice_sessions WHERE id=991344", LocalDateTime::class.java))

        records.save(991345, OWNER, now, MemoChange.Replace("지운 뒤 메모"))
        assertNull(jdbc.queryForObject("SELECT memo FROM practice_sessions WHERE id=991345", String::class.java))
    }

    private fun session(
        id: Long, userId: Long, start: Int, end: Int, status: String, scoreVersion: Int,
        songId: Long = 1, startedAt: String = "2026-11-01 10:00:00",
    ) {
        jdbc.update(
            """INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,recording_url,recording_bytes,
               recording_duration_ms,recording_sample_rate,recording_channels,status,analysis_attempt,analysis_started_at,started_at,ended_at)
               VALUES(?,?,?,?,96,'DOTTED_QUARTER',?,?,?,1024,2876,44100,1,?,1,'2026-11-01 10:00:40',?,'2026-11-01 10:00:30')""",
            id, userId, songId, scoreVersion, start, end, recordingKey(id), status, startedAt,
        )
    }

    // 녹음 저장소가 받는 키 형식(recordings/{세션}/{UUID}.m4a)을 따른다.
    private fun recordingKey(sessionId: Long) = "recordings/$sessionId/00000000-0000-4000-8000-%012d.m4a".format(sessionId)

    private fun evaluation(sessionId: Long, total: Int) {
        jdbc.update(
            "INSERT INTO evaluations(id,session_id,total_score,pitch_score,timing_score,duration_score,model_version,evaluated_at) VALUES(?,?,?,?,?,?,'test','2026-11-01 10:01:00')",
            sessionId, sessionId, total, total, total, total,
        )
    }

    private fun awaitSessionLock(sessionId: Long, worker: CompletableFuture<*>) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (System.nanoTime() < deadline) {
            val waiting = jdbc.queryForObject(
                """SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                   JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                   WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='practice_sessions' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?""",
                Int::class.java, sessionId.toString(),
            )!! > 0
            if (waiting) return
            if (worker.isDone) fail("deletion finished without waiting for the save's row lock: ${worker.get()}")
        }
        fail("deletion must wait for the save's row lock")
    }

    private fun ids(list: JsonNode): List<String> = list.path("items").values().map { it.path("sessionId").asString() }

    private fun stored(sessionId: Long, column: String): Instant =
        assertNotNull(jdbc.queryForObject("SELECT $column FROM practice_sessions WHERE id=?", LocalDateTime::class.java, sessionId)).toInstant(ZoneOffset.UTC)

    private fun result(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result")
    }

    private fun put(sessionId: Long, user: Long?, body: String?): HttpResponse<String> {
        val request = authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId/record"), user)
        if (body == null) request.PUT(HttpRequest.BodyPublishers.noBody())
        else request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body))
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(sessionId: Long, user: Long?): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun deleteRecord(sessionId: Long, user: Long?): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId/record"), user).DELETE().build(), HttpResponse.BodyHandlers.ofString())

    private fun evaluationOf(sessionId: Long, user: Long?): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId/evaluation"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun growth(user: Long?): HttpResponse<String> = client.send(
        authorized(URI("http://localhost:$port/api/users/me/growth/summary?from=2026-10-01&to=2026-11-30"), user).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun recording(sessionId: Long, user: Long?): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/practice-sessions/$sessionId/recording"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun list(user: Long?, query: String): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port/api/users/me/practice-records$query"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

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

    companion object {
        // 로컬 녹음 저장소는 심볼릭 링크가 낀 경로를 거부한다(macOS의 /var 등). 실제 경로로 바꿔 쓴다.
        val recordingRoot: Path = Files.createTempDirectory("practice-records-").toRealPath()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("eolssu.recording-storage.type") { "local" }
            registry.add("eolssu.recording-storage.local-directory") { recordingRoot.toString() }
            registry.add("eolssu.recording-storage.local-read-base-url") { "http://recordings.test/api/recordings/read" }
            registry.add("eolssu.recording-storage.local-signing-secret") { "practice-record-recording-read-key-342" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
