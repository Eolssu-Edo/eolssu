package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.BasicAccompanimentPreset
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.CommonScoreData as c
import com.ssafy.eolssu.data.song.ModelTempoChange
import com.ssafy.eolssu.data.song.commonSourceSha256
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * 빠르기가 바뀌는 곳마다 기호 하나(S15P31E102-1089). 시작 빠르기는 받지 않고 앞 빠르기에서 출발하며,
 * 바뀐 빠르기는 다음 변경까지 유지되고 본래 속도로(ㅂㅅ)가 기본 빠르기로 돌린다.
 */
class SheetTempoEditingTest {
    private fun draft() = smallScoreData().let { it.editingDraft(it.score.title, 80, null) }
    private fun hash(draft: SheetDraft) = commonSourceSha256(Json.encodeToJsonElement(requireNotNull(draft.original)))
    private fun JsonObject.entries(key: String) = getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.plan() = getValue("performancePlan").jsonObject
    private fun JsonObject.tempoRows() = plan().entries("tempos").map {
        Triple(it.getValue("gak").jsonPrimitive.int, it.getValue("cell").jsonPrimitive.int, it.getValue("bpm").jsonPrimitive.double)
    }
    private fun step(at: Int, bpm: Double) = SheetTempoCue(at, SheetTempoKind.Step, bpm)
    private fun ramp(at: Int, bpm: Double, end: Int) = SheetTempoCue(at, SheetTempoKind.Ramp, bpm, end)

    /** 한 각에 한 정간인 악보. 정간 번호가 곧 각-1이라 위치를 읽기 쉽다. */
    private fun longDraft(gak: Int = 8, bpm: Int = 160): SheetDraft {
        return CommonScoreData(1, c.Header("긴 악보", "DANSO", 0, "long-score", 1), c.Music(311.13, "", "QUARTER", 4, 4),
            (0 until gak).map { i -> c.Column(i, listOf(c.Cell("c$i-j0", 0, listOf(c.Unit(0, 0, "황", "e$i"))))) },
            (0 until gak).map { i -> c.Event("e$i", "NOTE", "황", 0, i * 4L, 4, "c$i-j0", false, emptyList()) },
            c.Provenance("long-score", "0".repeat(64), "inferred", "test-fixture"),
        ).editingDraft("긴 악보", bpm, null)
    }

    private fun reopen(draft: SheetDraft, base: SheetDraft) = draft.toCommonScore().editingDraft(base.title, base.bpm, null)
        .withTempoExpressions(requireNotNull(draft.exportExpressions(null, hash(base), base.bpm)))

    private fun SheetDraft.marks() = tempoAnnotations().values.flatten().map { HanbaeMark.fromSymbol(it) }

    @Test fun userExampleWritesOneMarkPerChange() {
        // 기본 160에서 2각에 80으로 바로 느려지고, 3~5각에 점점 160으로 돌아온다.
        val initial = longDraft()
        val edited = initial.withTempoCue(step(1, 80.0)).withTempoCue(ramp(2, 160.0, 4))
        assertEquals(listOf(HanbaeMark.Slower, HanbaeMark.GraduallyFaster), edited.marks())
        assertEquals(listOf(Triple(2, 1, 80.0), Triple(3, 1, 160.0)), requireNotNull(edited.exportExpressions(null, hash(initial), 160)).tempoRows())
        val rampEntry = requireNotNull(edited.exportExpressions(null, hash(initial), 160)).plan().entries("tempos").last()
        assertEquals(6, rampEntry.getValue("end_gak").jsonPrimitive.int)
        assertFalse("start_bpm" in rampEntry)
        // 점점 구간이 끝나면 이미 기본 빠르기라 기본으로 돌아가는 변경은 넣을 수 없다.
        assertNotNull(edited.tempoCueError(step(5, 160.0)))
        assertEquals(edited.tempoCues, reopen(edited, initial).tempoCues)
    }

    @Test fun marksFollowThePriorTempo() {
        val base = 160.0
        assertEquals(HanbaeMark.Faster, step(0, 170.0).markFrom(160.0, base))
        assertEquals(HanbaeMark.Slower, step(0, 150.0).markFrom(160.0, base))
        assertEquals(HanbaeMark.GraduallyFaster, ramp(0, 160.0, 1).markFrom(80.0, base))
        assertEquals(HanbaeMark.GraduallySlower, ramp(0, 80.0, 1).markFrom(160.0, base))
        // 기본 빠르기로 바로 돌아가면 본래 속도로가 자동으로 붙는다. 점점 돌아가는 것은 점점 기호다.
        assertEquals(HanbaeMark.Original, step(0, 160.0).markFrom(80.0, base))
        assertEquals(HanbaeMark.GraduallyFaster, ramp(0, 160.0, 1).markFrom(80.0, base))
        // 이미 있던 기호도 숫자에 맞춰 다시 붙는다.
        val edited = draft().withTempoCues(listOf(SheetTempoCue(1, SheetTempoKind.Step, 60.0, mark = HanbaeMark.Faster)))
        assertEquals(listOf("조금느리게"), edited.tempoAnnotations()[ScoreUnitKey("c0-j1", 0, 0)])
    }

    @Test fun sameTempoChangesAreRejectedAndOriginalNeedsADifferentTempo() {
        val initial = longDraft()
        assertNotNull(initial.tempoCueError(step(2, 160.0)), "기본과 같은 바로")
        assertNotNull(initial.tempoCueError(ramp(2, 160.0, 3)), "기본과 같은 점점")
        val slower = initial.withTempoCue(step(1, 120.0))
        assertNotNull(slower.tempoCueError(step(3, 120.0)), "같은 효과가 두 번")
        assertNull(slower.tempoCueError(step(3, 100.0)))
        assertNull(slower.tempoCueError(step(3, 160.0)))
        assertEquals(slower, slower.withTempoCue(step(3, 120.0)))
    }

    @Test fun changesInsideARampOrOnTheSameCellAreRejectedButTouchingIsAllowed() {
        val withRamp = longDraft().withTempoCue(ramp(1, 120.0, 3))
        assertNotNull(withRamp.tempoCueError(step(2, 100.0)))
        assertNotNull(withRamp.tempoCueError(step(1, 100.0)))
        assertNotNull(withRamp.tempoCueError(ramp(0, 100.0, 1)))
        // 점점 구간이 끝난 바로 다음 정간의 바로 바꾸기는 앞 빠르기(120) 기준이다.
        val touching = withRamp.withTempoCue(step(4, 100.0))
        assertEquals(listOf(HanbaeMark.GraduallySlower, HanbaeMark.Slower), touching.marks())
        assertNotNull(touching.tempoCueError(ramp(2, 100.0, 4), replacing = 1))
    }

    @Test fun deletingAChangeThatMakesALaterOneRedundantIsBlocked() {
        val initial = longDraft().withTempoCue(step(1, 80.0)).withTempoCue(step(3, 160.0))
        assertNotNull(initial.tempoCueDeletionError(1))
        assertEquals(initial, initial.withoutTempoCue(1))
        assertNull(initial.tempoCueDeletionError(3))
        assertEquals(listOf(1), initial.withoutTempoCue(3).tempoCues.map { it.start })
    }

    @Test fun editingAnEarlierChangeRecomputesLaterMarks() {
        val initial = longDraft().withTempoCue(step(1, 120.0)).withTempoCue(step(3, 140.0))
        assertEquals(listOf(HanbaeMark.Slower, HanbaeMark.Faster), initial.marks())
        val edited = initial.withTempoCue(step(1, 150.0), replacing = 1)
        assertEquals(listOf(HanbaeMark.Slower, HanbaeMark.Slower), edited.marks())
    }

    @Test fun stepBackToTheBaseShowsOriginalAndReadsBack() {
        val initial = longDraft()
        val edited = initial.withTempoCue(step(1, 120.0)).withTempoCue(step(4, 160.0))
        assertEquals(listOf(Triple(2, 1, 120.0), Triple(5, 1, 160.0)), requireNotNull(edited.exportExpressions(null, hash(initial), 160)).tempoRows())
        assertEquals(listOf(HanbaeMark.Slower, HanbaeMark.Original), edited.marks())
        assertEquals(edited.tempoCues, reopen(edited, initial).tempoCues)
    }

    @Test fun playbackChangesFollowTheCues() {
        val draft = longDraft().withTempoCue(step(1, 120.0)).withTempoCue(ramp(3, 60.0, 4)).withTempoCue(step(6, 160.0))
        assertEquals(listOf(ModelTempoChange(1, 120.0), ModelTempoChange(3, 60.0, 5, 120.0), ModelTempoChange(6, 160.0)),
            draft.playbackTempoChanges)
    }

    @Test fun importedRampWithADifferentStartIsSplitIntoAStepAndARamp() {
        val initial = longDraft()
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("sourceSha256", hash(initial)); put("placements", JsonArray(emptyList()))
            put("performancePlan", buildJsonObject {
                put("schema", "danso-performance/v1"); put("input_sha256", hash(initial)); put("repeats", JsonArray(emptyList()))
                put("tempos", buildJsonArray { add(buildJsonObject { put("gak", 2); put("cell", 1); put("bpm", 160); put("start_bpm", 80); put("end_gak", 5); put("end_cell", 1) }) })
            })
        }
        val loaded = initial.withTempoExpressions(expression)
        assertEquals(listOf(step(1, 80.0), ramp(2, 160.0, 3)), loaded.tempoCues)
        assertTrue(loaded.tempoAnnotations().isEmpty())
        // 손대지 않으면 저장 계획은 그대로다.
        assertEquals(listOf(Triple(2, 1, 160.0)), requireNotNull(loaded.exportExpressions(expression, hash(initial), 160)).tempoRows())
        assertEquals(initial.toCommonScore(), loaded.toCommonScore())
    }

    @Test fun rampWhoseStartMatchesThePriorTempoStaysOneCue() {
        val initial = draft()
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("placements", JsonArray(emptyList()))
            put("performancePlan", buildJsonObject {
                put("tempos", buildJsonArray { add(buildJsonObject { put("gak", 1); put("cell", 1); put("bpm", 160); put("start_bpm", 80); put("end_gak", 2); put("end_cell", 1) }) })
            })
        }
        assertEquals(listOf(ramp(0, 160.0, 1)), initial.withTempoExpressions(expression).tempoCues)
    }

    @Test fun legacyReturnEntriesReadAsAStepBackToTheBase() {
        // 이전 구현은 구간 끝 다음에 기본 빠르기 항목을 저장했다. 이제 기본으로 바로 돌아가는 곳(ㅂㅅ)으로 읽는다.
        val initial = longDraft()
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("placements", JsonArray(emptyList()))
            put("performancePlan", buildJsonObject {
                put("tempos", buildJsonArray {
                    add(buildJsonObject { put("gak", 3); put("cell", 1); put("bpm", 120) })
                    add(buildJsonObject { put("gak", 6); put("cell", 1); put("bpm", 160) })
                })
            })
        }
        assertEquals(listOf(step(2, 120.0), step(5, 160.0)), initial.withTempoExpressions(expression).tempoCues)
    }

    @Test fun tempoMarksDoNotOccupyTheThreeOrnamentInputSlots() {
        val source = smallScoreData().let { score -> score.copy(events = score.events.mapIndexed { i, event ->
            if (i == 0) event.copy(sigimsae = event.sigimsae + "조금느리게") else event
        }) }
        val draft = source.editingDraft(source.score.title, 80, null)
        assertFalse(draft.sigimsaeAt(0).any { it.name == "조금느리게" })
        val cue = draft.tempoCues.single()
        assertEquals(HanbaeMark.Slower, cue.mark)
        assertFalse(cue.resolved)
    }

    @Test fun unresolvedMarkOnHeldCellKeepsNotesAndExplicitEmptyPlan() {
        val before = draft()
        val after = before.withTempoCues(listOf(SheetTempoCue(1, SheetTempoKind.Step, mark = HanbaeMark.Slower)))
        val encoded = after.toCommonScore()
        assertEquals(before.toCommonScore().events.map { it.copy(sigimsae = emptyList()) }, encoded.events.map { it.copy(sigimsae = emptyList()) })
        assertEquals(before.toCommonScore().columns, encoded.columns)
        assertEquals(listOf("조금느리게"), after.tempoAnnotations()[ScoreUnitKey("c0-j1", 0, 0)])
        val expression = requireNotNull(after.exportExpressions(null, hash(before), before.bpm))
        assertTrue(expression.plan().entries("tempos").isEmpty())
        val marks = encoded.toScore(expression).tempoMarks
        assertEquals(listOf(HanbaeMark.Slower), marks[ScoreUnitKey("c0-j1", 0, 0)])
        val eventMarks = expression.entries("placements").groupBy { it["eventId"]!!.jsonPrimitive.content }
            .mapValues { (_, p) -> p.flatMap { it["symbols"]!!.jsonArray.map { v -> v.jsonPrimitive.content } } }
        encoded.events.forEach { assertEquals(it.sigimsae, eventMarks[it.id].orEmpty()) }
    }

    @Test fun firstManualTempoChangePreservesInferredRepeatsAndDeletionKeepsThem() {
        for (mark in listOf("반복표", "도돌이표")) {
            val source = smallScoreData().let { score -> score.copy(events = score.events.mapIndexed { i, event ->
                if (i == 0) event.copy(sigimsae = event.sigimsae + mark) else event
            }) }
            val initial = source.editingDraft(source.score.title, 80, null)
            val edited = initial.withTempoCue(step(1, 60.0))
            val expected = buildJsonArray { add(buildJsonObject { put("start_gak", 1); put("end_gak", 1); put("times", 2) }) }
            assertEquals(expected, requireNotNull(edited.exportExpressions(null, hash(initial), 80)).plan()["repeats"], mark)
            assertEquals(expected, requireNotNull(edited.withoutTempoCue(1).exportExpressions(null, hash(initial), 80)).plan()["repeats"], mark)
        }
    }

    @Test fun tempoBeforeHeldOrnamentKeepsEventAndPlacementOrderConsistent() {
        val source = smallScoreData().let { score ->
            val id = score.events.first().id
            score.copy(columns = score.columns.mapIndexed { i, column -> if (i != 0) column else column.copy(cells =
                column.cells.map { it.copy(units = listOf(com.ssafy.eolssu.data.song.CommonScoreData.Unit(0, 0,
                    if (it.index == 0) "황" else "", id))) }) },
                events = listOf(score.events.first().copy(durationTick = 8, sigimsae = listOf("니레")), score.events.last()))
        }
        val initial = source.editingDraft(source.score.title, 80, null)
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1")
            put("placements", buildJsonArray { add(buildJsonObject {
                put("eventId", source.events.first().id); put("cellId", "c0-j1"); put("row", 0); put("col", 0)
                put("symbols", buildJsonArray { add("니레") })
            }) })
        }
        val edited = initial.withTempoExpressions(expression).withTempoCue(step(0, 60.0))
        val output = requireNotNull(edited.exportExpressions(expression, hash(initial), 80))
        val signs = output.entries("placements").filter { it.getValue("eventId").jsonPrimitive.content == source.events.first().id }
            .flatMap { it.getValue("symbols").jsonArray.map { raw -> raw.jsonPrimitive.content } }
        assertEquals(listOf("조금느리게", "니레"), signs)
        assertEquals(signs, edited.toCommonScore().events.first().sigimsae)
    }

    @Test fun globalTempoAndDraftReopenApplyTheRatioOnlyOnce() {
        val initial = draft().copy(bpm = 60)
        val changed = initial.withTempoCue(step(1, 80.0))
        val global = changed.copy(bpm = 90)
        assertEquals(120.0, global.tempoCues.single().bpm)
        val draftExpression = requireNotNull(global.exportExpressions(null, hash(initial), 60))
        assertEquals(80.0, draftExpression.plan().entries("tempos").single()["bpm"]!!.jsonPrimitive.double)
        val reopened = global.toCommonScore().editingDraft(global.title, 90, null).withTempoExpressions(draftExpression, referenceBpm = 60)
        assertEquals(120.0, reopened.tempoCues.single().bpm)
        val afterServerConfirmation = requireNotNull(reopened.exportExpressions(draftExpression, hash(initial), 90))
        assertEquals(120.0, afterServerConfirmation.plan().entries("tempos").single()["bpm"]!!.jsonPrimitive.double)
    }

    @Test fun tempoAndBasicAccompanimentEditsPreserveBothPlansAndUndoIndependently() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val initial = source.editingDraft(source.score.title, 80, null)
        val expression = buildJsonObject {
            put("schema", "common-score-expressions/v1"); put("sourceSha256", hash(initial))
            put("placements", JsonArray(emptyList()))
            put("performancePlan", buildJsonObject {
                put("schema", "danso-performance/v1"); put("input_sha256", hash(initial))
                put("repeats", buildJsonArray { add(buildJsonObject { put("start_gak", 1); put("end_gak", 1); put("times", 2) }) })
                put("tempos", buildJsonArray { add(buildJsonObject { put("gak", 1); put("cell", 1); put("bpm", 160); put("end_gak", 2); put("end_cell", 1) }) })
            })
        }
        val metadata = buildJsonObject {
            put("schema", "sheet-authoring/v1")
            put("source", buildJsonObject { put("scoreSha256", hash(initial)) })
        }
        val before = SheetEditUiState(EditSource.Mine("31"), initial.copy(authoring = metadata).withTempoExpressions(expression))
            .selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 1)
        assertEquals(4L, assertNotNull(before.draft.basicAccompaniment).startTick)
        val edited = before.updateTempoSettings(before.draft.withTempoCue(step(2, 60.0)).copy(bpm = 120))
        assertEquals(before.draft.authoring, edited.draft.authoring)
        assertEquals(90.0, edited.draft.tempoCues.last().bpm)
        val exported = requireNotNull(edited.draft.exportExpressions(expression, hash(initial), 80))
        assertEquals(expression.plan()["repeats"], exported.plan()["repeats"])
        assertEquals(160.0, exported.plan().entries("tempos").first().getValue("bpm").jsonPrimitive.double, 1e-9)
        assertEquals(before.draft, edited.undo().draft)
        assertEquals(edited.draft, edited.undo().redo().draft)
        val moved = edited.selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 2)
        assertEquals(8L, assertNotNull(moved.draft.basicAccompaniment).startTick)
        assertEquals(edited.draft.tempoExpressions, moved.draft.tempoExpressions)
        assertEquals(edited.draft, moved.undo().draft)
    }

    @Test fun changesUndoRedoAndResolveOnlyAfterAMeaningfulTarget() {
        val initial = draft()
        val state = SheetEditUiState(EditSource.Mine("1"), initial, reviewIssues = listOf(SheetReviewIssue("t1", "c0-j1", "TEMPO_CUE_UNRESOLVED")))
        val unresolved = state.updateTempoSettings(initial.withTempoCues(listOf(SheetTempoCue(1, SheetTempoKind.Step, mark = HanbaeMark.Slower))))
        assertFalse("t1" in unresolved.validatedCheckedIssueIds)
        assertEquals(1, unresolved.completionValidation.pendingReviewCount)
        val resolved = unresolved.updateTempoSettings(unresolved.draft.withTempoCue(step(1, 60.0), replacing = 1))
        assertTrue("t1" in resolved.validatedCheckedIssueIds)
        assertEquals(unresolved.draft, resolved.undo().draft)
        assertEquals(resolved.draft, resolved.undo().redo().draft)
        assertEquals(initial, unresolved.undo().draft)
    }

    @Test fun changesAreListedInScoreOrderWhateverOrderTheyWereAddedOrMoved() {
        val added = longDraft().withTempoCue(step(6, 100.0)).withTempoCue(step(2, 120.0))
        assertEquals(listOf(2, 6), added.tempoCues.map { it.start })
        val moved = added.withTempoCue(step(7, 120.0), replacing = 2)
        assertEquals(listOf(6, 7), moved.tempoCues.map { it.start })
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
        val edited = initial.withTempoExpressions(old).withTempoCue(step(0, 60.0))
        val output = requireNotNull(edited.exportExpressions(old, hash(initial), 80))
        val slot = output.entries("sourceSlots").single()
        assertEquals(listOf("니", "조금느리게", "니레"), slot["symbols"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(old.entries("sourceSlots").single().filterKeys { it != "symbols" }, slot.filterKeys { it != "symbols" })
    }
}
