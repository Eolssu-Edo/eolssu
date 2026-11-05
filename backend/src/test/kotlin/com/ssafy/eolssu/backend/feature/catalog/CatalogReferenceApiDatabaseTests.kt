package com.ssafy.eolssu.backend.feature.catalog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
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

    @Test fun `structured fingering preserves unknown and half holes and rejects corrupted patterns without leaking data`() {
        seedInstrumentRange()
        val pattern = """{"blow":"MID","hanja":"仲","name":"중려","reading":"중","holes":["UNKNOWN","HALF","CLOSED","OPEN","OPEN"],"basic":false,"source":"확인 전 참고 자료","instrumentNote":"구멍 순서 보존","nominalHwangPitchClass":5,"verificationStatus":"BUILTIN_REFERENCE"}"""
        jdbc.update("INSERT INTO fingering_charts (id,instrument_id,pitch_id,pattern_json) VALUES (992884,?,992880,?)", instrumentId, pattern)
        val stored = body("/api/instruments/$instrumentId/fingerings").path("items")[0]
        assertTrue(stored.path("imageUrl").isNull)
        assertEquals(listOf("UNKNOWN","HALF","CLOSED","OPEN","OPEN"), stored.path("pattern").path("holes").toList().map { it.asString() })
        assertFalse(stored.path("pattern").path("basic").asBoolean())
        for (bad in listOf(
            pattern.replace("UNKNOWN","MAYBE"),
            pattern.replace("\"basic\":false","\"basic\":\"false\""),
            pattern.replace("\"nominalHwangPitchClass\":5","\"nominalHwangPitchClass\":5.5"),
            pattern.replace("\"reading\":\"중\"","\"reading\":\"\""),
        )) {
            jdbc.update("UPDATE fingering_charts SET pattern_json=? WHERE id=992884", bad)
            assertError("/api/instruments/$instrumentId/fingerings",500,"INTERNAL_SERVER_ERROR")
            assertFalse(get("/api/instruments/$instrumentId/fingerings").body().contains("확인 전"))
        }
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
            assertEquals("/api/jangdans/$id/audio", detail.path("soundUrl").asString())
            val audio = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/jangdans/$id/audio"))
                .header("Range", "bytes=0-63").header("Cookie", "access_token=stale").build(),
                HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(302, audio.statusCode())
            assertEquals("no-store", audio.headers().firstValue("Cache-Control").orElse(""))
            val location = URI(audio.headers().firstValue("Location").orElseThrow())
            assertEquals("https", location.scheme)
            assertTrue(location.path.substringAfter("/jangdan-preview/v1/").matches(Regex("$id/[0-9a-f]{64}\\.m4a")))
            assertTrue(location.query.contains("X-Amz-Expires=900"))
        }
        assertError("/api/jangdans/9223372036854775807",404,"RESOURCE_NOT_FOUND")
    }

    @Test fun `registered practice M4As redirect without requiring a valid session`() {
        val mapping = Properties().apply {
            requireNotNull(CatalogReferenceApiDatabaseTests::class.java.getResourceAsStream("/catalog/jangdan-practice-sha256.properties"))
                .use { load(it) }
        }
        assertEquals(11, mapping.size)
        for (sample in mapping.stringPropertyNames()) {
            val hash = mapping.getProperty(sample)
            val result = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/jangdan-samples/$sample/$hash"))
                .header("Cookie", "access_token=stale").build(), HttpResponse.BodyHandlers.discarding())
            assertEquals(302, result.statusCode())
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
            val location = URI(result.headers().firstValue("Location").orElseThrow())
            assertEquals("https", location.scheme)
            assertTrue(location.path.endsWith("/jangdan-practice/v1/$sample/$hash.m4a"))
            assertTrue(location.query.contains("X-Amz-Expires=900"))
        }
        assertError("/api/jangdan-samples/deong/${"0".repeat(64)}", 404, "RESOURCE_NOT_FOUND")
    }

    @Test fun `sourced additional rhythms are public without changing the original catalog or songs`() {
        val items = body("/api/jangdans", staleCookie = true).path("items").toList()
        assertEquals(listOf("1", "2", "3", "4", "5"), items.take(5).map { it.path("jangdanId").asString() })
        assertEquals(listOf("세마치", "굿거리", "중모리", "자진모리", "중중모리"), items.take(5).map { it.path("name").asString() })
        val expected = listOf(
            Triple("4", 96, listOf("떵", "", "덕", "쿵", "덕", "", "떵", "", "덕", "쿵", "덕", "")),
            Triple("5", 80, listOf("덩", "", "따", "궁", "따", "따", "궁", "궁", "따", "궁", "", "궁")),
        )
        for ((id, bpm, beats) in expected) {
            val summary = items.single { it.path("jangdanId").asString() == id }
            val detail = body("/api/jangdans/$id", staleCookie = true)
            assertEquals(bpm, detail.path("defaultBpm").asInt())
            assertEquals("DOTTED_QUARTER", detail.path("bpmUnit").asString())
            assertEquals(beats, detail.path("pattern").path("beats").toList().map { it.asString() })
            assertEquals(listOf(3, 3, 3, 3), detail.path("pattern").path("group").toList().map { it.asInt() })
            assertEquals(12, summary.path("beatCount").asInt())
            assertEquals(4, summary.path("groupCount").asInt())
            assertEquals(summary.path("defaultBpm"), detail.path("defaultBpm"))
            assertEquals(summary.path("bpmUnit"), detail.path("bpmUnit"))
            assertTrue(detail.path("source").asString().contains("https://"))
            assertEquals("/api/jangdans/$id/audio", detail.path("soundUrl").asString())
        }
        assertEquals(listOf("1 세마치 96 DOTTED_QUARTER", "2 굿거리 66 DOTTED_QUARTER", "3 중모리 88 QUARTER"),
            jdbc.queryForList("SELECT CONCAT(id,' ',name,' ',default_bpm,' ',bpm_unit) FROM jangdans WHERE id<=3 ORDER BY id", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE jangdan_id IN (4,5)", Int::class.java))
    }

    @Test fun `remaining rhythms preserve source syllables groups and chosen quarter tempo without linking songs`() {
        data class Expected(val id: String, val name: String, val bpm: Int, val beats: List<String>, val groups: List<Int>)
        val expected = listOf(
            Expected("6", "엇모리", 90, listOf("덩", "", "덕", "쿵", "", "쿵", "", "덕", "쿵", ""), listOf(3,2,3,2)),
            Expected("7", "단모리", 120, listOf("덩", "", "따", "따", "궁", "따", "궁", ""), listOf(2,2,2,2)),
            Expected("8", "타령", 48, listOf("덩", "", "", "기닥", "", "다", "덩", "", "기닥", "", "", "다"), listOf(3,3,3,3)),
            Expected("9", "계면가락도드리", 48, listOf("덩", "", "", "기닥", "", "다", "덩", "", "기닥", "", "", "다"), listOf(3,3,3,3)),
            Expected("10", "세령산", 63, listOf("덩", "", "덩", "더러러러", "", "궁", "", "기닥", "", "다"), listOf(3,2,2,3)),
        )
        val items = body("/api/jangdans", staleCookie = true).path("items").toList()
        assertEquals((1..10).map(Int::toString), items.map { it.path("jangdanId").asString() })
        for (e in expected) {
            val summary = items.single { it.path("jangdanId").asString() == e.id }
            val detail = body("/api/jangdans/${e.id}", staleCookie = true)
            assertEquals(e.name, detail.path("name").asString())
            assertEquals(e.bpm, detail.path("defaultBpm").asInt())
            assertEquals("QUARTER", detail.path("bpmUnit").asString())
            assertEquals(e.beats, detail.path("pattern").path("beats").toList().map { it.asString() })
            assertEquals(e.groups, detail.path("pattern").path("group").toList().map { it.asInt() })
            assertEquals(e.beats.size, summary.path("beatCount").asInt())
            assertEquals(e.groups.size, summary.path("groupCount").asInt())
            assertEquals(summary.path("defaultBpm"), detail.path("defaultBpm"))
            assertEquals(summary.path("bpmUnit"), detail.path("bpmUnit"))
            assertTrue(detail.path("source").asString().contains("https://"))
            assertEquals("/api/jangdans/${e.id}/audio", detail.path("soundUrl").asString())
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE jangdan_id>=6", Int::class.java))
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

    @Test fun `registered frontend symbols expose original descriptions provenance and public images`() {
        val items = body("/api/notation-symbols", staleCookie = true).path("items").toList()
        assertEquals(49, items.size)
        assertEquals(items.size, items.map { it.path("symbol").asString() }.distinct().size)
        val hold = items.single { it.path("symbol").asString() == "ㅡ" }
        assertEquals("이어요", hold.path("description").asString())
        val inferred = items.single { it.path("symbol").asString() == "노라느니" }
        assertEquals("INFERRED", inferred.path("descriptionStatus").asString())
        assertTrue(inferred.path("source").asString().contains("추정"))
        for (item in items) {
            val path = "/api/notation-symbols/${item.path("symbolId").asString()}/image"
            assertEquals(path, item.path("imageUrl").asString())
            val image = get(path, staleCookie = true)
            assertEquals(200, image.statusCode(), image.body())
            assertTrue(image.headers().firstValue("Content-Type").orElse("").startsWith("image/svg+xml"))
            assertTrue(image.body().contains("<svg"))
            if (item.path("imageLicense").asString() == "MIT") assertTrue(image.body().contains("Copyright (c) 2026 Danbinaerin Han"))
        }
        assertError("/api/notation-symbols/9223372036854775807/image", 404, "RESOURCE_NOT_FOUND")
        assertError("/api/notation-symbols/01/image", 400, "INVALID_REQUEST")
    }

    @Test fun `seed replay preserves custom content and distinct width aliases without claiming bundled provenance`() {
        val hold = jdbc.queryForMap("SELECT id,description FROM notation_symbols WHERE symbol='ㅡ'")
        val holdId = (hold["id"] as Number).toLong()
        try {
            jdbc.update("UPDATE notation_symbols SET description='custom teaching content' WHERE id=?", holdId)
            jdbc.update("INSERT INTO notation_symbols (id,symbol,name,description) VALUES (992886,'－','custom fullwidth','keep fullwidth'),(992887,'-','custom ASCII','keep ASCII')")
            val before = jdbc.queryForList("SELECT * FROM notation_symbols ORDER BY id")
            org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                org.springframework.core.io.ClassPathResource("db/migration/V19__seed_notation_symbols.sql")
            ).execute(jdbc.dataSource!!)
            assertEquals(before, jdbc.queryForList("SELECT * FROM notation_symbols ORDER BY id"))
            val changed = body("/api/notation-symbols").path("items").toList().single { it.path("symbolId").asString() == holdId.toString() }
            assertEquals("custom teaching content", changed.path("description").asString())
            assertTrue(changed.path("source").isNull)
            assertTrue(changed.path("descriptionStatus").isNull)
            assertTrue(changed.path("imageUrl").isNull)
            assertError("/api/notation-symbols/$holdId/image", 404, "RESOURCE_NOT_FOUND")
        } finally {
            jdbc.update("UPDATE notation_symbols SET description=? WHERE id=?", hold["description"], holdId)
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
            registry.add("eolssu.recording-storage.type") { "s3" }
            registry.add("eolssu.recording-storage.endpoint") { "https://r2.example.test" }
            registry.add("eolssu.recording-storage.bucket") { "test-bucket" }
            registry.add("eolssu.recording-storage.access-key-id") { "fixture-access-key" }
            registry.add("eolssu.recording-storage.secret-access-key") { "fixture-secret-key" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
