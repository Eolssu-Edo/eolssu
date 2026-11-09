package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SongInstrumentApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val instruments = (9924081L..9924086L).toList()
    private val songIds = (9924081L..9924087L).toList()

    @BeforeEach fun fixture() {
        // Four independent active instruments, an empty active instrument and an inactive instrument.
        for (id in instruments) jdbc.update("""INSERT INTO instruments
            (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active)
            SELECT ?,?,?,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,?
            FROM instruments WHERE id=1""", id, "FILTER_408_$id", "필터 검증 $id", id != instruments.last())
        for ((index, id) in songIds.withIndex()) {
            val instrumentId = if (index < 4) instruments[index] else instruments.first()
            val status = if (index == 4) "HIDDEN" else if (index == 5) "DRAFT" else "PUBLISHED"
            jdbc.update("""INSERT INTO songs
                (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status,deleted_at)
                SELECT ?,?,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,?,?
                FROM songs WHERE id=1""", id, instrumentId, status,
                if (index == 6) java.sql.Timestamp.valueOf("2026-11-03 00:00:00") else null)
        }
        jdbc.update("""INSERT INTO song_model_audios
            (song_id,score_version,object_key,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status)
            SELECT s.id,s.score_version,?,s.default_bpm,j.bpm_unit,53050,125,'audio/mp4',?,'PUBLISHED'
            FROM songs s JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=?""",
            "model-audio/drafts/${"b".repeat(64)}/cases/danso-001-p1/danso.m4a", "a".repeat(64), songIds.first())
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM song_model_audios WHERE song_id BETWEEN 9924081 AND 9924087")
        jdbc.update("DELETE FROM songs WHERE id BETWEEN 9924081 AND 9924087")
        jdbc.update("DELETE FROM instruments WHERE id BETWEEN 9924081 AND 9924086")
        client.close()
    }

    @Test fun `HTTP filter preserves publication ordering original score and model audio linkage`() {
        val original = json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1", String::class.java))
        for (id in instruments.take(4)) {
            val response = get("/api/songs?instrumentId=$id")
            assertEquals(200, response.statusCode(), response.body())
            val item = json.readTree(response.body()).path("result").path("items").single()
            assertEquals(id.toString(), item.path("instrumentId").asString())
            assertEquals(id.toString(), item.path("songId").asString())
            assertTrue(item.path("scoreVersion").isInt)
            assertFalse(item.has("score"))
            val detail = get("/api/songs/$id/score")
            assertEquals(200, detail.statusCode(), detail.body())
            assertEquals(original, json.readTree(detail.body()).path("result").path("score"))
        }
        val all = json.readTree(get("/api/songs").body()).path("result").path("items").toList()
            .map { it.path("songId").asString().toLong() }
        assertEquals(all.sorted(), all)
        assertEquals(songIds.take(4), all.filter { it in songIds })
        val audio = get("/api/songs/${songIds.first()}/model-audio")
        assertEquals(200, audio.statusCode(), audio.body())
        val media = json.readTree(audio.body()).path("result")
        assertEquals(songIds.first().toString(), media.path("songId").asString())
        assertEquals("/api/songs/${songIds.first()}/model-audio/play", media.path("audioUrl").asString())
        val empty = get("/api/songs?instrumentId=${instruments[4]}")
        assertEquals(200, empty.statusCode(), empty.body())
        assertEquals(0, json.readTree(empty.body()).path("result").path("items").size())
        for (id in listOf(instruments.last(), 9924099L, Long.MAX_VALUE)) {
            val missing = get("/api/songs?instrumentId=$id")
            assertEquals(404, missing.statusCode(), missing.body())
            assertEquals("RESOURCE_NOT_FOUND", json.readTree(missing.body()).path("result").path("errorCode").asString())
        }
    }

    @Test fun `danso catalog distinguishes duet parts and hides the reference edition`() {
        val inserted = jdbc.update("""INSERT INTO songs
            (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
            SELECT parts.song_id,instrument_id,jangdan_id,parts.song_title,difficulty,default_bpm,
                JSON_SET(score_json,'$.score.title',parts.song_title,'$.score.sourceId',parts.source_id),
                score_version,note_count,parts.song_id <> 16,'PUBLISHED'
            FROM songs
            CROSS JOIN (
                SELECT 15 AS song_id, '늴리리야' AS song_title, 'mr203-fixed-danso-011-p1' AS source_id
                UNION ALL SELECT 16, '늴리리야', 'mr203-fixed-danso-012-p1'
                UNION ALL SELECT 30, '어이 얼어자리', 'mr203-fixed-danso-026-p1'
                UNION ALL SELECT 31, '어이 얼어자리', 'mr203-fixed-danso-027-p1'
            ) AS parts
            WHERE songs.id=1""")
        try {
            assertEquals(4, inserted)
            jdbc.update("""INSERT INTO song_model_audios
                (song_id,score_version,object_key,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status)
                SELECT 16,score_version,?,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status
                FROM song_model_audios WHERE song_id=?""",
                "model-audio/drafts/${"c".repeat(64)}/cases/danso-016-p1/danso.m4a", songIds.first())

            fun result(path: String): JsonNode {
                val response = get(path)
                assertEquals(200, response.statusCode(), response.body())
                return json.readTree(response.body()).path("result")
            }
            val titles = mapOf(30L to "어이 얼어자리 · 단소2", 31L to "어이 얼어자리 · 단소1")
            val before = (titles.keys + 16L).associateWith { result("/api/songs/$it") }
            assertEquals(200, get("/api/songs/16/model-audio").statusCode())

            ResourceDatabasePopulator(
                ClassPathResource("db/migration/V31__resolve_danso_catalog_duplicates.sql"),
            ).execute(requireNotNull(jdbc.dataSource))

            val listed = result("/api/songs?instrumentId=1").path("items")
                .associate { it.path("songId").asLong() to it.path("title").asString() }
            assertEquals("늴리리야", listed[15])
            assertTrue(result("/api/songs/15").path("isScorable").asBoolean())
            assertFalse(16L in listed)
            for (path in listOf("/api/songs/16", "/api/songs/16/score", "/api/songs/16/model-audio")) {
                assertEquals(404, get(path).statusCode(), path)
            }
            assertEquals("HIDDEN", jdbc.queryForObject("SELECT status FROM songs WHERE id=16", String::class.java))
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM song_model_audios WHERE song_id=16", String::class.java))
            assertEquals(before.getValue(16).path("score"),
                json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=16", String::class.java)))

            for ((id, title) in titles) {
                val detail = result("/api/songs/$id")
                val score = result("/api/songs/$id/score").path("score")
                assertEquals(title, listed[id])
                assertEquals(title, detail.path("title").asString())
                assertEquals(title, score.path("score").path("title").asString())
                assertEquals(detail.path("score"), score)
                assertEquals(before.getValue(id).path("score").path("events"), score.path("events"))
                assertEquals(before.getValue(id).path("scoreVersion"), detail.path("scoreVersion"))
                assertTrue(detail.path("isScorable").asBoolean())
            }
        } finally {
            jdbc.update("DELETE FROM song_model_audios WHERE song_id=16")
            jdbc.update("DELETE FROM songs WHERE id IN (15, 16, 30, 31)")
        }
    }

    @Test fun `sogeum catalog distinguishes the original editions and duet parts`() {
        val titles = mapOf(
            104L to "북천이 맑다커늘 · 소금1",
            105L to "북천이 맑다커늘 · 소금2",
            109L to "취타 중 초장(고급)",
            110L to "취타 중 초장(중급)",
            111L to "취타 중 초장(초급)",
        )
        val sogeumId = jdbc.queryForObject("SELECT id FROM instruments WHERE code='SOGEUM'", Long::class.java)
        val wrongSourceId = "unrelated-score"
        val ids = "104, 105, 109, 110, 111"
        try {
            val inserted = jdbc.update("""INSERT INTO songs
                (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status)
                SELECT parts.song_id,?,jangdan_id,parts.title,difficulty,default_bpm,
                    JSON_SET(score_json,'$.score.title',parts.title,'$.score.sourceId',parts.source_id),
                    score_version,note_count,parts.song_id IN (105,110,111),'PUBLISHED'
                FROM songs
                CROSS JOIN (
                    SELECT 104 AS song_id, '북천이 맑다커늘' AS title, 'mr203-fixed-sogeum-016-p1' AS source_id
                    UNION ALL SELECT 105, '북천이 맑다커늘', 'mr203-fixed-sogeum-016-p2'
                    UNION ALL SELECT 109, '취타 중 초장', ?
                    UNION ALL SELECT 110, '취타 중 초장', 'mr203-fixed-sogeum-021-p1'
                    UNION ALL SELECT 111, '취타 중 초장', 'mr203-fixed-sogeum-022-p1'
                ) AS parts
                WHERE songs.id=1""", sogeumId, wrongSourceId)
            assertEquals(titles.size, inserted)
            val migration = ResourceDatabasePopulator(
                ClassPathResource("db/migration/V33__distinguish_sogeum_score_titles.sql"),
            )
            val dataSource = requireNotNull(jdbc.dataSource)
            fun payloads() = jdbc.queryForList("""SELECT id,score_version,is_scorable,default_bpm,note_count,
                JSON_REMOVE(score_json,'$.score.title') AS score_without_title
                FROM songs WHERE id IN ($ids) ORDER BY id""")

            val beforeFirstRun = payloads()
            migration.execute(dataSource)
            assertEquals(beforeFirstRun, payloads())
            assertEquals("취타 중 초장", jdbc.queryForObject("SELECT title FROM songs WHERE id=109", String::class.java))
            jdbc.update("""UPDATE songs
                SET score_json=JSON_SET(score_json,'$.score.sourceId','mr203-fixed-sogeum-020-p1') WHERE id=109""")

            val before = payloads()
            migration.execute(dataSource)
            migration.execute(dataSource)
            assertEquals(before, payloads())

            val list = get("/api/songs?instrumentId=$sogeumId")
            assertEquals(200, list.statusCode(), list.body())
            val listed = json.readTree(list.body()).path("result").path("items")
                .associate { it.path("songId").asLong() to it.path("title").asString() }
            for ((id, title) in titles) {
                assertEquals(title, listed[id], "song $id")
                val response = get("/api/songs/$id")
                assertEquals(200, response.statusCode(), response.body())
                val detail = json.readTree(response.body()).path("result")
                assertEquals(title, detail.path("title").asString())
                assertEquals(title, detail.path("score").path("score").path("title").asString())
                val score = get("/api/songs/$id/score")
                assertEquals(200, score.statusCode(), score.body())
                assertEquals(detail.path("score"), json.readTree(score.body()).path("result").path("score"))
            }
        } finally {
            jdbc.update("DELETE FROM songs WHERE id IN ($ids)")
        }
    }

    private fun get(path: String) = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        .header("Cookie", "access_token=expired-public-cookie").GET().build(), HttpResponse.BodyHandlers.ofString())

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
