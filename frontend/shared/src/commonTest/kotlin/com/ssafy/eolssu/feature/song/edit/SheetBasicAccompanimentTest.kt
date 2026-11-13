package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.BasicAccompanimentPreset
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetBasicAccompanimentTest {
    private fun state(): SheetEditUiState {
        val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val metadata = buildJsonObject {
            put("schema", "sheet-authoring/v1")
            put("source", buildJsonObject { put("scoreSha256", "a".repeat(64)) })
            put("adaptation", buildJsonObject { put("mode", "none") })
        }
        return SheetEditUiState(EditSource.Mine("31"), score.editingDraft("곡", 60, null).copy(authoring = metadata))
    }

    @Test fun presetAndPickupStartSaveWithoutChangingTheWrittenScoreAndUndoRestoresBoth() {
        val initial = state()
        val chosen = initial.selectBasicAccompaniment(BasicAccompanimentPreset.FiveTwoThree, 1)
        assertEquals(4L, assertNotNull(chosen.draft.basicAccompaniment).startTick)
        assertEquals(initial.draft.toCommonScore(), chosen.draft.toCommonScore())
        assertNull(chosen.draft.jangdanId)
        assertFalse(chosen.withJangdan)
        assertTrue(chosen.canUndo)
        assertEquals(initial.draft.authoring?.get("source"), chosen.draft.authoring?.get("source"))
        assertEquals(initial.draft.authoring?.get("adaptation"), chosen.draft.authoring?.get("adaptation"))
        assertEquals(initial.draft, chosen.undo().draft)
        assertEquals(chosen.draft, chosen.undo().redo().draft)
        val clear = chosen.clearJangdan()
        assertNull(clear.draft.basicAccompaniment)
        assertEquals(JsonNull, clear.draft.authoring?.get("accompaniment"))
        assertEquals(chosen.draft, clear.undo().draft)
    }

    @Test fun unsupportedSourceUnitOrMissingSourceReferenceCannotSelectABasicPattern() {
        val original = state()
        val source = original.draft.original!!
        val unknown = original.copy(draft = original.draft.copy(original = source.copy(music = source.music.copy(bpmUnit = "JEONGGAN"))))
        assertEquals(unknown, unknown.selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 0))
        val missing = original.copy(draft = original.draft.copy(authoring = null))
        assertEquals(missing, missing.selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 0))
    }
}
