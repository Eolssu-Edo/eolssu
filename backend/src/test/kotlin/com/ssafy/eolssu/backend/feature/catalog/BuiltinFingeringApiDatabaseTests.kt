package com.ssafy.eolssu.backend.feature.catalog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText
import kotlin.test.*
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
class BuiltinFingeringApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()

    @Test fun `OpenAPI marks both fingering representations nullable and documents the hole states`() {
        HttpClient.newHttpClient().use { client ->
            val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            val schemas = json.readTree(response.body()).path("components").path("schemas")
            val item = schemas.path("FingeringItem").path("properties")
            assertTrue(item.path("imageUrl").path("nullable").asBoolean(), item.toString())
            assertTrue(item.path("pattern").path("nullable").asBoolean(), item.toString())
            val pattern = schemas.path("FingeringPattern").path("properties")
            assertEquals(setOf("LOW","MID","HIGH"), pattern.path("blow").path("enum").toList().map { it.asString() }.toSet())
            assertEquals(setOf("CLOSED","OPEN","HALF","UNKNOWN"),
                pattern.path("holes").path("items").path("enum").toList().map { it.asString() }.toSet())
        }
    }

    @Test fun `all 92 builtin rows and pitch filters match the FE tables including variant glyphs`() {
        val source = BuiltinFingeringSource()
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            for (instrument in source.instruments) {
                val id = jdbc.queryForObject("SELECT id FROM instruments WHERE code=?", Long::class.java, instrument.code)!!
                val pitches = body(client, "/api/pitches?instrumentId=$id").path("items").toList()
                    .associateBy { it.path("yul").asString() to it.path("octave").asInt() }
                val result = body(client, "/api/instruments/$id/fingerings")
                assertTrue(result.path("structureImageUrl").isNull)
                val items = result.path("items").toList()
                assertEquals(instrument.rows.size, items.size, instrument.code)
                assertEquals(items.map { it.path("fingeringId").asString().toLong() }.sorted(),
                    items.map { it.path("fingeringId").asString().toLong() })
                for (row in instrument.rows) {
                    val pitch = assertNotNull(pitches[source.identity(row.hanja)], row.hanja)
                    val item = items.single { it.path("pitchId") == pitch.path("pitchId") }
                    assertEquals(id.toString(), item.path("instrumentId").asString())
                    assertTrue(item.path("imageUrl").isNull)
                    assertTrue(item.path("description").isNull)
                    val pattern = item.path("pattern")
                    assertEquals(row.blow, pattern.path("blow").asString())
                    assertEquals(row.hanja, pattern.path("hanja").asString())
                    assertEquals(row.name, pattern.path("name").asString())
                    assertEquals(row.reading, pattern.path("reading").asString())
                    assertEquals(row.holes.map { source.holes.getValue(it) }, pattern.path("holes").toList().map { it.asString() })
                    assertEquals(row.basic, pattern.path("basic").asBoolean())
                    assertEquals(instrument.source, pattern.path("source").asString())
                    assertEquals(instrument.note, pattern.path("instrumentNote").asString())
                    assertEquals(instrument.hwang, pattern.path("nominalHwangPitchClass").asInt())
                    assertEquals("BUILTIN_REFERENCE", pattern.path("verificationStatus").asString())
                    val filtered = body(client, "/api/instruments/$id/fingerings?pitchId=${pitch.path("pitchId").asString()}")
                    assertEquals(listOf(item), filtered.path("items").toList())
                }
                assertTrue(body(client, "/api/instruments/$id/fingerings?pitchId=9223372036854775807").path("items").isEmpty)
            }
        }
    }

    private fun body(client: HttpClient, path: String): JsonNode {
        val response = client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .timeout(Duration.ofSeconds(10)).header("Cookie", "access_token=invalid-test-cookie")
            .GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("result")
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}

/** Expected values come from the actual FE sources, independently of the SQL seed. */
internal class BuiltinFingeringSource {
    private val directory = Path.of("../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering")
    private val tables = directory.resolve("FingeringTables.kt").readText()
    private val definitions = directory.resolve("Fingering.kt").readText()
    private val notation = directory.resolve("YulNotation.kt").readText()
    val holes = mapOf('1' to "CLOSED", '0' to "OPEN", 'h' to "HALF", '?' to "UNKNOWN")
    private val aliases = Regex(""""([^"]+)" to "([^"]+)"""").findAll(notation)
        .associate { it.groupValues[1] to it.groupValues[2] }
    private val octaves = Regex(""""([^"]+)"\.forEach \{ put\(it\.toString\(\), (-?\d+)\) }""").findAll(notation)
        .flatMap { match -> match.groupValues[1].map { it.toString() to match.groupValues[2].toInt() } }.toMap()
    private val bases = "黃大太夾姑仲蕤林夷南無應".map { it.toString() }.zip("황대태협고중유임이남무응".map { it.toString() }).toMap()

    fun identity(hanja: String) = bases.getValue(aliases[hanja] ?: hanja) to (octaves[hanja] ?: 0)

    val instruments = listOf(
        instrument("Danso", "DANSO", "Danso", 12),
        instrument("Sogeum", "SOGEUM", "Sogeum", 20),
        instrument("DaegeumJeongak", "JEONGAK_DAEGEUM", "Daegeum", 30),
        instrument("DaegeumSanjo", "SANJO_DAEGEUM", "Daegeum", 30),
    )

    private fun instrument(name: String, code: String, table: String, count: Int): SourceInstrument {
        val definition = Regex("""(?s)\b$name\((.*?)\n    \),""").find(definitions)!!.groupValues[1]
        fun text(field: String) = Regex("""$field = "([^"]+)"""").find(definition)!!.groupValues[1]
        val body = tables.substringAfter("internal val ${table}Table:").substringBefore("\n)\n")
        val rows = Regex("""(?s)Blow\.(Low|Mid|High) to listOf\((.*?)\n    \),""").findAll(body).flatMap { group ->
            Regex("""n\("([^"]+)", "([^"]+)", "([^"]+)", "([10h?]+)", ([01])\)""").findAll(group.groupValues[2]).map {
                SourceRow(group.groupValues[1].uppercase(), it.groupValues[1], it.groupValues[2], it.groupValues[3],
                    it.groupValues[4], it.groupValues[5] == "1")
            }
        }.toList()
        assertEquals(count, rows.size, "$name parser must read the entire source")
        assertEquals(count, rows.map { identity(it.hanja) }.distinct().size)
        assertTrue(rows.all { it.holes.length == if (code == "DANSO") 5 else 6 })
        return SourceInstrument(code, text("source"), text("note"), Regex("""hwang = (\d+)""").find(definition)!!.groupValues[1].toInt(), rows)
    }
}

internal data class SourceInstrument(val code: String, val source: String, val note: String, val hwang: Int, val rows: List<SourceRow>)
internal data class SourceRow(val blow: String, val hanja: String, val name: String, val reading: String, val holes: String, val basic: Boolean)
