package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
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

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JangdanPlaybackApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newHttpClient()
    private var ownsSong = false
    private val source = json.readTree(requireNotNull(javaClass.getResourceAsStream("/jangdan-playback/6.json")))

    @BeforeEach fun prepare() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE id=6", Int::class.java))
        val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java,
            source.path("score").path("instrument").asString())
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,
            score_version,note_count,is_scorable,status,source) VALUES(6,?,NULL,?,1,99,?,1,?,FALSE,'PUBLISHED',?)""",
            instrument, source.path("score").path("title").asString(), json.writeValueAsString(source),
            source.path("events").count { it.path("kind").asString() == "NOTE" }, source.path("score").path("sourceId").asString())
        ownsSong = true
    }

    @AfterEach fun cleanup() {
        if (ownsSong) jdbc.update("DELETE FROM songs WHERE id=6")
        client.close()
    }

    @Test fun `public CAT04 survives MySQL JSON normalization and preserves FK BPM and source notes`() {
        val result = get()
        assertEquals(source, result.path("score"))
        assertEquals(99, result.path("defaultBpm").intValue())
        assertEquals("QUARTER", result.path("bpmUnit").asString())
        assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=6", Long::class.java))
        val playback = result.path("jangdanPlayback")
        assertEquals("2", playback.path("jangdanId").asString())
        assertEquals(0L, playback.path("startTick").longValue())
        assertEquals(24L, playback.path("cycleTicks").longValue())
        assertEquals("더러러러", playback.path("pattern").path("beats").get(4).asString())
    }

    @Test fun `CAT04 follows the current BPM and edited revision while the grid stays valid`() {
        jdbc.update("""UPDATE songs SET default_bpm=100,score_version=2,
            score_json=JSON_SET(score_json,'$.score.scoreVersion',2,'$.score.title','제목 수정','$.provenance.review','changed')
            WHERE id=6""")
        val edited = get()
        assertEquals(100, edited.path("defaultBpm").intValue())
        assertEquals(2, edited.path("scoreVersion").intValue())
        assertEquals(24L, edited.path("jangdanPlayback").path("cycleTicks").longValue())

        jdbc.update("UPDATE songs SET score_json=JSON_SET(score_json,'$.music.jangdan','') WHERE id=6")
        val unspecified = get()
        assertTrue(unspecified.path("jangdanPlayback").isNull)
        assertEquals(100, unspecified.path("defaultBpm").intValue())
        assertEquals("제목 수정", unspecified.path("score").path("score").path("title").asString())
    }

    @Test fun `CAT04 uses the current catalog pattern and rejects an invalid grouping`() {
        val pattern = jdbc.queryForObject("SELECT pattern FROM jangdans WHERE id=2", String::class.java)
        try {
            jdbc.update("UPDATE jangdans SET pattern=JSON_SET(pattern,'$.beats[0]','쿵') WHERE id=2")
            assertEquals("쿵", get().path("jangdanPlayback").path("pattern").path("beats").path(0).asString())
            jdbc.update("UPDATE jangdans SET pattern=JSON_SET(pattern,'$.group[0]',0) WHERE id=2")
            assertTrue(get().path("jangdanPlayback").isNull)
        } finally {
            jdbc.update("UPDATE jangdans SET pattern=? WHERE id=2", pattern)
        }
    }

    @Test fun `CAT04 returns the registered Jajin taryeong pattern for a valid score grid`() {
        jdbc.update("UPDATE songs SET score_json=JSON_SET(score_json,'$.music.jangdan','자진타령') WHERE id=6")
        val playback = get().path("jangdanPlayback")
        assertEquals("11", playback.path("jangdanId").asString())
        assertEquals(24L, playback.path("cycleTicks").longValue())
        assertEquals("덕", playback.path("pattern").path("beats").get(8).asString())
        assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=6", Long::class.java))
    }

    @Test fun `reviewed airplane revisions expose supplemental four beat accompaniment at 77 BPM without rewriting the score`() {
        val revisions = listOf(
            Triple("danso", "DANSO", "893185e48fc87c300d591f5e4f0774291f21da17828fdaea5f0f865705ae4b18"),
            Triple("sogeum", "SOGEUM", "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972"),
            Triple("sanjo-daegeum", "SANJO_DAEGEUM", "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972"),
        )
        val cells = (0..3).joinToString { """{"id":"c0-j$it","index":$it,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]}""" }
        for ((slug, code, hash) in revisions) {
            val original = json.readTree("""{"schemaVersion":1,
                "score":{"title":"비행기","sourceId":"airplane-beginner-$slug-v1","instrument":"$code","scoreVersion":1},
                "music":{"jangdan":"","bpmUnit":"QUARTER","ticksPerBeat":1,"ticksPerJeonggan":1},
                "provenance":{"jgbSha256":"$hash"},"columns":[{"index":0,"cells":[$cells]}],
                "events":[{"id":"c0-j0-r0-u0","kind":"NOTE","yul":"황","octave":0,"cellId":"c0-j0",
                    "startTick":0,"durationTick":4,"breathAfter":false,"sigimsae":[]}]}""")
            val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java, code)
            jdbc.update("UPDATE songs SET instrument_id=?,default_bpm=77,score_json=?,note_count=1,source=? WHERE id=6",
                instrument, original.toString(), "airplane-beginner-$slug-v1")

            val result = get()
            assertEquals(original, result.path("score"))
            assertEquals(1, result.path("scoreVersion").intValue())
            assertEquals(77, result.path("defaultBpm").intValue())
            assertTrue(result.path("jangdanPlayback").isNull)
            val playback = result.path("supplementalJangdanPlayback")
            assertEquals("12", playback.path("jangdanId").asString())
            assertEquals("4박 기본 반주", playback.path("name").asString())
            assertEquals(4L, playback.path("cycleTicks").longValue())
            assertEquals(listOf("덩", "덕", "궁", "덕"), playback.path("pattern").path("beats").toList().map { it.asString() })
            assertEquals(listOf(1, 1, 1, 1), playback.path("pattern").path("group").toList().map { it.intValue() })
            assertEquals(0L, playback.path("startTick").longValue())
            assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=6", Long::class.java))
            val detail = get("/api/songs/6")
            assertEquals("4박 기본 반주", detail.path("jangdanName").asString())
            assertTrue(detail.path("jangdan").isNull)
            assertEquals(original, detail.path("score"))
            val listed = get("/api/songs").path("items").single { it.path("songId").asString() == "6" }
            assertEquals("4박 기본 반주", listed.path("jangdanName").asString())
            assertTrue(listed.path("jangdan").isNull)
        }
    }

    @Test fun `Jeongak airplane second revision preview has supplemental four beat accompaniment without changing the stored score`() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE id=56", Int::class.java))
        val cells = (0..3).joinToString { """{"id":"c0-j$it","index":$it,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]}""" }
        val original = json.readTree("""{"schemaVersion":1,
            "score":{"title":"비행기","sourceId":"mr203-fixed-jeongak-daegeum-006-p1","instrument":"JEONGAK_DAEGEUM","scoreVersion":2},
            "music":{"jangdan":"","bpmUnit":"QUARTER","ticksPerBeat":6,"ticksPerJeonggan":6},
            "provenance":{"jgbSha256":"b8960383360b471a20214c05b2a37b3f511911aaa38f8db358e5e03a28a77e02"},
            "columns":[{"index":0,"cells":[$cells]}],"events":[{"id":"c0-j0-r0-u0","kind":"NOTE","yul":"황","octave":0,
                "cellId":"c0-j0","startTick":0,"durationTick":24,"breathAfter":false,"sigimsae":[]}]}""")
        val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code='JEONGAK_DAEGEUM'", Long::class.java)
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,
            score_version,note_count,is_scorable,status,source) VALUES(56,?,NULL,'비행기',1,77,?,2,1,FALSE,'PUBLISHED',?)""",
            instrument, original.toString(), "mr203-fixed-jeongak-daegeum-006-p1")
        try {
            val result = get("/api/songs/56/score")
            assertEquals(original, result.path("score"))
            assertEquals(2, result.path("scoreVersion").intValue())
            assertEquals(77, result.path("defaultBpm").intValue())
            assertTrue(result.path("jangdanPlayback").isNull)
            val playback = result.path("supplementalJangdanPlayback")
            assertEquals("12", playback.path("jangdanId").asString())
            assertEquals("4박 기본 반주", playback.path("name").asString())
            assertEquals(24L, playback.path("cycleTicks").longValue())
            assertEquals(listOf("덩", "덕", "궁", "덕"), playback.path("pattern").path("beats").toList().map { it.asString() })
            assertEquals(listOf(1, 1, 1, 1), playback.path("pattern").path("group").toList().map { it.intValue() })
            assertEquals("4박 기본 반주", get("/api/songs/56").path("jangdanName").asString())
            assertNull(jdbc.queryForObject("SELECT jangdan_id FROM songs WHERE id=56", Long::class.java))
        } finally {
            jdbc.update("DELETE FROM songs WHERE id=56")
        }
    }

    @Test fun `linked four beat edition resolves its reviewed opening rest through public APIs`() {
        val cells = listOf(
            """{"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"쉼","eventId":"c0-j0-r0-u0"}]}""",
            """{"id":"c0-j1","index":1,"units":[{"row":0,"col":0,"symbol":"쉼","eventId":"c0-j0-r0-u0"},{"row":1,"col":0,"symbol":"청중","eventId":"c0-j1-r1-u0"}]}""",
            """{"id":"c0-j2","index":2,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j1-r1-u0"}]}""",
            """{"id":"c0-j3","index":3,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j1-r1-u0"}]}""",
        ).joinToString()
        // Synthetic sustained note: checks source binding and grid, without redistributing the melody.
        val original = json.readTree("""{"schemaVersion":1,
            "score":{"title":"4박 연결 검증","sourceId":"user-blue-valentine-20261111-sogeum","instrument":"SOGEUM","scoreVersion":3},
            "music":{"hwangHz":311.13,"jangdan":"4박 기본 반주","bpmUnit":"QUARTER","ticksPerBeat":4,"ticksPerJeonggan":4},
            "provenance":{"jgbSha256":"971be444d01a313ce5bdfd3f03915ea9b01b314430743d5d4172ab1241e818a4"},
            "columns":[{"index":0,"cells":[$cells]}],"events":[
              {"id":"c0-j0-r0-u0","kind":"REST","yul":null,"octave":null,"cellId":"c0-j0","startTick":0,"durationTick":6,"breathAfter":false,"sigimsae":[]},
              {"id":"c0-j1-r1-u0","kind":"NOTE","yul":"중","octave":1,"cellId":"c0-j1","startTick":6,"durationTick":10,"breathAfter":false,"sigimsae":[]}]}""")
        val instrument = jdbc.queryForObject("SELECT id FROM instruments WHERE code='SOGEUM'", Long::class.java)
        jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,
            score_version,note_count,is_scorable,status,source) VALUES(135,?,12,'4박 연결 검증',3,160,?,3,1,FALSE,'PUBLISHED',?)""",
            instrument, original.toString(), "user-blue-valentine-20261111-sogeum")
        try {
            val result = get("/api/songs/135/score")
            assertEquals(original, result.path("score"))
            assertEquals(3, result.path("scoreVersion").intValue())
            val playback = result.path("jangdanPlayback")
            assertEquals("12", playback.path("jangdanId").asString())
            assertEquals(0L, playback.path("startTick").longValue())
            assertEquals(16L, playback.path("cycleTicks").longValue())
            assertEquals(listOf("덩", "덕", "궁", "덕"), playback.path("pattern").path("beats").toList().map { it.asString() })
            assertEquals("4박 기본 반주", get("/api/songs/135").path("jangdanName").asString())
            jdbc.update("UPDATE songs SET score_json=? WHERE id=135",
                original.toString().replace("user-blue-valentine-20261111-sogeum", "unreviewed-source"))
            assertTrue(get("/api/songs/135/score").path("jangdanPlayback").isNull)
        } finally {
            jdbc.update("DELETE FROM songs WHERE id=135")
        }
    }

    private fun get(path: String = "/api/songs/6/score"): JsonNode {
        val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Cookie", "access_token=malformed").GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result")
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
