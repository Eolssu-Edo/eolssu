package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.feature.song.edit.SheetJangdanCorrectionTest.Companion.recognized
import com.ssafy.eolssu.feature.song.edit.SheetJangdanCorrectionTest.Companion.sourceMetadata
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.test.*

/** 구간 빠르기·반복이 있어도 칸 경계를 바꾸지 않는 편집은 허용한다(S15P31E102-1091, 스펙 1단계). */
class SheetTempoReshapeTest {
    private val gutgeori66 = CatalogJangdanSummary("2", "굿거리", 66, "DOTTED_QUARTER", 12, 4, 1)
    private val danmori = CatalogJangdanSummary("7", "단모리", 120, "QUARTER", 8, 4, 1)

    private fun state(vararg changes: SheetTempoChange, options: List<CatalogJangdanSummary> = listOf(gutgeori66, danmori)): SheetEditUiState {
        val score = recognized("QUARTER", 12, 36, "중모리")
        val base = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("인식 악보", 80, null).copy(authoring = sourceMetadata(score)),
            availableJangdans = options, jangdanPicker = SheetEditJangdanPicker.Ready(options))
        val planned = changes.fold(base.draft) { draft, change -> draft.withTempoChange(change).also { assertNotEquals(draft, it, "$change") } }
        return base.updateTempoSettings(planned).copy(jangdanPicker = SheetEditJangdanPicker.Ready(options))
    }

    private fun assertNear(expected: Double, actual: Double?) = assertTrue(actual != null && abs(expected - actual) < 1e-6, "$expected != $actual")

    @Test fun splitAndMergeAreAllowedAndKeepThePlan() {
        val planned = state(SheetTempoChange(2, null, 120.0))
        assertTrue(planned.hasTempoPlan)
        val split = planned.copy(selected = 2).split()
        assertNotEquals(SheetEditMessage.TempoPlanBeforeReshape, split.message)
        assertEquals(2, split.draft.cells[2].size)
        assertEquals(planned.draft.tempoChanges, split.draft.tempoChanges)
        val merged = split.merge()
        assertEquals(1, merged.draft.cells[2].size)
        assertEquals(planned.draft.tempoChanges, merged.draft.tempoChanges)
        assertEquals(planned.draft, merged.undo().undo().draft)
        // 각 추가·삭제는 2단계 합의 전이라 계속 막는다.
        assertEquals(SheetEditMessage.TempoPlanBeforeReshape, planned.addColumn().message)
        assertEquals(SheetEditMessage.TempoPlanBeforeReshape, planned.deleteColumn().message)
    }

    @Test fun mergingAwayTheUnitOfAMarkMovesTheMarkToTheFirstUnitOfThatCell() {
        val planned = state(SheetTempoChange(2, HanbaeMark.Slower, 60.0, 3))
        val split = planned.copy(selected = 2).split().draft.let { it.copy(cells = it.cells.toMutableList().also { cells ->
            cells[2] = listOf(ScoreNote.SourcePitch("황", 0), ScoreNote.SourcePitch("태", 0))
        }) }
        // 기호가 둘째 음에 붙은 인식 악보를 흉내 낸다.
        val second = split.toCommonScoreWithoutTempo().columns[0].cells[2].units[1]
        val moved = split.withTempoExpressions(JsonObject(split.tempoExpressions!! + ("placements" to JsonArray(
            split.tempoExpressions!!.getValue("placements").jsonArray.map { placement ->
                val p = placement.jsonObject
                if (p["cellId"]?.jsonPrimitive?.content == "c0-j2") JsonObject(p + mapOf("row" to JsonPrimitive(second.row),
                    "col" to JsonPrimitive(second.col), "eventId" to JsonPrimitive(second.eventId))) else p
            }))), split.tempoReferenceBpm ?: split.bpm)
        val merged = planned.copy(draft = moved, selected = 2).merge()
        assertEquals(1, merged.draft.cells[2].size)
        val exported = merged.draft.exportExpressions(null, "a".repeat(64), 80)!!
        val marks = exported.getValue("placements").jsonArray.map { it.jsonObject }
            .filter { p -> p.getValue("symbols").jsonArray.any { HanbaeMark.fromSymbol(it.jsonPrimitive.content) != null } }
        assertEquals(listOf("c0-j2"), marks.map { it.getValue("cellId").jsonPrimitive.content })
        val first = merged.draft.toCommonScore().columns[0].cells[2].units.single()
        assertEquals(first.eventId, marks.single().getValue("eventId").jsonPrimitive.content)
    }

    @Test fun mergingACellWithAMarkIntoAnEmptyCellIsBlocked() {
        val planned = state(SheetTempoChange(2, HanbaeMark.Slower, 60.0, 3))
        val blankFirst = planned.copy(selected = 2, draft = planned.draft.copy(cells = planned.draft.cells.toMutableList().also {
            it[2] = listOf(ScoreNote.Blank, ScoreNote.SourcePitch("황", 0))
        }))
        val blocked = blankFirst.merge()
        assertEquals(SheetEditMessage.TempoMarkBeforeMerge, blocked.message)
        assertEquals(blankFirst.draft, blocked.draft)
    }

    @Test fun retimingKeepsCellsAndTheRatioToTheBaseTempo() {
        val planned = state(SheetTempoChange(2, null, 120.0), SheetTempoChange(5, HanbaeMark.Original, 80.0))
        assertTrue(planned.draft.requiresJangdanRetiming(gutgeori66))
        assertEquals(listOf(Triple(2, 120.0, 99.0), Triple(5, 80.0, 66.0)),
            planned.draft.jangdanTempoPreview(gutgeori66).map { (c, b, a) -> Triple(c, b, kotlin.math.round(a * 1e6) / 1e6) })
        val changed = planned.selectJangdan(gutgeori66.jangdanId)
        assertEquals("2", changed.draft.jangdanId)
        assertEquals(66, changed.draft.bpm)
        assertEquals("DOTTED_QUARTER", changed.draft.toCommonScore().music.bpmUnit)
        assertTrue(changed.hasTempoPlan)
        assertEquals(planned.draft.tempoChanges.map { it.cell to it.mark }, changed.draft.tempoChanges.map { it.cell to it.mark })
        // 기본 66 대비 1.5배 구간, 본래 속도로는 새 기본값과 같다.
        assertNear(99.0, changed.draft.tempoChanges[0].targetBpm)
        assertNear(66.0, changed.draft.tempoChanges[1].targetBpm)
        assertNull(changed.draft.tempoChangeError(changed.draft.tempoChanges[0]))
        assertEquals(planned.draft, changed.undo().draft)
        assertEquals(changed.draft, changed.undo().redo().draft)
    }

    @Test fun retimedPlanExportsTheSameJeongganSpeedTheServerExpects() {
        val planned = state(SheetTempoChange(2, null, 120.0))
        val before = planned.draft.exportExpressions(null, "a".repeat(64), 80)!!
            .getValue("performancePlan").jsonObject.getValue("tempos").jsonArray.single().jsonObject.getValue("bpm").jsonPrimitive.double
        val changed = planned.selectJangdan(gutgeori66.jangdanId).draft
        // 서버는 저장된 빠르기 80 기준 숫자를 받고 새 기본 66으로 한 번 비례 조정한다(SheetExpressions.prepare).
        val sent = changed.exportExpressions(null, "a".repeat(64), 80)!!
            .getValue("performancePlan").jsonObject.getValue("tempos").jsonArray.single().jsonObject.getValue("bpm").jsonPrimitive.double
        val stored = sent * 66 / 80
        val oldFactor = 12.0 / 36
        val newFactor = changed.toCommonScore().music.let { it.ticksPerBeat.toDouble() / it.ticksPerJeonggan }
        // 저장값은 정간/분(=BPM×ticksPerBeat/ticksPerJeonggan)이므로 기본 정간 속도 대비 배율 1.5가 유지된다.
        assertNear(1.5, stored / (66 * newFactor))
        assertNear(1.5, before / (80 * oldFactor))
    }

    @Test fun retimingThatPushesASectionOutOfRangeIsRefusedWithAMessage() {
        val planned = state(SheetTempoChange(2, null, 200.0), options = listOf(danmori))
        assertTrue(planned.draft.requiresJangdanRetiming(danmori))
        assertTrue(planned.draft.jangdanTempoOutOfRange(danmori))
        val refused = planned.selectJangdan(danmori.jangdanId)
        assertEquals(SheetEditMessage.TempoOutOfRangeAfterJangdan, refused.message)
        assertEquals(planned.draft, refused.draft)
        assertEquals(SheetEditMessage.TempoOutOfRangeAfterJangdan, planned.updateSettings(120, danmori.jangdanId).message)
    }
}
