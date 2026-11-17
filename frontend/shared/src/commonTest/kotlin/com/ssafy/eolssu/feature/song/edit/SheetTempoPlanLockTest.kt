package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

/** 구간 빠르기를 모두 지운 뒤 빈 빠르기 계획만 남아도 정간·장단 도구를 막지 않는다(S15P31E102-1080). */
class SheetTempoPlanLockTest {
    private fun state() = SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("잠금", 90, null))

    @Test fun deletingTheLastTempoChangeUnlocksCellTools() {
        val added = state().updateTempoSettings(state().draft.withTempoChange(SheetTempoChange(1, null, 120.0)))
        assertTrue(added.hasTempoPlan)
        // 나누기·합치기는 S15P31E102-1091부터 허용하고 각 추가·삭제만 막는다.
        assertEquals(SheetEditMessage.TempoPlanBeforeReshape, added.deleteColumn().message)
        assertNotEquals(SheetEditMessage.TempoPlanBeforeReshape, added.split().message)

        val removed = added.updateTempoSettings(added.draft.withoutTempoChange(1))
        assertTrue(removed.draft.tempoExpressions?.containsKey("performancePlan") == true)
        assertFalse(removed.hasTempoPlan)
        assertNotEquals(SheetEditMessage.TempoPlanBeforeReshape, removed.deleteColumn().message)
    }

    @Test fun onlyPositionedTempoOrRepeatEntriesCountAsAPlan() {
        fun plan(tempos: Int, repeats: Int) = buildJsonObject {
            put("performancePlan", buildJsonObject {
                put("tempos", JsonArray(List(tempos) { buildJsonObject { put("gak", 1); put("cell", 1); put("bpm", 90) } }))
                put("repeats", JsonArray(List(repeats) { buildJsonObject { put("start_gak", 1); put("end_gak", 1); put("times", 2) } }))
            })
        }
        assertFalse(null.hasPositionedTempoPlan())
        assertFalse(buildJsonObject { put("placements", JsonArray(emptyList())) }.hasPositionedTempoPlan())
        assertFalse(plan(0, 0).hasPositionedTempoPlan())
        assertTrue(plan(1, 0).hasPositionedTempoPlan())
        assertTrue(plan(0, 1).hasPositionedTempoPlan())
    }
}
