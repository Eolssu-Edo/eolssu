package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.sheet.application.SheetExpressions
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/** Authenticated HTTP and real MySQL/Flyway; OMR, renderer and object storage are explicit doubles. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SheetRhythmApiDatabaseTests.ExternalDoubles::class)
class SheetRhythmApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var queue: ConversionQueue
    @Autowired lateinit var scores: SheetScores
    @Autowired lateinit var playback: CapturingPlayback
    @Value("\${local.server.port}") private var port = 0
    @TempDir lateinit var directory: Path
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newHttpClient()

    enum class Meter(val cells: List<Int>, val unit: String, val ticksPerBeat: Int, val totalBeats: Double) {
        TWO_FOUR(listOf(2,2), "QUARTER", 12, 4.0),
        THREE_FOUR(listOf(3,3), "QUARTER", 12, 6.0),
        SIX_EIGHT(listOf(2,2), "DOTTED_QUARTER", 12, 4.0),
        FIVE_EIGHT(listOf(5,5), "QUARTER", 24, 5.0),
        SEVEN_EIGHT(listOf(7,7), "QUARTER", 24, 7.0),
        MIXED(listOf(3,2,3), "QUARTER", 12, 8.0),
        MIXED_UNITS(listOf(6,5,6), "QUARTER", 24, 8.5),
    }

    @BeforeEach fun setup() {
        cleanup()
        playback.estimates.clear()
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(993883,'리듬 보존 검사','ACTIVE',1)")
        jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,bpm,bpm_unit,column_count,idempotency_key,request_hash)
            VALUES(993883,993883,'리듬 보존 검사','JEONGGANBO','STAFF_OMR','NEEDS_REVIEW',60,'QUARTER',1,'rhythm883',REPEAT('0',64))""")
        jdbc.update("""INSERT INTO sheet_conversions(sheet_id,user_id,notation,instrument_id,status,page_count,accepted_at)
            VALUES(993883,993883,'STAFF',1,'QUEUED',1,UTC_TIMESTAMP(6))""")
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id=993883")
        jdbc.update("DELETE FROM users WHERE id=993883")
    }
    @AfterEach fun closeClient() = client.close()

    @ParameterizedTest @EnumSource(Meter::class)
    fun `unaccompanied imported meters preserve ties timing tempo and per-sheet settings through SQL and HTTP`(meter: Meter) {
        val original = convert(meter)
        val imported = query()
        assertTrue(imported.path("jangdanId").isNull)
        assertEquals("DONE", imported.path("conversionStatus").asString())
        assertEquals(80, imported.path("bpm").asInt())
        assertRhythm(original, imported.path("scoreData"), meter)
        val originalSource = imported.path("authoring").path("source")
        val basic = basic(meter)
        val draft = saveBody(imported.path("scoreData"), 1).put("draftRevision", 0)
        draft.set("authoring", reference(imported.path("authoring")).apply { set("accompaniment", basic) })
        assertStatus(200, call("PUT", "/score/draft", draft))
        var editing = query()
        assertEquals(basic, editing.path("draft").path("authoring").path("accompaniment"))
        assertTrue(editing.path("authoring").path("accompaniment").isMissingNode)
        assertTrue(editing.path("draft").path("jangdanId").isNull)
        assertRhythm(original, editing.path("draft").path("score"), meter)

        // An older client omits authoring on the next autosave and confirmation.
        draft.remove("authoring"); draft.put("draftRevision", 1)
        assertStatus(200, call("PUT", "/score/draft", draft))
        assertStatus(200, call("PUT", "/score", saveBody(editing.path("draft").path("score"), 1).put("bpm", 96)))
        val confirmed = query()
        assertEquals("EVALUABLE", confirmed.path("reviewStatus").asString())
        assertEquals("2", confirmed.path("scoreVersion").asString())
        assertTrue(confirmed.path("draft").isNull)
        assertTrue(confirmed.path("jangdanId").isNull)
        assertEquals(basic, confirmed.path("authoring").path("accompaniment"))
        assertEquals(originalSource, confirmed.path("authoring").path("source"))
        assertRhythm(original, confirmed.path("scoreData"), meter)
        val timeline = confirmed.path("events").toList()
        assertEquals(original.path("events").size(), timeline.size)
        assertEquals(meter.totalBeats, timeline.sumOf { it.path("durationBeat").asDouble() }, 1e-10)
        assertEquals(listOf(96.0,144.0), confirmed.path("expressions").path("performancePlan").path("tempos").toList().map { it.path("bpm").asDouble() })
        assertEquals(SheetExpressions(json).hash(confirmed.path("scoreData").toString()), confirmed.path("expressions").path("sourceSha256").asString())

        assertStatus(202, call("POST", "/audio", json.createObjectNode().put("withJangdan", false)))
        val audio = json.readTree(jdbc.queryForObject("SELECT request_json FROM sheet_audios WHERE sheet_id=993883", String::class.java)!!)
        assertFalse(audio.path("withJangdan").booleanValue())
        assertTrue(audio.path("jangdan").isNull)
        assertRhythm(original, audio.path("score"), meter)
        assertEquals(confirmed.path("expressions"), audio.path("expressions"))
        assertEquals(2, playback.estimates.size)
        assertStatus(400, call("POST", "/audio", json.createObjectNode().put("withJangdan", true)))

        // Clear in a draft, omit it in a later save, and read the server's canonical absence.
        val cleared = saveBody(confirmed.path("scoreData"), 2).put("bpm", 96).put("draftRevision", 0)
        cleared.set("authoring", reference(confirmed.path("authoring")).putNull("accompaniment"))
        assertStatus(200, call("PUT", "/score/draft", cleared))
        editing = query()
        assertTrue(editing.path("draft").path("authoring").path("accompaniment").isMissingNode)
        assertEquals(basic, editing.path("authoring").path("accompaniment"))
        assertStatus(200, call("PUT", "/score", saveBody(editing.path("draft").path("score"), 2).put("bpm", 96)))
        val without = query()
        assertTrue(without.path("authoring").path("accompaniment").isMissingNode)
        assertEquals(originalSource, without.path("authoring").path("source"))
        assertRhythm(original, without.path("scoreData"), meter)
        assertNull(jdbc.queryForObject("SELECT jangdan_id FROM user_sheets WHERE id=993883", Long::class.java))
    }

    @Test fun `invalid metadata and mismatched traditional jangdan fail before changing the stored draft or score`() {
        convert(Meter.FIVE_EIGHT)
        val before = query()
        val score = before.path("scoreData")
        val malformed = listOf(json.readTree("[]"), json.readTree("""{"schema":"sheet-authoring/v1","source":{},"accompaniment":true}"""),
            reference(before.path("authoring")).apply { set("accompaniment", basic(Meter.FIVE_EIGHT).put("startTick", 120)) })
        for (metadata in malformed) for (suffix in listOf("/score", "/score/draft")) {
            val body = saveBody(score, 1).put("draftRevision", 0).apply { set("authoring", metadata) }
            val response = call("PUT", suffix, body)
            assertStatus(400, response)
            assertEquals("INVALID_REQUEST", json.readTree(response.body()).path("result").path("errorCode").asString())
        }
        val mismatched = score.deepCopy() as ObjectNode
        (mismatched.path("music") as ObjectNode).put("jangdan", "4박 기본 반주")
        assertStatus(400, call("PUT", "/score", saveBody(mismatched, 1).put("jangdanId", "12")))
        for (suffix in listOf("/score", "/score/draft")) {
            val conflict = saveBody(mismatched, 1).put("jangdanId", "12").put("draftRevision", 0)
            conflict.set("authoring", reference(before.path("authoring")).apply { set("accompaniment", basic(Meter.FIVE_EIGHT)) })
            assertStatus(400, call("PUT", suffix, conflict))
        }
        assertEquals(before.path("scoreData"), query().path("scoreData"))
        assertEquals("1", query().path("scoreVersion").asString())
        assertEquals(0, jdbc.queryForObject("SELECT draft_revision FROM user_sheets WHERE id=993883", Int::class.java))
        assertTrue(playback.estimates.isEmpty())
    }

    private fun convert(meter: Meter): ObjectNode {
        val score = fixture(meter)
        val hash = SheetExpressions(json).hash(score.toString())
        val response = json.createObjectNode().put("status", "DONE").put("bpm", 80).apply {
            set("score", score); putObject("jgb").put("v", 1); putArray("issues"); putArray("notices")
            set("expressions", json.readTree("""{"schema":"common-score-expressions/v1","sourceSha256":"$hash","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$hash","tempos":[{"gak":1,"cell":1,"bpm":80},{"gak":2,"cell":1,"bpm":120}]}}"""))
        }
        val source = directory.resolve("source.png"); Files.write(source, byteArrayOf(1,2,3))
        val key = "sheet-import-temp/993883/source.png"
        val local = LocalSheetSourceStorage(directory.resolve("sources"))
        val sourceStorage = object : SheetSourceStorage by local {
            override fun readUrl(key: String,ttl: Duration) = "https://fixture.invalid/source.png"
        }
        local.put(key, source, "image/png")
        jdbc.update("""INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name)
            VALUES(993883,0,?,'image/png',3,'source.png')""", key)
        val recognition = RecognitionClient { request ->
            assertTrue(request.path("jangdan").isNull)
            assertEquals("STAFF", request.path("notation").asString())
            response
        }
        ConversionWorker(queue, sourceStorage, recognition, RecognitionResultBuilder(json,scores), Clock.systemUTC(),json).runOne()
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=993883", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_files WHERE sheet_id=993883", Int::class.java))
        assertFalse(Files.exists(directory.resolve("sources").resolve(key)))
        return score
    }

    private fun fixture(meter: Meter): ObjectNode {
        val root = json.readTree(checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()) as ObjectNode
        (root.path("music") as ObjectNode).put("bpmUnit",meter.unit).put("ticksPerBeat",meter.ticksPerBeat).put("ticksPerJeonggan",12)
        val columns = root.putArray("columns"); val events = root.putArray("events")
        var index = 0
        val tieStart = meter.cells.first() - 1
        val tieId = "c0-j${tieStart}-r0-u0"
        for ((c, count) in meter.cells.withIndex()) {
            val cells = columns.addObject().put("index",c).putArray("cells")
            for (j in 0 until count) {
                val cid = "c$c-j$j"; val eid = "$cid-r0-u0"
                val continuation = index == tieStart + 1
                val unit = cells.addObject().put("id",cid).put("index",j).putArray("units").addObject().put("row",0).put("col",0)
                unit.put("eventId",if(continuation) tieId else eid).put("symbol",if(continuation) "-" else if(index==0) "쉼" else "청황")
                if(!continuation) {
                    val event = events.addObject().put("id",eid).put("cellId",cid).put("kind",if(index==0) "REST" else "NOTE")
                        .put("startTick",index*12).put("durationTick",if(index==tieStart) 24 else 12).put("breathAfter",false)
                    event.putArray("sigimsae")
                    if(index!=0) event.put("yul","황").put("octave",1) else event.putNull("yul").putNull("octave")
                }
                index++
            }
        }
        return root
    }

    private fun basic(meter: Meter) = json.createObjectNode().put("schema","basic-accompaniment/v1").put("startTick",12).apply {
        put("pulseUnit", if(meter==Meter.SIX_EIGHT) "DOTTED_QUARTER" else if(meter.ticksPerBeat==24) "EIGHTH" else "QUARTER")
        set("groups",json.valueToTree(when(meter) { Meter.FIVE_EIGHT, Meter.MIXED_UNITS -> listOf(2,3); Meter.SEVEN_EIGHT -> listOf(2,2,3); Meter.THREE_FOUR, Meter.MIXED -> listOf(3); else -> listOf(2) }))
    }
    private fun reference(metadata: JsonNode) = json.createObjectNode().put("schema","sheet-authoring/v1").apply {
        putObject("source").set("scoreSha256",metadata.path("source").path("scoreSha256"))
    }
    private fun assertRhythm(original: JsonNode, stored: JsonNode, meter: Meter) {
        assertEquals(original.path("music"), stored.path("music"))
        assertEquals(original.path("columns"), stored.path("columns"))
        assertEquals(original.path("events"), stored.path("events"))
        assertEquals(meter.cells,stored.path("columns").toList().map { it.path("cells").size() })
        assertEquals(meter.totalBeats,stored.path("events").sumOf { it.path("durationTick").asDouble()/meter.ticksPerBeat },1e-10)
    }
    private fun saveBody(score: JsonNode, version: Int) = json.createObjectNode().put("baseScoreVersion",version.toString()).putNull("jangdanId").put("bpm",80).apply { set("score",score) }
    private fun query(): JsonNode = json.readTree(call("GET","/score").also { assertStatus(200,it) }.body()).path("result")
    private fun assertStatus(status: Int, response: HttpResponse<String>) = assertEquals(status,response.statusCode(),response.body())
    private fun call(method: String, suffix: String, body: JsonNode? = null): HttpResponse<String> {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256),JWTClaimsSet.Builder().subject("993883").expirationTime(Date.from(Instant.now().plusSeconds(120))).build())
        token.sign(MACSigner(SECRET.toByteArray()))
        return client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/users/me/sheets/993883$suffix"))
            .header("Cookie","access_token=${token.serialize()}").header("Content-Type","application/json")
            .method(method,body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) } ?: HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString())
    }

    class CapturingPlayback : PlaybackClient {
        val estimates = CopyOnWriteArrayList<String>()
        override fun estimate(requestJson: String): Long { estimates += requestJson; return 1000L }
        override fun generate(requestJson: String): GeneratedSheetAudio = error("Audio synthesis is outside this persistence test")
    }
    @TestConfiguration(proxyBeanMethods=false)
    class ExternalDoubles {
        @Bean @Primary fun capturingPlayback() = CapturingPlayback()
        @Bean @Primary fun noRemoteStorage() = object : SheetSourceStorage {
            override val available = true
            override fun put(key: String,file: Path,contentType: String): Unit = error("No external objects may be written")
            override fun delete(key: String): Unit = error("No external objects may be deleted")
            override fun readUrl(key: String,ttl: Duration): String = error("No external objects may be read")
        }
    }
    companion object {
        private const val SECRET = "rhythm883-local-http-signing-key-only"
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "2" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
