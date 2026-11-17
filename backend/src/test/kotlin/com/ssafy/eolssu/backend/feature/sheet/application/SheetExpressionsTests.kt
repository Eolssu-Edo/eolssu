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
        val removed = json.readTree(original)
        (removed.path("columns")[0].path("cells") as tools.jackson.databind.node.ArrayNode).remove(3)
        (removed.path("events") as tools.jackson.databind.node.ArrayNode).remove(3)
        assertFailsWith<SheetExpressionPositionChanged> {
            expressions.prepare(companion, original, json.writeValueAsString(removed), 100, 120)
        }
        val renamed = json.readTree(original)
        (renamed.path("columns")[0].path("cells")[2] as tools.jackson.databind.node.ObjectNode).put("id", "c1-j0")
        assertFailsWith<SheetExpressionPositionChanged> {
            expressions.prepare(companion, original, json.writeValueAsString(renamed), 100, 120)
        }
    }

    @Test fun `jangdan retiming and splitting inside a cell keep the cue grid (S15P31E102-1091)`() {
        val original = checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()
        val digest = expressions.hash(original)
        val companion = """{"schema":"common-score-expressions/v1","sourceSha256":"$digest","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$digest","tempos":[{"gak":1,"cell":3,"bpm":80}]}}"""
        val retimed = json.readTree(original)
        (retimed.path("music") as tools.jackson.databind.node.ObjectNode).put("bpmUnit", "DOTTED_QUARTER").put("ticksPerBeat", 3).put("ticksPerJeonggan", 3)
        retimed.path("events").forEach { event ->
            (event as tools.jackson.databind.node.ObjectNode).put("startTick", event.path("startTick").asLong() * 3)
                .put("durationTick", event.path("durationTick").asLong() * 3)
        }
        val retimedJson = json.writeValueAsString(retimed)
        val result = json.readTree(expressions.prepare(companion, original, retimedJson, 100, 66))
        assertEquals(80.0 * 66 / 100, result.path("performancePlan").path("tempos")[0].path("bpm").asDouble())
        assertEquals(expressions.hash(retimedJson), result.path("performancePlan").path("input_sha256").asString())

        val split = json.readTree(original)
        val cell = split.path("columns")[0].path("cells")[1]
        (cell.path("units") as tools.jackson.databind.node.ArrayNode).addObject()
            .put("row", 1).put("col", 0).put("symbol", "청중").put("eventId", "c0-j1-r1-u0")
        val firstHalf = split.path("events")[1] as tools.jackson.databind.node.ObjectNode
        firstHalf.put("startTick", 2).put("durationTick", 1)
        (split.path("events") as tools.jackson.databind.node.ArrayNode).insert(2, firstHalf.deepCopy().put("id", "c0-j1-r1-u0").put("startTick", 3))
        (split.path("music") as tools.jackson.databind.node.ObjectNode).put("ticksPerBeat", 2).put("ticksPerJeonggan", 2)
        expressions.prepare(companion, original, json.writeValueAsString(split), 100, 100)
    }
}
