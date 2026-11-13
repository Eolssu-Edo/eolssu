package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.commonSourceSha256
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetTempoEditingTest {
    private fun draft() = smallScoreData().let { it.editingDraft(it.score.title, 80, null) }
    private fun hash(draft: SheetDraft) = commonSourceSha256(Json.encodeToJsonElement(requireNotNull(draft.original)))
    private fun JsonObject.entries(key: String) = getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.plan() = getValue("performancePlan").jsonObject

    @Test fun tempoMarksDoNotOccupyTheThreeOrnamentInputSlots() {
        val source = smallScoreData().let { score -> score.copy(events = score.events.mapIndexed { i, event ->
            if (i == 0) event.copy(sigimsae = event.sigimsae + "조금느리게") else event
        }) }
        val draft = source.editingDraft(source.score.title, 80, null)
        assertFalse(draft.sigimsaeAt(0).any { it.name == "조금느리게" })
        assertEquals(HanbaeMark.Slower, draft.tempoChanges.single().mark)
    }

    @Test fun unresolvedMarkOnHeldCellKeepsNotesAndExplicitEmptyPlan() {
        val before = draft()
        val after = before.withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, null))
        val encoded = after.toCommonScore()
        assertEquals(before.toCommonScore().events.map { it.copy(sigimsae = emptyList()) }, encoded.events.map { it.copy(sigimsae = emptyList()) })
        assertEquals(before.toCommonScore().columns, encoded.columns)
        assertEquals(listOf("조금느리게"), after.tempoAnnotations()[ScoreUnitKey("c0-j1", 0, 0)])
        val expression = requireNotNull(after.exportExpressions(null, hash(before), before.bpm))
        assertTrue(expression.plan().entries("tempos").isEmpty())
        assertEquals(hash(before), expression["sourceSha256"]!!.jsonPrimitive.content)
        val marks = encoded.toScore(expression).tempoMarks
        assertEquals(listOf(HanbaeMark.Slower), marks[ScoreUnitKey("c0-j1", 0, 0)])
        val eventMarks = expression.entries("placements").groupBy { it["eventId"]!!.jsonPrimitive.content }
            .mapValues { (_, p) -> p.flatMap { it["symbols"]!!.jsonArray.map { v -> v.jsonPrimitive.content } } }
        encoded.events.forEach { assertEquals(it.sigimsae, eventMarks[it.id].orEmpty()) }
    }

    @Test fun resolvedRampRoundTripsWithAllCompanionSettingsAndSupportsDeletion() {
        val initial = draft()
        val companion = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("sourceSha256", hash(initial))
            put("placements", JsonArray(emptyList()))
            put("relativeScale", "hwang-pyeong")
            put("performancePlan", buildJsonObject {
                put("schema", "danso-performance/v1"); put("input_sha256", hash(initial))
                put("tempos", JsonArray(emptyList()))
                put("repeats", buildJsonArray { add(buildJsonObject { put("start_gak", 1); put("end_gak", 1); put("times", 2) }) })
            })
        }
        val edited = initial.withTempoExpressions(companion).withTempoChange(SheetTempoChange(0, HanbaeMark.GraduallyFaster, 120.0, 2))
        val exported = requireNotNull(edited.exportExpressions(companion, hash(initial), 80))
        assertEquals(companion["relativeScale"], exported["relativeScale"])
        assertEquals(companion.plan()["repeats"], exported.plan()["repeats"])
        val cue = exported.plan().entries("tempos").single()
        assertEquals(120.0, cue["bpm"]!!.jsonPrimitive.double)
        assertEquals(2, cue["end_gak"]!!.jsonPrimitive.int)
        val restored = edited.toCommonScore().editingDraft(initial.title, 80, null).withTempoExpressions(exported)
        assertEquals(edited.tempoChanges, restored.tempoChanges)
        assertTrue(restored.withoutTempoChange(0).tempoChanges.isEmpty())
    }

    @Test fun existingNumericRampRetainsExplicitStartAndDoesNotInventAMark() {
        val initial = draft()
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("sourceSha256", hash(initial)); put("placements", JsonArray(emptyList()))
            put("performancePlan", buildJsonObject {
                put("schema", "danso-performance/v1"); put("input_sha256", hash(initial)); put("repeats", JsonArray(emptyList()))
                put("tempos", buildJsonArray { add(buildJsonObject { put("gak", 1); put("cell", 1); put("bpm", 160); put("start_bpm", 80); put("end_gak", 2); put("end_cell", 1) }) })
            })
        }
        val loaded = initial.withTempoExpressions(expression)
        assertNull(loaded.tempoChanges.single().mark)
        assertTrue(loaded.tempoAnnotations().isEmpty())
        val output = requireNotNull(loaded.exportExpressions(expression, hash(initial), 80))
        assertEquals(expression.plan()["tempos"], output.plan()["tempos"])
        assertEquals(initial.toCommonScore(), loaded.toCommonScore())
    }

    @Test fun globalTempoAndDraftReopenApplyTheRatioOnlyOnce() {
        val initial = draft().copy(bpm = 60)
        val changed = initial.withTempoChange(SheetTempoChange(1, HanbaeMark.Faster, 80.0))
        val global = changed.copy(bpm = 90)
        assertEquals(120.0, global.tempoChanges.single().targetBpm)
        val draftExpression = requireNotNull(global.exportExpressions(null, hash(initial), 60))
        assertEquals(80.0, draftExpression.plan().entries("tempos").single()["bpm"]!!.jsonPrimitive.double)
        val reopened = global.toCommonScore().editingDraft(global.title, 90, null).withTempoExpressions(draftExpression, referenceBpm = 60)
        assertEquals(120.0, reopened.tempoChanges.single().targetBpm)
        val afterServerConfirmation = requireNotNull(reopened.exportExpressions(draftExpression, hash(initial), 90))
        assertEquals(120.0, afterServerConfirmation.plan().entries("tempos").single()["bpm"]!!.jsonPrimitive.double)
    }

    @Test fun changesUndoRedoAndResolveOnlyAfterAMeaningfulTarget() {
        val initial = draft()
        val state = SheetEditUiState(EditSource.Mine("1"), initial, reviewIssues = listOf(SheetReviewIssue("t1", "c0-j1", "TEMPO_CUE_UNRESOLVED")))
        val unresolved = state.updateTempoSettings(initial.withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, null)))
        assertFalse("t1" in unresolved.validatedCheckedIssueIds)
        assertEquals(1, unresolved.completionValidation.pendingReviewCount)
        val resolved = unresolved.updateTempoSettings(unresolved.draft.withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, 60.0), replacing = 1))
        assertTrue("t1" in resolved.validatedCheckedIssueIds)
        assertEquals(unresolved.draft, resolved.undo().draft)
        assertEquals(resolved.draft, resolved.undo().redo().draft)
        assertEquals(initial, unresolved.undo().draft)
    }

    @Test fun overlappingRangesAndWrongDirectionAreRejected() {
        val initial = draft()
        val ramp = initial.withTempoChange(SheetTempoChange(0, HanbaeMark.GraduallyFaster, 120.0, 2))
        assertNotNull(ramp.tempoChangeError(SheetTempoChange(1, HanbaeMark.Slower, 60.0), null))
        assertNotNull(initial.tempoChangeError(SheetTempoChange(1, HanbaeMark.Slower, 100.0)))
        assertNotNull(initial.tempoChangeError(SheetTempoChange(1, HanbaeMark.GraduallySlower, 60.0, 0)))
        assertEquals(initial, initial.withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, 100.0)))
    }

    @Test fun sourceSlotIdentityAndRelativePrefixSurviveOrderedMarkRebuild() {
        val initial = draft()
        val old = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("sourceSha256", hash(initial))
            put("placements", buildJsonArray { add(buildJsonObject { put("eventId", "c0-j0-r0-u1"); put("cellId", "c0-j0"); put("row", 0); put("col", 1); put("symbols", buildJsonArray { add("니레") }) }) })
            put("sourceSlots", buildJsonArray { add(buildJsonObject {
                put("cellId", "c0-j0"); put("row", 0); put("cols", buildJsonArray { add(0); add(1) }); put("kind", "relative")
                put("anchorSymbol", "황"); put("symbols", buildJsonArray { add("니"); add("니레") }); put("lengthMode", "performed"); put("basis", "test-source")
            }) })
        }
        val edited = initial.withTempoExpressions(old).withTempoChange(SheetTempoChange(0, HanbaeMark.Slower, 60.0))
        val output = requireNotNull(edited.exportExpressions(old, hash(initial), 80))
        val slot = output.entries("sourceSlots").single()
        assertEquals(listOf("니", "조금느리게", "니레"), slot["symbols"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(old.entries("sourceSlots").single().filterKeys { it != "symbols" }, slot.filterKeys { it != "symbols" })
    }

    @Test fun exportIntegrationExample() {
        val initial = draft()
        val edited = initial.withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, 60.0))
        val expression = requireNotNull(edited.exportExpressions(null, hash(initial), 80))
        println("HANBAE_FE_EXAMPLE=" + buildJsonObject {
            put("original", Json.encodeToJsonElement(initial.original)); put("score", Json.encodeToJsonElement(edited.toCommonScore()))
            put("expressions", expression); put("oldBpm", 80); put("newBpm", 80)
        })
    }
}
