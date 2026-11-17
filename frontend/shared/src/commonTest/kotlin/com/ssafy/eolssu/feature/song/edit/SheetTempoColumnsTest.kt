package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.commonSourceSha256
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

/** 구간 빠르기·반복이 있는 악보의 각 추가·삭제(S15P31E102-1095, 스펙 2단계). 한 각은 4정간이다. */
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

    private fun state(columns: Int, vararg changes: SheetTempoChange): SheetEditUiState {
        val base = SheetEditUiState(EditSource.Mine("31"), score(columns).editingDraft("각 편집", 80, null))
        val planned = changes.fold(base.draft) { draft, change -> draft.withTempoChange(change).also { assertNotEquals(draft, it, "$change") } }
        return base.updateTempoSettings(planned).also { assertTrue(it.hasTempoPlan) }
    }

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

    @Test fun insertingAGakShiftsLaterChangesMarksAndTheirCompanionIds() {
        val planned = state(4, SheetTempoChange(4, null, 120.0), SheetTempoChange(9, HanbaeMark.Slower, 60.0, 11))
        val added = planned.copy(selected = 0).addColumn()
        assertNull(added.message)
        assertEquals(5, added.draft.columnCount)
        assertEquals(listOf(8 to null, 13 to HanbaeMark.Slower), added.draft.tempoChanges.map { it.cell to it.mark })
        assertEquals(15, added.draft.tempoChanges[1].endCell)
        // 바로 변경 (3,1), 조금 느리게 (4,2)와 끝 다음 정간 복귀 항목 (4,4)
        assertEquals(listOf(listOf(3, 1), listOf(4, 2), listOf(4, 4)), added.draft.plan().positions("tempos"))
        val exported = requireNotNull(added.draft.exportExpressions(null, 80))
        val score = added.draft.toCommonScore()
        assertEquals(commonSourceSha256(Json.encodeToJsonElement(score)), exported.getValue("sourceSha256").jsonPrimitive.content)
        assertEquals(exported.getValue("sourceSha256"), exported.getValue("performancePlan").jsonObject.getValue("input_sha256"))
        val marks = exported.getValue("placements").jsonArray.map { it.jsonObject }
            .filter { p -> p.getValue("symbols").jsonArray.any { HanbaeMark.fromSymbol(it.jsonPrimitive.content) != null } }
        assertEquals(listOf("c3-j1" to "c3-j1-r0-u0"), marks.map { it.getValue("cellId").jsonPrimitive.content to it.getValue("eventId").jsonPrimitive.content })
        assertEquals(listOf("조금느리게"), score.events.first { it.id == "c3-j1-r0-u0" }.sigimsae)
        assertEquals(planned.draft, added.undo().draft)
    }

    @Test fun aGakInsertedRightAfterARangeContinuesIt() {
        val planned = state(3, SheetTempoChange(1, null, 120.0, 4, startBpm = 90.0)).let { it.updateTempoSettings(it.draft.withRepeats(1 to 1)) }
        val added = planned.copy(selected = 0).addColumn()
        // 점진 끝 (2,1)은 다음 각 시작이라 새 각도 변화 구간에 든다. 반복은 끝 각을 포함하므로 새 각은 반복 밖이다.
        assertEquals(8, added.draft.tempoChanges.single().endCell)
        assertEquals(listOf(listOf(1, 2, 3, 1)), added.draft.plan().positions("tempos"))
        assertEquals(listOf(listOf(1, 1)), added.draft.plan().positions("repeats"))
    }

    @Test fun gakWithAChangeStartARangeOrARepeatCannotBeDeleted() {
        val planned = state(5, SheetTempoChange(5, HanbaeMark.GraduallyFaster, 120.0, 12))
            .let { it.updateTempoSettings(it.draft.withRepeats(5 to 5)) }
        // 점점 구간은 정간 5~11(2·3각), 반복은 5각이다.
        for (column in listOf(1, 2, 4)) {
            val tried = planned.copy(selected = planned.draft.indexOf(column, 0)).deleteColumn()
            assertEquals(SheetEditMessage.TempoPlanBeforeReshape, tried.message, "$column")
            assertEquals(planned.draft, tried.draft)
        }
        assertNull(planned.copy(selected = planned.draft.indexOf(3, 0)).deleteColumn().message)
        val first = planned.copy(selected = 0).deleteColumn()
        assertNull(first.message)
        assertEquals(listOf(1), first.draft.tempoChanges.map { it.cell })
        assertEquals(8, first.draft.tempoChanges.single().endCell)
        assertEquals(listOf(listOf(4, 4)), first.draft.plan().positions("repeats"))
    }

    @Test fun deletingAGakThatOnlyContinuesATempoShiftsLaterPositions() {
        val planned = state(4, SheetTempoChange(1, null, 120.0), SheetTempoChange(13, HanbaeMark.Faster, 140.0, 14))
        val deleted = planned.copy(selected = planned.draft.indexOf(1, 0)).deleteColumn()
        assertNull(deleted.message)
        assertEquals(listOf(1 to null, 9 to HanbaeMark.Faster), deleted.draft.tempoChanges.map { it.cell to it.mark })
        assertEquals(listOf(listOf(1, 2), listOf(3, 2), listOf(3, 3)), deleted.draft.plan().positions("tempos"))
        val marks = requireNotNull(deleted.draft.exportExpressions(null, 80)).getValue("placements").jsonArray.map { it.jsonObject }
            .filter { p -> p.getValue("symbols").jsonArray.any { HanbaeMark.fromSymbol(it.jsonPrimitive.content) != null } }
        assertEquals(listOf("c2-j1"), marks.map { it.getValue("cellId").jsonPrimitive.content })
    }

    @Test fun theReturnOfARangeEndingBeforeADeletedGakMovesToTheNextGak() {
        val planned = state(3, SheetTempoChange(1, HanbaeMark.Slower, 60.0, 4))
        assertEquals(listOf(listOf(1, 2), listOf(2, 1)), planned.draft.plan().positions("tempos"))
        val deleted = planned.copy(selected = planned.draft.indexOf(1, 0)).deleteColumn()
        assertNull(deleted.message)
        assertEquals(listOf(listOf(1, 2), listOf(2, 1)), deleted.draft.plan().positions("tempos"))
        assertEquals(4, deleted.draft.tempoChanges.single().endCell)
        // 마지막 각을 지우면 복귀할 곳이 없어 구간이 악보 끝까지 간다.
        val last = planned.copy(selected = planned.draft.indexOf(2, 0)).deleteColumn()
            .let { it.copy(selected = it.draft.indexOf(1, 0)).deleteColumn() }
        assertEquals(listOf(listOf(1, 2)), last.draft.plan().positions("tempos"))
    }

    @Test fun anInsertedGakTakesNumericChangesButMarksOnlyAfterSaving() {
        val added = state(2, SheetTempoChange(1, null, 120.0)).copy(selected = 0).addColumn()
        val inserted = added.draft.indexOf(1, 0)
        assertNotNull(added.draft.tempoChangeError(SheetTempoChange(inserted, HanbaeMark.Slower, 60.0, inserted + 1)))
        assertNull(added.draft.tempoChangeError(SheetTempoChange(inserted, null, 100.0)))
    }

    @Test fun slurGroupsAndContinuationsFollowTheRenumberedIds() {
        val planned = state(3, SheetTempoChange(9, null, 120.0))
        val expression = requireNotNull(planned.draft.tempoExpressions)
        val withExtras = planned.draft.withTempoExpressions(JsonObject(expression +
            ("slurGroups" to buildJsonArray { addJsonObject { put("basis", "t"); putJsonArray("eventIds") { add("c2-j0-r0-u0"); add("c2-j1-r0-u0") } } }) +
            ("gakContinuations" to buildJsonArray {
                addJsonObject { put("gak", 2); put("basis", "t") }
                addJsonObject { put("gak", 3); put("basis", "t") }
            })), planned.draft.tempoReferenceBpm ?: 80)
        val added = planned.updateTempoSettings(withExtras).copy(selected = 0).addColumn()
        val exported = requireNotNull(added.draft.exportExpressions(null, 80))
        assertEquals(listOf("c3-j0-r0-u0", "c3-j1-r0-u0"), exported.getValue("slurGroups").jsonArray.single().jsonObject
            .getValue("eventIds").jsonArray.map { it.jsonPrimitive.content })
        // 1각 뒤의 새 각 때문에 2각으로 들어오던 지속은 끊기고, 3각 경계는 4각으로 민다.
        assertEquals(listOf(4), exported.getValue("gakContinuations").jsonArray.map { it.jsonObject.getValue("gak").jsonPrimitive.int })
    }
}
