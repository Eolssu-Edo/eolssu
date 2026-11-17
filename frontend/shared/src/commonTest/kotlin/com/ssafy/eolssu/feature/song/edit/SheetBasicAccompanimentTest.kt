package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.BasicAccompanimentPreset
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.basicAccompaniment
import com.ssafy.eolssu.data.song.CommonScoreData
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

    @Test fun deletingTheFirstStrongColumnClearsStoredMetadataAndUndoRestoresIt() {
        val before = state().selectBasicAccompaniment(BasicAccompanimentPreset.FiveTwoThree, 2).select(2)
        val after = before.deleteColumn()
        assertNull(after.draft.basicAccompaniment)
        assertEquals(JsonNull, after.draft.authoring?.get("accompaniment"), "삭제된 시작 위치를 원시 메타데이터에도 남기지 않는다")
        assertEquals(SheetEditMessage.BasicAccompanimentCleared, after.message)
        assertTrue(after.message!!.shownInEditor())
        val encoded = after.draft.toCommonScore()
        encoded.toScore()
        assertNull(after.draft.authoring!!.basicAccompaniment(encoded))
        assertEquals(before.draft.authoring?.get("source"), after.draft.authoring?.get("source"))
        assertEquals(before.draft, after.undo().draft)
        assertEquals(after.draft, after.undo().redo().draft)
    }

    @Test fun appendingAGakKeepsTheFirstStrongCellTickAndUndoKeepsTheAnchor() {
        val before = state().selectBasicAccompaniment(BasicAccompanimentPreset.FiveTwoThree, 2).select(0)
        val after = before.addColumn()
        val encoded = after.draft.toCommonScore()
        assertEquals(before.draft.authoring!!.basicAccompaniment(before.draft.toCommonScore())?.startTick,
            after.draft.authoring!!.basicAccompaniment(encoded)?.startTick)
        assertEquals(before.draft, after.undo().draft)
        assertEquals(after.draft, after.undo().redo().draft)
    }

    @Test fun deletingBeforeTheFirstStrongCellMovesItIntoTheRemainingScoreAndCanBeSaved() {
        val before = state().selectBasicAccompaniment(BasicAccompanimentPreset.FiveTwoThree, 2).select(0)
        val after = before.deleteColumn()
        val encoded = after.draft.toCommonScore()
        encoded.toScore()
        assertEquals(0L, after.draft.authoring!!.basicAccompaniment(encoded)?.startTick)
        assertEquals(before.draft, after.undo().draft)
        assertEquals(after.draft, after.undo().redo().draft)
        assertEquals(before.draft.authoring?.get("adaptation"), after.draft.authoring?.get("adaptation"))
    }

    @Test fun removingASplitColumnKeepsASurvivingAnchorWhenTheEncodedTickScaleChanges() {
        val initial = state()
        val split = initial.copy(draft = initial.draft.split(2).split(2))
        val before = split.selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 1).select(2)
        assertEquals(12, before.draft.toCommonScore().music.ticksPerJeonggan)
        assertEquals(12L, before.draft.basicAccompaniment?.startTick)
        val after = before.deleteColumn()
        val encoded = after.draft.toCommonScore()
        assertEquals(4, encoded.music.ticksPerJeonggan)
        assertEquals(4L, after.draft.authoring!!.basicAccompaniment(encoded)?.startTick)
        assertEquals(before.draft, after.undo().draft)
    }

    @Test fun splittingAndMergingAnEarlierCellRescalesTheAnchorWithoutMovingIt() {
        val base = smallScoreData()
        val score = base.copy(music = base.music.copy(jangdan = "", bpmUnit = "QUARTER", ticksPerBeat = 1, ticksPerJeonggan = 1),
            columns = (0..1).map { column -> CommonScoreData.Column(column, listOf(CommonScoreData.Cell("c$column-j0", 0,
                listOf(CommonScoreData.Unit(0, 0, "황", "c$column-j0-r0-u0"))))) },
            events = (0..1).map { column -> CommonScoreData.Event("c$column-j0-r0-u0", "NOTE", "황", 0,
                column.toLong(), 1, "c$column-j0", false, emptyList()) })
        val initial = state().copy(draft = score.editingDraft("곡", 60, null).copy(authoring = state().draft.authoring))
        val before = initial.selectBasicAccompaniment(BasicAccompanimentPreset.TwoQuarter, 1).select(0)
        assertEquals(1L, before.draft.basicAccompaniment?.startTick)
        val split = before.split()
        assertEquals(2, split.draft.toCommonScore().music.ticksPerJeonggan)
        assertEquals(2L, split.draft.authoring!!.basicAccompaniment(split.draft.toCommonScore())?.startTick)
        assertEquals(1, split.draft.basicAccompanimentStartCell, "분할 후 다시 여는 설정은 두 번째 정간을 표시한다")
        val merged = split.merge()
        assertEquals(1, merged.draft.toCommonScore().music.ticksPerJeonggan)
        assertEquals(1L, merged.draft.basicAccompaniment?.startTick)
        assertEquals(1, merged.draft.basicAccompanimentStartCell)
        assertEquals(before.draft, split.undo().draft)
        assertEquals(split.draft, merged.undo().draft)
        val erased = split.erase()
        assertEquals(1, erased.draft.toCommonScore().music.ticksPerJeonggan)
        assertEquals(1L, erased.draft.basicAccompaniment?.startTick)
    }
}
