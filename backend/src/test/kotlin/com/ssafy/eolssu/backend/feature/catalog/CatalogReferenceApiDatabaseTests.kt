package com.ssafy.eolssu.backend.feature.catalog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
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

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogReferenceApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = JsonMapper.builder().build()

    @Test fun `four supported instruments expose independent nominal ranges and preserve original pitch IDs`() {
        val items = body("/api/instruments").path("items").toList().associateBy { it.path("code").asString() }
        assertTrue(items.keys.containsAll(listOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM")), items.keys.toString())
        for ((code, low, high) in listOf(Triple("DANSO",3,29), Triple("SOGEUM",7,33),
            Triple("JEONGAK_DAEGEUM",-5,29), Triple("SANJO_DAEGEUM",-5,29))) {
            val id = items.getValue(code).path("instrumentId").asString()
            val pitches = body("/api/pitches?instrumentId=$id").path("items").toList()
            assertEquals(low, pitches.first().path("semitone").asInt(), code)
            assertEquals(high, pitches.last().path("semitone").asInt(), code)
            assertEquals(pitches.map { it.path("semitone").asInt() }.sorted(), pitches.map { it.path("semitone").asInt() })
        }
        val old = body("/api/pitches").path("items").toList().filter { it.path("pitchId").asString().toLong() <= 11 }
            .sortedBy { it.path("pitchId").asString().toLong() }
        assertEquals((1..11).map(Int::toString), old.map { it.path("pitchId").asString() })
        assertEquals(listOf(5,7,10,12,14,17,19,22,24,26,29), old.map { it.path("semitone").asInt() })
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM fingering_charts WHERE instrument_id IN (?, ?)", instrumentId, inactiveId)
        jdbc.update("DELETE FROM instruments WHERE id IN (?, ?) AND code IN ('CAT288_ACTIVE','CAT288_INACTIVE')", instrumentId, inactiveId)
        jdbc.update("DELETE FROM pitches WHERE id BETWEEN 992879 AND 992882 AND yul LIKE 'CAT288%'")
        jdbc.update("DELETE FROM notation_symbols WHERE id IN (992886,992887)")
        jdbc.update("DELETE FROM jangdans WHERE id=992888 AND name='CAT288_PATTERN'")
        client.close()
    }

    @Test fun `public catalog preserves seeded values and ignores stale authentication cookies`() {
        val instruments = body("/api/instruments", staleCookie = true).path("items").toList()
        val ids = instruments.map { it.path("instrumentId").asString() }
        assertEquals(ids.sortedBy { it.toLong() }, ids)
        val dansoId = jdbc.queryForObject("SELECT id FROM instruments WHERE code='DANSO'", Long::class.java)!!
        val danso = instruments.single { it.path("instrumentId").asString() == dansoId.toString() }
        assertEquals(50, danso.path("pitchToleranceCent").asInt())
        assertTrue(danso.path("rangeLowPitchId").isString)
        val pitches = body("/api/pitches?instrumentId=$dansoId").path("items").toList()
        assertEquals(26, pitches.size)
        assertEquals((3..24).toList() + listOf(26,27,28,29), pitches.map { it.path("semitone").asInt() })
        assertEquals("㴢", pitches.last().path("hanja").asString())
        assertTrue(pitches.all { it.path("description").isNull && it.path("soundUrl").isNull })
        assertTrue(body("/api/instruments/$dansoId/fingerings").path("structureImageUrl").isNull)
        val global = body("/api/pitches").path("items").toList()
        assertEquals(36, global.size)
        assertEquals((-5..24).toList() + listOf(26,27,28,29,31,33),
            global.map { it.path("semitone").asInt() })
        val original = global.filter { it.path("pitchId").asString().toLong() in 1L..11L }
            .sortedBy { it.path("pitchId").asString().toLong() }
        assertEquals((1..11).map(Int::toString), original.map { it.path("pitchId").asString() })
        assertEquals(listOf(5,7,10,12,14,17,19,22,24,26,29), original.map { it.path("semitone").asInt() })
        assertEquals(listOf("중","임","무","청황","청태","청중","청임","청무","중청황","중청태","중청중"),
            original.map { it.path("hangul").asString() })
    }

    @Test fun `range filtering uses pitch semitones with unordered IDs and inactive instruments stay private`() {
        seedInstrumentRange()
        val instruments = body("/api/instruments").path("items").toList()
        assertTrue(instruments.any { it.path("instrumentId").asString() == instrumentId.toString() })
        assertFalse(instruments.any { it.path("instrumentId").asString() == inactiveId.toString() })
        val pitches = body("/api/pitches?instrumentId=$instrumentId").path("items").toList()
        assertEquals(listOf("992881", "992880", "992879"), pitches.map { it.path("pitchId").asString() })
        assertEquals(listOf(-10,-9,-8), pitches.map { it.path("semitone").asInt() })
        for (id in listOf(inactiveId, 9223372036854775807L)) {
            assertError("/api/pitches?instrumentId=$id", 404, "RESOURCE_NOT_FOUND")
            assertError("/api/instruments/$id/fingerings", 404, "RESOURCE_NOT_FOUND")
        }
    }

    @Test fun `fingering filters stay within active instrument and retain nullable stored descriptions`() {
        seedInstrumentRange()
        jdbc.update("INSERT INTO fingering_charts (id,instrument_id,pitch_id,image_url,description) VALUES (992884,?,992880,'https://example.test/288-chart.png',NULL)", instrumentId)
        jdbc.update("INSERT INTO fingering_charts (id,instrument_id,pitch_id,image_url,description) VALUES (992885,?,992881,'https://example.test/288-other.png','other')", inactiveId)
        val all = body("/api/instruments/$instrumentId/fingerings").path("items")
        assertEquals(1, all.size())
        assertEquals("992884", all[0].path("fingeringId").asString())
        assertEquals(instrumentId.toString(), all[0].path("instrumentId").asString())
        assertTrue(all[0].path("description").isNull)
        assertEquals(all, body("/api/instruments/$instrumentId/fingerings?pitchId=992880").path("items"))
        assertEquals(0, body("/api/instruments/$instrumentId/fingerings?pitchId=992881").path("items").size())
        assertEquals(0, body("/api/instruments/$instrumentId/fingerings?pitchId=9223372036854775807").path("items").size())
    }

    @Test fun `notation descriptions preserve distinct fullwidth and ASCII glyphs and nullable images`() {
        jdbc.update("INSERT INTO notation_symbols (id,symbol,name,description,image_url) VALUES (992886,'CAT－','검증 연장','검증 설명',NULL)")
        jdbc.update("INSERT INTO notation_symbols (id,symbol,name,description,image_url) VALUES (992887,'CAT-','검증 반각','다른 설명','https://example.test/288-symbol.png')")
        val items = body("/api/notation-symbols").path("items").toList()
        val selected = items.filter { it.path("symbolId").asString() in listOf("992886", "992887") }
        assertEquals(listOf("CAT－", "CAT-"), selected.map { it.path("symbol").asString() })
        assertTrue(selected[0].path("imageUrl").isNull)
        assertEquals("https://example.test/288-symbol.png", selected[1].path("imageUrl").asString())
        assertTrue(selected.none { it.has("semanticCode") })
    }

    @Test fun `jangdan list and detail preserve actual reference pattern and BPM units`() {
        val list = body("/api/jangdans").path("items").toList()
        for (item in list) {
            val id = item.path("jangdanId").asString()
            val detail = body("/api/jangdans/$id")
            val stored = json.readTree(jdbc.queryForObject("SELECT pattern FROM jangdans WHERE id=?", String::class.java, id.toLong()))
            assertEquals(stored, detail.path("pattern"))
            assertEquals(stored.path("beats").size(), item.path("beatCount").asInt())
            assertEquals(stored.path("group").size(), item.path("groupCount").asInt())
            assertEquals(item.path("defaultBpm"), detail.path("defaultBpm"))
            assertEquals(item.path("bpmUnit"), detail.path("bpmUnit"))
            assertTrue(detail.path("soundUrl").isNull)
        }
        assertError("/api/jangdans/9223372036854775807",404,"RESOURCE_NOT_FOUND")
    }

    @Test fun `invalid stored jangdan shapes are rejected without publishing misleading rhythm data`() {
        jdbc.update("INSERT INTO jangdans (id,name,pattern,default_bpm,bpm_unit) VALUES (992888,'CAT288_PATTERN','{}',96,'DOTTED_QUARTER')")
        for (pattern in listOf("{}", "[]", """{"beats":["떵"],"group":[2]}""",
            """{"beats":[1],"group":[1]}""", """{"beats":["떵"],"group":["1"]}""",
            """{"beats":["떵"],"group":[0,1]}""", """{"beats":["떵"],"group":[2147483648]}""")) {
            jdbc.update("UPDATE jangdans SET pattern=? WHERE id=992888", pattern)
            assertError("/api/jangdans/992888",500,"INTERNAL_SERVER_ERROR")
            assertError("/api/jangdans",500,"INTERNAL_SERVER_ERROR")
        }
    }

    @Test fun `invalid public query identifiers use the shared request error contract`() {
        for (path in listOf("/api/pitches?instrumentId=0", "/api/pitches?instrumentId=", "/api/pitches?instrumentId=9223372036854775808",
            "/api/instruments/01/fingerings", "/api/instruments/1/fingerings?pitchId=-1", "/api/jangdans/abc")) {
            assertError(path,400,"INVALID_REQUEST")
        }
    }

    private fun seedInstrumentRange() {
        for ((id,yul,semitone) in listOf(Triple(992881,"CAT288L",-10), Triple(992880,"CAT288M",-9),
            Triple(992879,"CAT288H",-8), Triple(992882,"CAT288X",-7))) {
            jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (?,?,-1,'黃','검증',?)",id,yul,semitone)
        }
        for ((id,code,active) in listOf(Triple(instrumentId,"CAT288_ACTIVE",true),Triple(inactiveId,"CAT288_INACTIVE",false))) {
            jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active) VALUES (?,?,'검증 악기',992881,992879,50,?)",id,code,active)
        }
    }
    private fun body(path: String, staleCookie: Boolean = false): JsonNode {
        val response = get(path, staleCookie)
        assertEquals(200,response.statusCode(),response.body())
        val body = json.readTree(response.body())
        assertTrue(body.path("isSuccess").asBoolean())
        assertEquals(200,body.path("code").asInt())
        return body.path("result")
    }
    private fun assertError(path: String, status: Int, error: String) {
        val response = get(path)
        assertEquals(status,response.statusCode(),response.body())
        val body = json.readTree(response.body())
        assertFalse(body.path("isSuccess").asBoolean())
        assertEquals(error,body.path("result").path("errorCode").asString())
        assertFalse(response.body().contains("Stored jangdan"))
    }
    private fun get(path: String, staleCookie: Boolean = false): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10))
            .header("Accept","application/json")
        if (staleCookie) request.header("Cookie","access_token=invalid-test-cookie")
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString())
    }
    companion object {
        private const val instrumentId = 9007199254740993L
        private const val inactiveId = 992883L
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
