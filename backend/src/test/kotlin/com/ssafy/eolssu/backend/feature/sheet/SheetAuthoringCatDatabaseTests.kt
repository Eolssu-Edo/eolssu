package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.sheet.application.SheetAudios
import com.ssafy.eolssu.backend.feature.sheet.application.SheetExpressions
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.HttpPlaybackClient
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.PlaybackClient
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.sheetAudioSha256
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
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

/** Authenticated save/audio APIs and actual CAT HTTP. Only object storage is replaced. */
@Tag("database")
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "TEST_PLAYBACK_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SheetAuthoringCatDatabaseTests.ObjectStorageConfig::class)
class SheetAuthoringCatDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var worker: SheetAudios
    @Autowired lateinit var playback: PlaybackClient
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newHttpClient()

    @BeforeEach fun setup() {
        cleanup()
        uploaded.clear()
        val score = checkNotNull(javaClass.getResource("/tempo-authoring/supported-first-rest-id12.json")).readText()
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(994773,'실제 생성 검사','ACTIVE',1)")
        jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,score_version,score_json,note_count,idempotency_key,request_hash)
            VALUES(994773,994773,'실제 생성 검사','JEONGGANBO','MANUAL','EVALUABLE',12,60,'QUARTER',2,1,?,7,'integrated773',REPEAT('0',64))""", score)
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM sheet_audio_objects WHERE sheet_id=994773")
        jdbc.update("DELETE FROM user_sheets WHERE user_id=994773")
        jdbc.update("DELETE FROM users WHERE id=994773")
    }

    @Test fun `saved supported notes first rest ID12 and numeric tempo reach actual CAT and versioned metadata`() {
        assertIs<HttpPlaybackClient>(playback, "이 검증에는 생성기 대역을 쓰지 않는다")
        val source = storedScore()
        val digest = SheetExpressions(json).hash(json.writeValueAsString(source))
        val expressions = json.readTree("""{"schema":"common-score-expressions/v1","sourceSha256":"$digest","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$digest","tempos":[{"gak":2,"cell":1,"bpm":30}]}}""")
        val saved = call("PUT", "/score", json.writeValueAsString(mapOf(
            "baseScoreVersion" to "1", "title" to "실제 생성 검사", "bpm" to 60, "jangdanId" to "12",
            "score" to source, "expressions" to expressions)))
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals("2", json.readTree(saved.body()).path("result").path("scoreVersion").asString())
        val current = storedScore()
        val sourceHash = SheetExpressions(json).hash(json.writeValueAsString(current))

        val accepted = call("POST", "/audio", """{"instrumentId":"1","withJangdan":true}""")
        assertEquals(202, accepted.statusCode(), accepted.body())
        val snapshot = json.readTree(jdbc.queryForObject("SELECT request_json FROM sheet_audios WHERE sheet_id=994773", String::class.java)!!)
        assertEquals(2, snapshot.path("jangdan").path("patternCellEighths").asInt())
        assertEquals(listOf("덩", "덕", "궁", "덕"), snapshot.path("jangdan").path("pattern").path("beats").toList().map { it.asString() })
        assertEquals(30.0, snapshot.path("expressions").path("performancePlan").path("tempos")[0].path("bpm").asDouble())
        assertEquals(sourceHash, snapshot.path("expressions").path("sourceSha256").asString())

        worker.runOne()
        val response = call("GET", "/audio", null)
        assertEquals(200, response.statusCode(), response.body())
        val result = json.readTree(response.body()).path("result")
        assertEquals("DONE", result.path("status").asString(), response.body())
        assertEquals("2", result.path("scoreVersion").asString())
        assertTrue(result.path("withJangdan").asBoolean())
        assertEquals(60, result.path("bpm").asInt())
        val file = result.path("audio")
        assertEquals(12550, file.path("durationMs").asInt())
        assertEquals("실제 생성 검사.m4a", file.path("fileName").asString())
        assertEquals(1, uploaded.size)
        val (key, bytes) = uploaded.entries.single()
        assertTrue(key.startsWith("sheet-audio/994773/994773/"))
        assertTrue(key.endsWith("/audio.m4a"))
        assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII))
        assertEquals(bytes.sheetAudioSha256(), file.path("sha256").asString())
        assertTrue(result.path("midi").isNull)
        val timeline = file.path("performance").path("timeline")
        assertEquals(sourceHash, timeline.path("sourceSha256").asString())
        assertEquals(bytes.sheetAudioSha256(), timeline.path("audioSha256").asString())
        assertEquals(2, timeline.path("scoreVersion").asInt())
        assertEquals(12000.0, timeline.path("durationMs").asDouble())
        assertEquals(listOf(0.0, 1000.0, 1500.0, 2000.0, 3000.0, 4000.0, 6000.0, 8000.0, 10000.0),
            timeline.path("segments").toList().map { it.path("startMs").asDouble() })
        val firstNote = current.path("events").first { it.path("kind").asString() == "NOTE" }.path("id").asString()
        assertEquals(1500.0, timeline.path("segments").first { it.path("eventId").asString() == firstNote }.path("startMs").asDouble())
        val metadata = json.readTree(jdbc.queryForObject("SELECT result_json FROM sheet_audios WHERE sheet_id=994773", String::class.java)!!)
        assertEquals(key, metadata.path("audio").path("key").asString())
        assertEquals(bytes.size, metadata.path("audio").path("byteSize").asInt())
        assertEquals(bytes.sheetAudioSha256(), metadata.path("audio").path("sha256").asString())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_objects WHERE sheet_id=994773 AND state='CURRENT'", Int::class.java))
    }

    private fun storedScore(): JsonNode = json.readTree(jdbc.queryForObject("SELECT score_json FROM user_sheets WHERE id=994773", String::class.java)!!)
    private fun call(method: String, suffix: String, body: String?): HttpResponse<String> {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().subject("994773")
            .expirationTime(Date.from(Instant.now().plusSeconds(120))).build())
        token.sign(MACSigner(SECRET.toByteArray()))
        return client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/users/me/sheets/994773$suffix"))
            .header("Cookie", "access_token=${token.serialize()}").header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ObjectStorageConfig {
        @Bean @Primary fun verifiedAudioStorage() = object : SheetSourceStorage {
            override val available = true
            override fun put(key: String, file: Path, contentType: String) {
                check(contentType == "audio/mp4")
                uploaded[key] = Files.readAllBytes(file)
            }
            override fun delete(key: String) { uploaded.remove(key) }
            override fun readUrl(key: String, ttl: Duration) = "https://fixture.invalid/$key"
        }
    }

    companion object {
        private const val SECRET = "integrated773-local-http-signing-key-only"
        private val uploaded = ConcurrentHashMap<String, ByteArray>()
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.hikari.maximum-pool-size") { "2" }
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
            registry.add("eolssu.sheet-audio.base-url") { requireNotNull(System.getenv("TEST_PLAYBACK_URL")) }
        }
    }
}
