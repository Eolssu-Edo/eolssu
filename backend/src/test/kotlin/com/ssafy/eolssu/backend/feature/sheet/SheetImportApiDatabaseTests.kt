package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper

private const val SECRET = "sheet-import-api-test-signing-key-471"
private const val OWNER = 991471L
private const val OTHER = 991472L

/** SHT-07·10과 SHT-01·02·08의 불러오기 필드(S15P31E102-471). 원본은 임시 디렉터리 저장소(local)에 둔다. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SheetImportApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private var jangdanId = 0L
    private val png = sheetImageBytes()

    @BeforeEach
    fun fixtures() {
        cleanup()
        for (id in listOf(OWNER, OTHER)) {
            jdbc.update(
                "INSERT INTO users(id,email,password_hash,nickname,instrument_id,notation_mode,status) VALUES(?,?,'fixture','불러오기',1,'HANGUL','ACTIVE')",
                id, "import$id@test.invalid",
            )
        }
        jangdanId = jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=1", Long::class.java)!!
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id IN (?,?)", OWNER, OTHER)
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", OWNER, OTHER)
    }

    @Test
    fun `an accepted staff import queues a conversion that every sheet API reports`() {
        val response = import(OWNER, "k-1", staff(), listOf("1쪽.png" to png, "2쪽.png" to png + 9))
        assertEquals(202, response.statusCode(), response.body())
        val accepted = json.readTree(response.body()).path("result")
        val sheetId = accepted.path("sheetId").asString()
        assertEquals("STAFF_OMR", accepted.path("source").asString())
        assertEquals("QUEUED", accepted.path("conversionStatus").asString())
        assertEquals(2, accepted.path("pageCount").intValue())

        val keys = jdbc.queryForList("SELECT object_key FROM sheet_source_files WHERE sheet_id=? ORDER BY file_idx", String::class.java, sheetId)
        assertEquals(2, keys.size)
        assertTrue(keys.all { it!!.startsWith("sheet-sources/$OWNER/") && storageDir.resolve(it).exists() })

        val status = get(OWNER, "/api/users/me/sheets/$sheetId/conversion")
        assertEquals(200, status.statusCode(), status.body())
        assertEquals("no-store", status.headers().firstValue("Cache-Control").orElse(""))
        val conversion = json.readTree(status.body()).path("result")
        assertEquals("QUEUED", conversion.path("status").asString())
        assertTrue(conversion.path("jangdanAuto").booleanValue() && conversion.path("bpmAuto").booleanValue())
        assertEquals(2000, conversion.path("pollAfterMs").intValue())
        assertEquals(1, conversion.path("attempt").intValue())
        assertFalse(conversion.path("retryable").booleanValue())
        assertEquals(404, get(OTHER, "/api/users/me/sheets/$sheetId/conversion").statusCode())

        val item = json.readTree(get(OWNER, "/api/users/me/sheets").body()).path("result").path("items").get(0)
        assertEquals("QUEUED", item.path("conversionStatus").asString())
        assertEquals(2, item.path("sourcePageCount").intValue())
        val score = json.readTree(get(OWNER, "/api/users/me/sheets/$sheetId/score").body()).path("result")
        assertEquals("QUEUED", score.path("conversionStatus").asString())
        assertEquals(0, score.path("columnCount").intValue())
        assertEquals("STAFF_OMR", score.path("source").path("type").asString())
        assertEquals("1쪽.png", score.path("source").path("fileName").asString())
        assertEquals("Eb4", score.path("source").path("hwangPitch").asString())
        assertTrue(score.path("scoreData").isNull && score.path("notices").isArray)
    }

    @Test
    fun `the same key replays the same import and refuses different content`() {
        val first = import(OWNER, "k-same", jeongganbo(), listOf("a.png" to png))
        val again = import(OWNER, "k-same", jeongganbo(), listOf("renamed.png" to png))
        assertEquals(202, again.statusCode(), again.body())
        assertEquals(sheetId(first), sheetId(again))
        assertEquals(1, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
        assertEquals(1, count("SELECT COUNT(*) FROM sheet_source_files f JOIN user_sheets s ON s.id=f.sheet_id WHERE s.user_id=$OWNER"))

        val other = import(OWNER, "k-same", jeongganbo(), listOf("a.png" to png + 1))
        assertEquals("IDEMPOTENCY_CONFLICT", errorCode(other))
    }

    @Test
    fun `one conversion at a time and ten sheets per member`() {
        val first = import(OWNER, "k-a", jeongganbo(), listOf("a.png" to png))
        assertEquals(202, first.statusCode(), first.body())
        val busy = import(OWNER, "k-b", jeongganbo(), listOf("b.png" to png))
        assertEquals(409, busy.statusCode())
        assertEquals("CONVERSION_IN_PROGRESS", errorCode(busy))
        assertEquals(202, import(OTHER, "k-b", jeongganbo(), listOf("b.png" to png)).statusCode())

        jdbc.update("UPDATE sheet_conversions SET status='DONE', finished_at=accepted_at WHERE user_id=$OWNER")
        for (index in 2..10) {
            jdbc.update(
                """INSERT INTO user_sheets (user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,idempotency_key,request_hash)
                VALUES (?, ?, 'JEONGGANBO', 'MANUAL', 'NEEDS_REVIEW', ?, 60, 'JANGDAN_BEAT', 4, ?, REPEAT('0', 64))""",
                OWNER, "악보 $index", jangdanId, "fill-$index",
            )
        }
        val objects = storedObjects()
        val full = import(OWNER, "k-c", jeongganbo(), listOf("c.png" to png))
        assertEquals(409, full.statusCode())
        assertEquals("SHEET_LIMIT_EXCEEDED", errorCode(full))
        assertEquals(objects, storedObjects())
    }

    @Test
    fun `files and metadata outside SHT-07 are refused before anything is stored`() {
        assertEquals("UNREADABLE_FILE", errorCode(import(OWNER, "broken-image", staff(), listOf("broken.png" to png.copyOf(11)))))
        assertEquals("UNSUPPORTED_FILE_TYPE", errorCode(import(OWNER, "k-1", jeongganbo(), listOf("a.png" to "GIF89a".toByteArray()))))
        assertEquals("INVALID_REQUEST", errorCode(import(OWNER, "k-2", """{"title":"x","notation":"JEONGGANBO","instrumentId":"1"}""", listOf("a.png" to png))))
        assertEquals("INVALID_REQUEST", errorCode(import(OWNER, "k-3", """{"title":"x","notation":"STAFF","instrumentId":1}""", listOf("a.png" to png))))
        assertEquals("INVALID_REQUEST", errorCode(import(OWNER, "k-4", "not json", listOf("a.png" to png))))
        assertEquals("RESOURCE_NOT_FOUND", errorCode(import(OWNER, "k-5", jeongganbo(jangdan = "999999"), listOf("a.png" to png))))
        assertEquals(0, count("SELECT COUNT(*) FROM user_sheets WHERE user_id=$OWNER"))
        assertEquals(400, import(OWNER, null, jeongganbo(), listOf("a.png" to png)).statusCode())
        assertEquals(401, import(null, "k-7", jeongganbo(), listOf("a.png" to png)).statusCode())
    }

    @Test
    fun `deleting an imported sheet removes its objects and rows`() {
        val sheetId = sheetId(import(OWNER, "k-del", jeongganbo(), listOf("a.png" to png, "b.png" to png)))
        val keys = jdbc.queryForList("SELECT object_key FROM sheet_source_files WHERE sheet_id=?", String::class.java, sheetId)
        val pageKey="sheet-sources/$OWNER/pages/$sheetId/0.png"
        Files.createDirectories(storageDir.resolve(pageKey).parent)
        Files.write(storageDir.resolve(pageKey),png)
        jdbc.update("INSERT INTO sheet_source_pages(sheet_id,page_idx,object_key,width,height) VALUES(?,0,?,200,100)",sheetId,pageKey)

        assertEquals(404, send(OTHER, "DELETE", "/api/users/me/sheets/$sheetId").statusCode())
        assertTrue(keys.all { storageDir.resolve(it).exists() })
        assertEquals(204, send(OWNER, "DELETE", "/api/users/me/sheets/$sheetId").statusCode())
        assertTrue(keys.none { storageDir.resolve(it).exists() })
        assertFalse(storageDir.resolve(pageKey).exists())
        assertEquals(0, count("SELECT COUNT(*) FROM sheet_conversions WHERE sheet_id=$sheetId"))
        assertEquals(0, count("SELECT COUNT(*) FROM sheet_source_files WHERE sheet_id=$sheetId"))
    }

    @Test
    fun `retry API exposes eligibility and accepts the same retry once`() {
        val id=sheetId(import(OWNER,"k-retry",staff(),listOf("a.png" to png)))
        jdbc.update("UPDATE sheet_conversions SET status='FAILED',failure_code='SERVER_ERROR',failure_cause='SERVER_ERROR',finished_at=accepted_at WHERE sheet_id=?",id)
        val status=json.readTree(get(OWNER,"/api/users/me/sheets/$id/conversion").body()).path("result")
        assertTrue(status.path("retryable").booleanValue())
        assertEquals(404,send(OTHER,"POST","/api/users/me/sheets/$id/conversion/retry").statusCode())
        repeat(2) {
            val response=send(OWNER,"POST","/api/users/me/sheets/$id/conversion/retry")
            assertEquals(202,response.statusCode(),response.body())
            assertEquals(2,json.readTree(response.body()).path("result").path("attempt").intValue())
        }
    }

    @Test
    fun `score API returns conversion issues and notices without caching`() {
        val id=sheetId(import(OWNER,"k-details",staff(),listOf("a.png" to png)))
        jdbc.update("""UPDATE sheet_conversions SET status='DONE',result_json='{"issues":[{"issueId":"i1","type":"LOW_CONFIDENCE","cellId":"c1-j0","eventId":null,"candidates":[]}],"notices":[{"type":"TRANSPOSED"}],"pages":[]}',finished_at=accepted_at WHERE sheet_id=?""",id)
        jdbc.update("UPDATE user_sheets SET pending_cell_count=1 WHERE id=?",id)
        val response=get(OWNER,"/api/users/me/sheets/$id/score")
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(""))
        val details=json.readTree(response.body()).path("result")
        assertEquals("i1",details.path("issues").get(0).path("issueId").asString())
        assertEquals("TRANSPOSED",details.path("notices").get(0).path("type").asString())
    }

    @Test
    fun `the schema keeps one active conversion per member and staff-only fields`() {
        val sheetId = sheetId(import(OWNER, "k-db", jeongganbo(), listOf("a.png" to png))).toLong()
        val second = jdbc.queryForObject(
            """SELECT id FROM user_sheets WHERE id = (SELECT MAX(id) FROM user_sheets)""", Long::class.java,
        )!!
        assertEquals(sheetId, second)
        jdbc.update(
            """INSERT INTO user_sheets (user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,idempotency_key,request_hash)
            VALUES (?, '둘째', 'JEONGGANBO', 'JEONGGANBO_OMR', 'NEEDS_REVIEW', ?, 60, 'JANGDAN_BEAT', 1, 'k-db-2', REPEAT('0', 64))""",
            OWNER, jangdanId,
        )
        val other = jdbc.queryForObject("SELECT id FROM user_sheets WHERE idempotency_key='k-db-2'", Long::class.java)!!
        fun conversion(notation: String, jangdan: Long?, hwang: String?, status: String = "QUEUED") = jdbc.update(
            """INSERT INTO sheet_conversions (sheet_id,user_id,notation,instrument_id,jangdan_id,hwang_pitch,status,page_count,accepted_at)
            VALUES (?, ?, ?, 1, ?, ?, ?, 1, NOW(6))""",
            other, OWNER, notation, jangdan, hwang, status,
        )
        assertFailsWith<DataAccessException> { conversion("JEONGGANBO", jangdanId, null) }
        assertFailsWith<DataAccessException> { conversion("JEONGGANBO", null, null, status = "DONE") }
        assertFailsWith<DataAccessException> { conversion("JEONGGANBO", jangdanId, "Eb4", status = "DONE") }
        assertFailsWith<DataAccessException> {
            jdbc.update("UPDATE user_sheets SET source='PDF' WHERE id=?", other)
        }
        assertEquals(1, conversion("STAFF", null, "Eb4", status = "DONE"))
    }

    private fun staff() = """{"title":"아리랑 (오선보)","notation":"STAFF","instrumentId":"1","hwangPitch":"Eb4"}"""

    private fun jeongganbo(jangdan: String = jangdanId.toString()) =
        """{"title":"아리랑","notation":"JEONGGANBO","instrumentId":"1","jangdanId":"$jangdan","bpm":60}"""

    private fun import(user: Long?, key: String?, metadata: String, files: List<Pair<String, ByteArray>>): HttpResponse<String> {
        val boundary = "----eolssu-${UUID.randomUUID()}"
        val body = ByteArrayOutputStream()
        fun text(value: String) = body.write(value.toByteArray())
        for ((name, bytes) in files) {
            text("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$name\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            body.write(bytes)
            text("\r\n")
        }
        text("--$boundary\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n$metadata\r\n--$boundary--\r\n")
        val request = authorized(URI("http://localhost:$port/api/users/me/sheets/imports"), user)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
        if (key != null) request.header("Idempotency-Key", key)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun sheetId(response: HttpResponse<String>): String {
        assertEquals(202, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result").path("sheetId").asString()
    }

    private fun errorCode(response: HttpResponse<String>) = json.readTree(response.body()).path("result").path("errorCode").asString()

    private fun storedObjects(): Set<Path> = Files.walk(storageDir).use { paths -> paths.filter(Files::isRegularFile).toList().toSet() }

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!

    private fun get(user: Long?, path: String): HttpResponse<String> =
        client.send(authorized(URI("http://localhost:$port$path"), user).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun send(user: Long?, method: String, path: String): HttpResponse<String> = client.send(
        authorized(URI("http://localhost:$port$path"), user).method(method, HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

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
        private val storageDir: Path = Files.createTempDirectory("sheet-storage-")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.sheet-conversion.omr-base-url") { "http://127.0.0.1:1" }
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("eolssu.sheet-storage.type") { "local" }
            registry.add("eolssu.sheet-storage.local-directory") { storageDir.toString() }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
