package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.sheet.application.SheetExpressions
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
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

/** Real authenticated HTTP, MySQL JSON round trip and the configured renderer's actual preflight. */
@Tag("database")
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "TEST_PLAYBACK_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SheetTempoApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newHttpClient()

    @BeforeEach fun setup() {
        cleanup()
        val score = checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(993773,'빠르기 검사','ACTIVE',1)")
        jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,bpm,bpm_unit,column_count,score_version,score_json,note_count,idempotency_key,request_hash)
            VALUES(993773,993773,'빠르기 검사','JEONGGANBO','MANUAL','EVALUABLE',100,'QUARTER',1,1,?,4,'tempo773',REPEAT('0',64))""", score)
    }

    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id=993773")
        jdbc.update("DELETE FROM users WHERE id=993773")
    }

    @Test fun `save preserves numeric profile through database reordering and renderer rejects short notes before mutation`() {
        val source = storedScore()
        val hash = SheetExpressions(json).hash(json.writeValueAsString(source))
        val expressions = json.readTree("""{"schema":"common-score-expressions/v1","sourceSha256":"$hash","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$hash","tempos":[{"gak":1,"cell":1,"bpm":100},{"gak":1,"cell":3,"bpm":80}]}}""")
        val first = save(1, 120, source, expressions)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(listOf(120.0, 96.0), storedTempos())
        val query = call("GET", null)
        assertEquals(200, query.statusCode(), query.body())
        assertTrue(json.readTree(query.body()).path("result").path("expressions").isObject)
        // Old clients omit expressions. They must retain and scale the persisted companion.
        val second = save(2, 144, storedScore(), null)
        assertEquals(200, second.statusCode(), second.body())
        assertEquals(listOf(144.0, 115.2), storedTempos())
        val fast = save(3, 480, storedScore(), null)
        assertEquals(200, fast.statusCode(), fast.body())
        assertEquals(listOf(480.0, 384.0), storedTempos())
        assertEquals(200, call("GET", null).statusCode())
        val rejected = save(4, 2_000_000, storedScore(), null)
        assertEquals(400, rejected.statusCode(), rejected.body())
        val error = json.readTree(rejected.body()).path("result")
        assertEquals("TEMPO_UNRENDERABLE", error.path("errorCode").asString())
        assertEquals("PCM_NOTE_TOO_SHORT", error.path("details").path("reason").asString())
        val reshaped = storedScore()
        (reshaped.path("columns")[0].path("cells") as tools.jackson.databind.node.ArrayNode).remove(3)
        (reshaped.path("events") as tools.jackson.databind.node.ArrayNode).remove(3)
        val moved = save(4, 480, reshaped, null)
        assertEquals(400, moved.statusCode(), moved.body())
        assertEquals("PLAN_POSITIONS_CHANGED", json.readTree(moved.body()).path("result").path("details").path("reason").asString())
        assertEquals(4, jdbc.queryForObject("SELECT score_version FROM user_sheets WHERE id=993773", Int::class.java))
    }

    @Test fun `draft-only companion survives omitted and null requests before confirmation`() {
        val score = storedScore()
        val plan = companion(score, 80)
        assertEquals(200, saveDraft(0, score, plan).statusCode())
        for ((revision, explicitNull) in listOf(1 to true, 2 to false)) {
            val response = saveDraft(revision, score, null, explicitNull)
            assertEquals(200, response.statusCode(), response.body())
            val query = json.readTree(call("GET", null).body()).path("result")
            assertEquals(plan, query.path("draft").path("expressions"))
            assertEquals(revision + 1, query.path("draft").path("draftRevision").asInt())
            assertTrue(query.path("expressions").isNull)
        }
        val saved = save(1, 120, score, null)
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(listOf(96.0), storedTempos())
        assertSavedCompanionBinding()
    }

    @Test fun `confirmation uses the newer draft companion and still rejects changed positions`() {
        val score = storedScore()
        jdbc.update("UPDATE user_sheets SET expressions_json=? WHERE id=993773", companion(score, 60).toString())
        val plan = companion(score, 80)
        assertEquals(200, saveDraft(0, score, plan).statusCode())
        val moved = score.deepCopy()
        (moved.path("columns")[0].path("cells") as tools.jackson.databind.node.ArrayNode).remove(3)
        (moved.path("events") as tools.jackson.databind.node.ArrayNode).remove(3)
        val rejected = save(1, 120, moved, null)
        assertEquals(400, rejected.statusCode(), rejected.body())
        assertEquals("PLAN_POSITIONS_CHANGED", json.readTree(rejected.body()).path("result").path("details").path("reason").asString())
        val unchanged = json.readTree(call("GET", null).body()).path("result")
        assertEquals("1", unchanged.path("scoreVersion").asString())
        assertEquals(plan, unchanged.path("draft").path("expressions"))
        assertEquals(listOf(60.0), storedTempos())
        val saved = save(1, 120, score, null)
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(listOf(96.0), storedTempos())
        assertSavedCompanionBinding()
    }

    private fun companion(score: JsonNode, bpm: Int): JsonNode {
        val hash = SheetExpressions(json).hash(score.toString())
        return json.readTree("""{"schema":"common-score-expressions/v1","sourceSha256":"$hash","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$hash","tempos":[{"gak":1,"cell":3,"bpm":$bpm}]}}""")
    }

    private fun assertSavedCompanionBinding() {
        val result = json.readTree(call("GET", null).body()).path("result")
        val hash = SheetExpressions(json).hash(result.path("scoreData").toString())
        assertEquals(hash, result.path("expressions").path("sourceSha256").asString())
        assertEquals(hash, result.path("expressions").path("performancePlan").path("input_sha256").asString())
        assertTrue(result.path("draft").isNull)
    }

    private fun saveDraft(revision: Int, score: JsonNode, expressions: JsonNode?, explicitNull: Boolean = false): HttpResponse<String> {
        val body = json.createObjectNode().put("baseScoreVersion", "1").put("draftRevision", revision).put("bpm", 120)
        body.set("score", score)
        if (expressions != null) body.set("expressions", expressions) else if (explicitNull) body.putNull("expressions")
        return call("PUT", body, "/draft")
    }

    private fun storedScore() = json.readTree(jdbc.queryForObject("SELECT score_json FROM user_sheets WHERE id=993773", String::class.java)!!)
    private fun storedTempos() = json.readTree(jdbc.queryForObject("SELECT expressions_json FROM user_sheets WHERE id=993773", String::class.java)!!)
        .path("performancePlan").path("tempos").toList().map { it.path("bpm").asDouble() }
    private fun save(version: Int, bpm: Int, score: JsonNode, expressions: JsonNode?): HttpResponse<String> {
        val body = json.createObjectNode().put("baseScoreVersion", version.toString()).put("bpm", bpm)
        body.set("score", score)
        expressions?.let { body.set("expressions", it) }
        return call("PUT", body)
    }
    private fun call(method: String, body: ObjectNode?, suffix: String = ""): HttpResponse<String> {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().subject("993773")
            .expirationTime(Date.from(Instant.now().plusSeconds(60))).build())
        token.sign(MACSigner(SECRET.toByteArray()))
        return client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/users/me/sheets/993773/score$suffix"))
            .header("Cookie", "access_token=${token.serialize()}").header("Content-Type", "application/json")
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) } ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }
    companion object {
        private const val SECRET = "tempo773-local-http-signing-key-only"
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
