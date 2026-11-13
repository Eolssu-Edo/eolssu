package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraft
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class SheetAccompanimentTests {
    private val json = JsonMapper.builder().build()
    private val authoring = SheetAuthoring(json)
    private val score = checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText()
    private val sheet = UserSheet(1, 1, "기본 박 반주", "JEONGGANBO", "MANUAL", SheetStatus.EVALUABLE,
        null, null, 80, "QUARTER", 1, 1, score, 4, 0, Instant.EPOCH)

    @Test fun `basic accompaniment is independent metadata and retains immutable source`() {
        val baseline = assertNotNull(authoring.current(sheet))
        val request = reference(baseline).apply { set("accompaniment", basic()) }
        val result = json.readTree(authoring.merge(sheet, request.toString()))
        assertEquals(basic(), result.path("accompaniment"))
        assertEquals(json.readTree(baseline.toString()).path("source"), result.path("source"))
        assertEquals(json.readTree(score), result.path("source").path("score"))
    }

    @Test fun `latest draft setting is retained by omission and explicit null clears it`() {
        val confirmed = assertNotNull(authoring.current(sheet))
        confirmed.putObject("adaptation").put("transpose", 0).put("instrument", "DANSO").putArray("changes")
        val draft = confirmed.deepCopy().apply { set("accompaniment", basic()) }
        val editing = sheet.copy(authoringJson = confirmed.toString(),
            draft = SheetDraft(json.createObjectNode().set("authoring", draft).toString(), 1, Instant.EPOCH))
        assertEquals(basic(), json.readTree(authoring.merge(editing, null)).path("accompaniment"))
        assertTrue(assertNotNull(authoring.current(editing)).path("accompaniment").isMissingNode)
        assertEquals(confirmed.path("adaptation"), json.readTree(authoring.merge(editing, null)).path("adaptation"))
        val omitted = reference(draft)
        assertEquals(basic(), json.readTree(authoring.merge(editing, omitted.toString())).path("accompaniment"))
        assertTrue(json.readTree(authoring.merge(editing, omitted.putNull("accompaniment").toString())).path("accompaniment").isMissingNode)
    }

    @Test fun `a legacy draft captures its source before adding a basic accompaniment`() {
        val legacy = sheet.copy(scoreJson = null, draft = SheetDraft("""{"score":$score}""", 1, Instant.EPOCH))
        val captured = assertNotNull(authoring.current(legacy))
        val request = reference(captured).apply { set("accompaniment", basic()) }
        val merged = json.readTree(authoring.merge(legacy, request.toString(), score))
        assertEquals(basic(), merged.path("accompaniment"))
        assertEquals(json.readTree(captured.toString()).path("source"), merged.path("source"))
    }

    @Test fun `legacy confirmed sheet does not expose unconfirmed draft accompaniment`() {
        val confirmed = assertNotNull(authoring.current(sheet))
        val draft = confirmed.deepCopy().apply { set("accompaniment", basic()) }
        val raw = json.createObjectNode().apply { set("score", json.readTree(score)); set("authoring", draft) }
        val editing = sheet.copy(draft = SheetDraft(raw.toString(), 1, Instant.EPOCH))
        assertTrue(assertNotNull(authoring.current(editing)).path("accompaniment").isMissingNode)
        assertEquals(basic(), json.readTree(authoring.merge(editing, null)).path("accompaniment"))
        assertEquals(confirmed.path("source"), assertNotNull(authoring.current(editing)).path("source"))
    }

    @Test fun `all agreed presets allow fractional pulse ticks and an in-range pickup origin`() {
        val baseline = assertNotNull(authoring.current(sheet))
        val presets = listOf("QUARTER" to listOf(2), "QUARTER" to listOf(3), "DOTTED_QUARTER" to listOf(2),
            "EIGHTH" to listOf(2,3), "EIGHTH" to listOf(3,2), "EIGHTH" to listOf(2,2,3),
            "EIGHTH" to listOf(2,3,2), "EIGHTH" to listOf(3,2,2))
        for ((unit, groups) in presets) {
            val value = basic() as ObjectNode
            value.put("pulseUnit", unit).put("startTick", 3)
            value.set("groups", json.valueToTree(groups))
            val request = reference(baseline).apply { set("accompaniment", value) }
            assertEquals(value, json.readTree(authoring.merge(sheet, request.toString())).path("accompaniment"))
        }
    }

    @Test fun `unknown presets types origins and ambiguous source clocks are rejected`() {
        val baseline = assertNotNull(authoring.current(sheet))
        val invalid = listOf(
            """{"schema":"other","groups":[2,3],"pulseUnit":"EIGHTH","startTick":0}""",
            """{"schema":"basic-accompaniment/v1","groups":[4],"pulseUnit":"QUARTER","startTick":0}""",
            """{"schema":"basic-accompaniment/v1","groups":[2.0,3],"pulseUnit":"EIGHTH","startTick":0}""",
            """{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":-1}""",
            """{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":4}""",
            """{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":0.0}""",
            """{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":true}""",
            """{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":0,"extra":1}""",
            "[]", "true")
        for (value in invalid) {
            val request = reference(baseline).apply { set("accompaniment", json.readTree(value)) }
            assertFailsWith<IllegalArgumentException>(value) { authoring.merge(sheet, request.toString()) }
        }
        val request = reference(baseline).apply { set("accompaniment", basic()) }.toString()
        for (unit in listOf("JEONGGAN", "JANGDAN_BEAT")) {
            assertFailsWith<IllegalArgumentException>(unit) { authoring.merge(sheet, request, score.replace("QUARTER", unit)) }
        }
        assertFailsWith<IllegalArgumentException> { authoring.merge(sheet, request, score, 12) }
        assertFailsWith<IllegalArgumentException> { authoring.merge(sheet, request, score.replace("\"jangdan\": \"\"", "\"jangdan\": \"세마치\"")) }
        val forged = reference(baseline).apply { putObject("source").put("scoreSha256", "0".repeat(64)); set("accompaniment", basic()) }
        assertFailsWith<IllegalArgumentException> { authoring.merge(sheet, forged.toString()) }
    }

    private fun reference(metadata: ObjectNode) = json.createObjectNode().put("schema", "sheet-authoring/v1").apply {
        putObject("source").set("scoreSha256", metadata.path("source").path("scoreSha256"))
    }
    private fun basic() = json.readTree("""{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":0}""")
}
