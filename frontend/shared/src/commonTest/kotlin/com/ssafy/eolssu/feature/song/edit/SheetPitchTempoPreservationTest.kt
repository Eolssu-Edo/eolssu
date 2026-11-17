package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.*
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetPitchTempoPreservationTest {
    private val source = smallScoreData()
    private val sourceHash = commonSourceSha256(Json.encodeToJsonElement(source))
    private val companion = buildJsonObject {
        put("schema", "common-score-expressions/v1")
        put("sourceSha256", sourceHash)
        put("placements", JsonArray(emptyList()))
        put("performancePlan", buildJsonObject {
            put("schema", "danso-performance/v1"); put("input_sha256", sourceHash)
            put("repeats", buildJsonArray { add(buildJsonObject {
                put("start_gak", 1); put("end_gak", 1); put("times", 2)
            }) })
            put("tempos", buildJsonArray { add(buildJsonObject {
                put("gak", 1); put("cell", 1); put("start_bpm", 80); put("bpm", 160)
                put("end_gak", 2); put("end_cell", 1)
            }) })
        })
    }
    private fun draft() = source.editingDraft("빠르기 보존", 80, null)
        .withTempoExpressions(companion, referenceBpm = 80).copy(bpm = 120)

    @Test fun instrumentAdaptationKeepsNumericTempoAndItsReferenceAcrossUndoAndDraftReentry() {
        val before = SheetEditUiState(EditSource.Mine("31"), draft(), hasTempoPlan = true)
        val capability = CatalogAuthoringCapabilities("2", "SOGEUM", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val plan = planPitchAdaptation(before.draft.sourcePitchNotes(), emptyList()).first { it.transpose == 4 }
        val changed = before.applyAdaptation(plan, capability)
        assertEquals("SOGEUM", changed.draft.toCommonScore().score.instrument)
        assertPreserved(changed.draft)
        assertTrue(changed.hasTempoPlan)
        assertEquals(before.draft, changed.undo().draft)
        assertEquals(changed.draft, changed.undo().redo().draft)
        assertPreserved(changed.draft.applyAdaptation(plan, capability))
    }

    @Test fun correctingAnExistingPitchKeepsNumericTempoAndItsReference() {
        val before = draft()
        val target = SheetPitchCorrection(0, 0, 0, 0, Yul.Hwangjong to 0,
            before.toCommonScore().columns.first().cells.first().units.first().eventId)
        val after = before.correctPitch(target, Yul.Taeju to 0)
        assertEquals("태", after.toCommonScore().events.first { it.id == target.eventId }.yul)
        assertPreserved(after)
    }

    @Test fun adaptationKeepsTheRampInTheEstimatedDuration() {
        val plan = companion.getValue("performancePlan").jsonObject
        val timed = draft().withTempoExpressions(JsonObject(companion + ("performancePlan" to
            JsonObject(plan + ("repeats" to JsonArray(emptyList()))))), referenceBpm = 80)
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val pitchPlan = planPitchAdaptation(timed.sourcePitchNotes(), emptyList()).first { it.transpose == 4 }
        val after = timed.applyAdaptation(pitchPlan, capability)
        // Two beats of 120→240, then one beat at 240; source cells remain one quarter beat each.
        val expected = kotlin.math.ln(2.0) + 0.25
        assertEquals(expected, assertNotNull(timed.estimatedDurationSeconds(120)), 1e-9)
        assertEquals(expected, assertNotNull(after.estimatedDurationSeconds(120)), 1e-9)
    }

    private fun assertPreserved(after: SheetDraft) {
        // 시작값이 앞 빠르기와 같은 점점 구간은 바뀌는 곳 하나로 읽고, 끝 뒤에는 목표를 유지한다.
        assertEquals(listOf(SheetTempoCue(0, SheetTempoKind.Ramp, 240.0, 1)), after.tempoCues)
        assertEquals(companion, after.tempoExpressions)
        val draftExpression = assertNotNull(after.exportExpressions(companion, sourceHash, serverBpm = 80))
        assertTempoNumbers(draftExpression, 80.0, 160.0)
        val reopened = after.toCommonScore().editingDraft(after.title, 120, null)
            .withTempoExpressions(draftExpression, referenceBpm = 80)
        assertEquals(after.tempoCues, reopened.tempoCues)
        val confirmed = assertNotNull(reopened.exportExpressions(draftExpression, sourceHash, serverBpm = 120))
        assertTempoNumbers(confirmed, 120.0, 240.0)
        val confirmedDraft = reopened.toCommonScore().editingDraft(reopened.title, 120, null)
            .withTempoExpressions(confirmed, referenceBpm = 120)
        assertEquals(after.tempoCues, confirmedDraft.tempoCues)
        assertEquals(sourceHash, confirmed.getValue("sourceSha256").jsonPrimitive.content)
        assertEquals(sourceHash, confirmed.getValue("performancePlan").jsonObject.getValue("input_sha256").jsonPrimitive.content)
    }

    private fun assertTempoNumbers(expression: JsonObject, start: Double, target: Double) {
        val plan = expression.getValue("performancePlan").jsonObject
        val cue = plan.getValue("tempos").jsonArray.single().jsonObject
        assertEquals(start, cue.getValue("start_bpm").jsonPrimitive.double)
        assertEquals(target, cue.getValue("bpm").jsonPrimitive.double)
        assertEquals(1, cue.getValue("gak").jsonPrimitive.int)
        assertEquals(2, cue.getValue("end_gak").jsonPrimitive.int)
        assertEquals(companion.getValue("performancePlan").jsonObject.getValue("repeats"), plan["repeats"])
    }
}
