package com.ssafy.eolssu.backend.feature.catalog

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
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
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReferenceAudioApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val json = JsonMapper.builder().build()
    private val firstId = "d".repeat(64)
    private val secondId = "e".repeat(64)

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM reference_audio_assets WHERE label_id LIKE 'CAT364.%'")
        client.close()
    }

    @Test fun `draft remains private and published exact label supports anonymous metadata and no-store redirect`() {
        seed(firstId,"CAT364.001")
        assertEquals(404, get("/$firstId").statusCode())
        assertEquals(404, get("/$firstId/play").statusCode())
        assertTrue(items("?datasetId=danso&technique=CAT364").isEmpty())
        jdbc.update("UPDATE reference_audio_assets SET status='PUBLISHED' WHERE asset_id=?",firstId)
        val rows=items("?datasetId=danso&technique=CAT364")
        assertEquals(listOf(firstId), rows.map { it.path("assetId").asString() })
        assertEquals("inferred",rows.single().path("review").asString())
        assertFalse(rows.single().path("eligibleForNoteScoring").asBoolean())
        val detail=get("/$firstId")
        assertEquals(200,detail.statusCode(),detail.body())
        assertEquals("KOGL-1",json.readTree(detail.body()).path("result").path("metadata").path("source").path("license").asString())
        val play=get("/$firstId/play")
        assertEquals(302,play.statusCode(),play.body())
        assertEquals("no-store",play.headers().firstValue("Cache-Control").orElseThrow())
        val location=URI(play.headers().firstValue("Location").orElseThrow())
        assertEquals("r2.example.test",location.host)
        assertTrue(location.path.endsWith("/$firstId/${"c".repeat(64)}.m4a"))
        assertTrue(location.query.contains("X-Amz-Expires=900"))
        assertEquals("",play.body())
    }

    @Test fun `database enforces provenance boundaries and query filters never select another dataset or draft`() {
        seed(secondId,"CAT364.002",yul="임",status="PUBLISHED")
        seed(firstId,"CAT364.001",status="PUBLISHED")
        assertEquals(listOf(firstId,secondId),items("?datasetId=danso&technique=CAT364").map { it.path("assetId").asString() })
        val filter="?datasetId=danso&technique=CAT364&category="+enc("기본음계")+"&register="+enc("평취")+"&yulmyeong="+enc("중")
        assertEquals(listOf(firstId),items(filter).map { it.path("assetId").asString() })
        assertTrue(items("?datasetId=sogeum&technique=CAT364").isEmpty())
        for (update in listOf("object_key='recordings/private.m4a'", "content_type='text/html'", "duration_ms=0",
            "review_status='rejected'", "eligible_for_note_scoring=1", "review_status='reviewed',eligible_for_note_scoring=2", "dataset_id='unknown'", "metadata_json=JSON_ARRAY()")) {
            assertFails { jdbc.update("UPDATE reference_audio_assets SET $update WHERE asset_id=?",firstId) }
        }
        assertFails { seed("f".repeat(64),"CAT364.001") }
        assertEquals(400,get("?datasetId=unknown").statusCode())
        assertEquals(400,get("?datasetId=danso&technique=").statusCode())
    }

    private fun seed(id: String,label: String,yul: String="중",status: String="DRAFT") {
        jdbc.update("""INSERT INTO reference_audio_assets(asset_id,dataset_id,label_id,category,technique,register_name,yulmyeong,display_label,
            object_key,content_type,sha256,byte_size,duration_ms,sample_rate_hz,channels,review_status,eligible_for_note_scoring,metadata_json,status)
            VALUES(?,'danso',?,'기본음계','CAT364','평취',?,'평취 중',?,'audio/mp4',?,12345,5043,44100,1,'inferred',false,?,?)""",
            id,label,yul,"reference-audio/v1/danso/$id/${"c".repeat(64)}.m4a","c".repeat(64),
            """{"source":{"license":"KOGL-1","attribution":"국립국악원"}}""",status)
    }
    private fun enc(value: String)=URLEncoder.encode(value,UTF_8)
    private fun get(suffix: String)=client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/reference-audios$suffix"))
        .header("Cookie","access_token=expired-public-cookie").GET().build(),HttpResponse.BodyHandlers.ofString())
    private fun items(suffix: String): List<tools.jackson.databind.JsonNode> {
        val response=get(suffix);assertEquals(200,response.statusCode(),response.body())
        return json.readTree(response.body()).path("result").path("items").toList()
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
            r.add("eolssu.reference-audio-storage.endpoint") { "https://r2.example.test" }
            r.add("eolssu.reference-audio-storage.bucket") { "reference-assets" }
            r.add("eolssu.reference-audio-storage.access-key-id") { "synthetic-key" }
            r.add("eolssu.reference-audio-storage.secret-access-key") { "synthetic-secret" }
        }
    }
}
