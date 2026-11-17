package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.network.ApiError
import com.ssafy.eolssu.data.song.SheetSigimsaeIssue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S15P31E102-1084: 음원 생성기가 거절한 시김새를 칸 단위로 찾아 바로 고친다. */
class SheetSigimsaeRejectionTest {
    private fun state(): SheetEditUiState {
        val original = com.ssafy.eolssu.data.song.smallScoreData()
        val base = SheetEditUiState.initial(EditSource.Mine("31"), original.editingDraft(original.score.title, 60, null))
        // 첫 율명 소박에 퇴성과 전성을 붙여 두 기호 중 하나만 거절되는 경우를 만든다.
        val cell = target(base)
        return base.copy(draft = base.draft.withSigimsae(cell, listOf(Sigimsae.Toeseong, Sigimsae.Jeonseong)))
    }

    private fun target(state: SheetEditUiState): Int =
        state.draft.cells.indexOfFirst { notes -> notes.firstOrNull()?.isPitch() == true }

    private fun eventIdOf(state: SheetEditUiState, slot: NoteSlot): String =
        state.draft.toCommonScore().columns.flatMap { it.cells }[slot.cell].units[slot.sub].eventId

    @Test fun parsesOnlyTheSigimsaeRejectionDetails() {
        val details = Json.parseToJsonElement(
            """{"reason":"SIGIMSAE_UNRENDERABLE","eventId":"c0-j1-r0-u0","symbols":["전성"],"message":"미지원 기호"}""") as JsonObject
        assertEquals(SheetSigimsaeIssue("c0-j1-r0-u0", listOf("전성"), "미지원 기호"),
            SheetSigimsaeIssue.from(ApiError("INVALID_REQUEST", details)))
        assertNull(SheetSigimsaeIssue.from(ApiError("TEMPO_UNRENDERABLE", details)))
        assertNull(SheetSigimsaeIssue.from(ApiError("INVALID_REQUEST", JsonObject(details - "eventId"))))
    }

    @Test fun movesToTheRejectedCellAndRemovesOnlyTheNamedSigimsae() {
        val base = state()
        val slot = NoteSlot(target(base))
        val rejected = base.copy(sigimsaeIssue = SheetSigimsaeIssue(eventIdOf(base, slot), listOf("전성"), "이유"))
        assertEquals(slot, rejected.sigimsaeIssueSlot)

        val opened = rejected.select(0).openSigimsaeIssue()
        assertEquals(slot.cell, opened.selected)
        assertEquals(EditTab.Sigimsae, opened.tab)

        val fixed = rejected.removeRejectedSigimsae()
        assertNull(fixed.sigimsaeIssue)
        assertEquals(listOf(Sigimsae.Toeseong), fixed.draft.sigimsaeAt(slot.cell, slot.sub))
        assertTrue(fixed.canUndo)
    }

    @Test fun anUnknownEventClearsTheIssueWithoutEditing() {
        val base = state().copy(sigimsaeIssue = SheetSigimsaeIssue("missing", listOf("전성"), null))
        assertNull(base.sigimsaeIssueSlot)
        val fixed = base.removeRejectedSigimsae()
        assertNull(fixed.sigimsaeIssue)
        assertEquals(base.draft, fixed.draft)
        assertNotNull(base.draft.sigimsae.keys.firstOrNull())
    }
}
