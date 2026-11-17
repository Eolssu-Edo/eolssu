package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.commonSourceSha256
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

/** 구간 빠르기·반복이 있는 악보의 각 추가·삭제(S15P31E102-1095, 스펙 2단계). 각 추가는 맨 끝에 붙는다(S15P31E102-1139). 한 각은 4정간이다. */
class SheetTempoColumnsTest {
    private fun score(columns: Int): CommonScoreData {
        val base = smallScoreData()
        val cols = (0 until columns).map { c -> CommonScoreData.Column(c, (0 until 4).map { j ->
            CommonScoreData.Cell("c$c-j$j", j, listOf(CommonScoreData.Unit(0, 0, "황", "c$c-j$j-r0-u0")))
        }) }
        val events = (0 until columns).flatMap { c -> (0 until 4).map { j ->
            CommonScoreData.Event("c$c-j$j-r0-u0", "NOTE", "황", 0, (c * 4L + j), 1, "c$c-j$j", false, emptyList())
        } }
        return base.copy(music = base.music.copy(jangdan = "", bpmUnit = "QUARTER", ticksPerBeat = 1, ticksPerJeonggan = 1),
            columns = cols, events = events)
    }

    private fun state(columns: Int, vararg changes: SheetTempoCue): SheetEditUiState {
        val base = SheetEditUiState(EditSource.Mine("31"), score(columns).editingDraft("각 편집", 80, null))
        val planned = changes.fold(base.draft) { draft, change -> draft.withTempoCue(change).also { assertNotEquals(draft, it, "$change") } }
        return base.updateTempoSettings(planned).also { assertTrue(it.hasTempoPlan) }
    }

    private fun step(at: Int, bpm: Double) = SheetTempoCue(at, SheetTempoKind.Step, bpm)
    private fun ramp(at: Int, bpm: Double, end: Int) = SheetTempoCue(at, SheetTempoKind.Ramp, bpm, end)

    private fun SheetDraft.plan() = requireNotNull(exportExpressions(null, 80)).getValue("performancePlan").jsonObject
    private fun JsonObject.positions(key: String) = getValue(key).jsonArray.map { it.jsonObject.let { e ->
        listOfNotNull(e["gak"], e["cell"], e["end_gak"], e["end_cell"], e["start_gak"]).map { v -> v.jsonPrimitive.int }
    } }

    private fun SheetDraft.withRepeats(vararg ranges: Pair<Int, Int>): SheetDraft {
        val expression = requireNotNull(tempoExpressions)
        val plan = expression.getValue("performancePlan").jsonObject
        return withTempoExpressions(JsonObject(expression + ("performancePlan" to JsonObject(plan + ("repeats" to JsonArray(ranges.map { (s, e) ->
            buildJsonObject { put("start_gak", s); put("end_gak", e); put("times", 2) }
        }))))), tempoReferenceBpm ?: bpm)
    }

    private fun JsonObject.tempoMarkCells() = getValue("placements").jsonArray.map { it.jsonObject }
        .filter { p -> p.getValue("symbols").jsonArray.any { HanbaeMark.fromSymbol(it.jsonPrimitive.content) != null } }

    @Test fun addingAGakAppendsItAtTheEndAndKeepsChangesMarksAndTheirCompanionIds() {
        val planned = state(4, step(4, 120.0), step(9, 60.0))
        // 어느 각을 골랐든 맨 끝에 붙고 새 각의 첫 정간을 고른다.
        val added = planned.copy(selected = 0).addColumn()
        assertEquals(added.draft, planned.copy(selected = planned.draft.indexOf(2, 0)).addColumn().draft)
        assertNull(added.message)
        assertEquals(5, added.draft.columnCount)
        assertEquals(16, added.selected)
        assertEquals(List(4) { emptyList() }, added.draft.cells.drop(16))
        assertEquals(planned.draft.tempoCues, added.draft.tempoCues)
        assertEquals(listOf(listOf(2, 1), listOf(3, 2)), added.draft.plan().positions("tempos"))
        val exported = requireNotNull(added.draft.exportExpressions(null, 80))
        val score = added.draft.toCommonScore()
        assertEquals(commonSourceSha256(Json.encodeToJsonElement(score)), exported.getValue("sourceSha256").jsonPrimitive.content)
        assertEquals(exported.getValue("sourceSha256"), exported.getValue("performancePlan").jsonObject.getValue("input_sha256"))
        assertEquals(listOf("c1-j0" to "c1-j0-r0-u0", "c2-j1" to "c2-j1-r0-u0"),
            exported.tempoMarkCells().map { it.getValue("cellId").jsonPrimitive.content to it.getValue("eventId").jsonPrimitive.content })
        assertEquals(listOf("조금느리게"), score.events.first { it.id == "c2-j1-r0-u0" }.sigimsae)
        assertEquals(planned.draft, added.undo().draft)
    }

    @Test fun anAppendedGakContinuesTheLastTempoAndStaysOutsideARepeatOnTheLastGak() {
        val planned = state(3, step(9, 120.0)).let { it.updateTempoSettings(it.draft.withRepeats(3 to 3)) }
        val added = planned.copy(selected = 0).addColumn()
        // 끝 각까지 이어지던 빠르기는 새 각에도 이어진다. 반복은 끝 각을 포함하므로 그 뒤 새 각은 반복 밖이다.
        assertNull(added.message)
        assertEquals(planned.draft.tempoCues, added.draft.tempoCues)
        assertEquals(listOf(listOf(3, 2)), added.draft.plan().positions("tempos"))
        assertEquals(listOf(listOf(3, 3)), added.draft.plan().positions("repeats"))
    }

    @Test fun gakWithAChangeStartARampOrARepeatCannotBeDeleted() {
        val planned = state(5, ramp(5, 120.0, 11)).let { it.updateTempoSettings(it.draft.withRepeats(5 to 5)) }
        // 점점 구간은 정간 5~11(2·3각), 반복은 5각이다.
        for (column in listOf(1, 2, 4)) {
            val tried = planned.copy(selected = planned.draft.indexOf(column, 0)).deleteColumn()
            assertEquals(SheetEditMessage.TempoPlanBeforeReshape, tried.message, "$column")
            assertEquals(planned.draft, tried.draft)
        }
        assertNull(planned.copy(selected = planned.draft.indexOf(3, 0)).deleteColumn().message)
        val first = planned.copy(selected = 0).deleteColumn()
        assertNull(first.message)
        assertEquals(listOf(1), first.draft.tempoCues.map { it.start })
        assertEquals(7, first.draft.tempoCues.single().last)
        assertEquals(listOf(listOf(4, 4)), first.draft.plan().positions("repeats"))
    }

    @Test fun deletingAGakThatOnlyContinuesATempoShiftsLaterPositions() {
        val planned = state(4, step(1, 120.0), step(13, 140.0))
        val deleted = planned.copy(selected = planned.draft.indexOf(1, 0)).deleteColumn()
        assertNull(deleted.message)
        assertEquals(listOf(1 to HanbaeMark.Faster, 9 to HanbaeMark.Faster), deleted.draft.tempoCues.map { it.start to it.mark })
        assertEquals(listOf(listOf(1, 2), listOf(3, 2)), deleted.draft.plan().positions("tempos"))
        assertEquals(listOf("c0-j1", "c2-j1"), requireNotNull(deleted.draft.exportExpressions(null, 80)).tempoMarkCells()
            .map { it.getValue("cellId").jsonPrimitive.content })
    }

    @Test fun aGakWhereTheTempoReturnsToTheBaseCannotBeDeleted() {
        // 기본으로 돌아가는 곳(본래 속도로)도 바뀌는 곳이므로 그 각은 지우지 않는다.
        val planned = state(3, step(1, 60.0), step(4, 80.0))
        assertEquals(listOf(HanbaeMark.Slower, HanbaeMark.Original), planned.draft.tempoCues.map { it.mark })
        val tried = planned.copy(selected = planned.draft.indexOf(1, 0)).deleteColumn()
        assertEquals(SheetEditMessage.TempoPlanBeforeReshape, tried.message)
        assertNull(planned.copy(selected = planned.draft.indexOf(2, 0)).deleteColumn().message)
    }

    @Test fun anAppendedGakTakesChangesAndGetsItsMarkAfterSaving() {
        val added = state(2, step(1, 120.0)).copy(selected = 0).addColumn()
        val inserted = added.draft.indexOf(2, 0)
        assertNull(added.draft.tempoCueError(step(inserted, 100.0)))
        val withChange = added.draft.withTempoCue(step(inserted, 100.0))
        // 저장 전 새 각에는 companion ID가 없어 숫자만 두고 기호는 붙이지 않는다.
        assertEquals(listOf(1 to HanbaeMark.Faster, inserted to null), withChange.tempoCues.map { it.start to it.mark })
        assertEquals(listOf(listOf(1, 2), listOf(3, 1)), withChange.plan().positions("tempos"))
    }

    @Test fun slurGroupsAndContinuationsKeepTheirIdsWhenAGakIsAppended() {
        val planned = state(3, step(9, 120.0))
        val expression = requireNotNull(planned.draft.tempoExpressions)
        val withExtras = planned.draft.withTempoExpressions(JsonObject(expression +
            ("slurGroups" to buildJsonArray { addJsonObject { put("basis", "t"); putJsonArray("eventIds") { add("c2-j0-r0-u0"); add("c2-j1-r0-u0") } } }) +
            ("gakContinuations" to buildJsonArray {
                addJsonObject { put("gak", 2); put("basis", "t") }
                addJsonObject { put("gak", 3); put("basis", "t") }
            })), planned.draft.tempoReferenceBpm ?: 80)
        val added = planned.updateTempoSettings(withExtras).copy(selected = 0).addColumn()
        val exported = requireNotNull(added.draft.exportExpressions(null, 80))
        assertEquals(listOf("c2-j0-r0-u0", "c2-j1-r0-u0"), exported.getValue("slurGroups").jsonArray.single().jsonObject
            .getValue("eventIds").jsonArray.map { it.jsonPrimitive.content })
        // 새 각은 맨 끝에 붙어 기존 각 경계의 지속은 그대로다.
        assertEquals(listOf(2, 3), exported.getValue("gakContinuations").jsonArray.map { it.jsonObject.getValue("gak").jsonPrimitive.int })
    }
}
