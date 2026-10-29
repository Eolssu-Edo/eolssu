package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.AfterEach
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
class SongApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @AfterEach fun cleanup() { jdbc.update("DELETE FROM songs WHERE id BETWEEN 991256 AND 991258"); client.close() }

    @Test fun `public HTTP excludes hidden and deleted songs and preserves score identity`() {
        for ((id, state, deleted) in listOf(Triple(991256, "PUBLISHED", "NULL"), Triple(991257, "HIDDEN", "NULL"), Triple(991258, "PUBLISHED", "CURRENT_TIMESTAMP"))) {
            jdbc.update("""INSERT INTO songs (id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,status,deleted_at)
                SELECT ?,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,is_scorable,?,$deleted FROM songs WHERE id=1""",id,state)
        }
        val result = json.readTree(get("/api/songs").body()).path("result").path("items")
        val ids = mutableListOf<String>()
        result.forEach { ids.add(it.path("songId").asString()) }
        assertTrue("991256" in ids)
        assertFalse("991257" in ids); assertFalse("991258" in ids)
        assertEquals(ids.sortedBy { it.toLong() }, ids)
        val detail = get("/api/songs/991256")
        assertEquals(200, detail.statusCode(), detail.body())
        val score = json.readTree(detail.body()).path("result").path("score")
        assertEquals(json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1", String::class.java)),score)
        for (id in listOf(991257,991258,999999)) assertEquals(404,get("/api/songs/$id").statusCode())
    }

    private fun get(path: String) = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),HttpResponse.BodyHandlers.ofString())
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
