package com.ssafy.eolssu.backend.feature.sheet.application

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class SheetExpressionsTests {
    private val json = JsonMapper.builder().build()
    private val expressions = SheetExpressions(json)
    private val original = """{"score":{"instrument":"DANSO","scoreVersion":1}}"""
    private val updated = """{"score":{"instrument":"DANSO","scoreVersion":2}}"""

    @Test fun `base BPM scales every numeric cue once and rebinds both hashes`() {
        val hash = expressions.hash(original)
        val source = """{"schema":"common-score-expressions/v1","sourceSha256":"$hash","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$hash","tempos":[{"gak":1,"cell":1,"bpm":100},{"gak":1,"cell":3,"bpm":80}]}}"""
        val result = json.readTree(expressions.prepare(source, original, updated, 100, 120))
        assertEquals(listOf(120.0, 96.0), result.path("performancePlan").path("tempos").toList().map { it.path("bpm").asDouble() })
        assertEquals(expressions.hash(updated), result.path("sourceSha256").asString())
        assertEquals(expressions.hash(updated), result.path("performancePlan").path("input_sha256").asString())
        assertEquals(80, json.readTree(source).path("performancePlan").path("tempos")[1].path("bpm").asInt())
    }

    @Test fun `an unknown source binding cannot be silently reassigned`() {
        assertFailsWith<IllegalArgumentException> {
            expressions.prepare("""{"schema":"common-score-expressions/v1","sourceSha256":"bad","placements":[]}""", original, updated, 100, 120)
        }
    }

    @Test fun `numeric cue locations cannot move while pitch and version changes are allowed`() {
        val original = checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()
        val digest = expressions.hash(original)
        val companion = """{"schema":"common-score-expressions/v1","sourceSha256":"$digest","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$digest","tempos":[{"gak":1,"cell":3,"bpm":80}]}}"""
        val pitched = json.readTree(original)
        (pitched.path("events")[0] as tools.jackson.databind.node.ObjectNode).put("yul", "태")
        expressions.prepare(companion, original, json.writeValueAsString(pitched), 100, 120)
        (pitched.path("events")[0] as tools.jackson.databind.node.ObjectNode).put("kind", "REST")
        expressions.prepare(companion, original, json.writeValueAsString(pitched), 100, 120)
        (pitched.path("events")[0] as tools.jackson.databind.node.ObjectNode).put("startTick", 1)
        assertFailsWith<IllegalArgumentException> {
            expressions.prepare(companion, original, json.writeValueAsString(pitched), 100, 120)
        }
    }
}
