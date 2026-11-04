package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetEditStateTest {
    private val importSource = EditSource.Import(ImportKind.Jeongganbo, HwangPitch.Eb4, pendingChecks = 2)
    private fun importState() = SheetEditUiState.initial(importSource, SheetEditSamples.draftFor(importSource, emptyList()))

    @Test
    fun importStartsLikeWireframeWithEmptyCellsFromFifthColumn() {
        val state = importState()
        assertEquals(54, state.draft.cells.size)
        assertEquals(40, state.selected, "5각 5정간을 고른 채로 시작한다")
        assertEquals((40 until 54).toList(), state.draft.emptyCells)
        assertEquals(2, state.pendingChecks)
        assertFalse(state.savedOnce)
    }

    @Test
    fun saveWithEmptyCellsOpensWarningAndDraftIsAlwaysAllowed() {
        val warned = importState().requestSave()
        assertTrue(warned.emptyWarning)
        assertNull(warned.message)
        assertFalse(warned.savedOnce)

        val drafted = warned.saveDraft()
        assertFalse(drafted.emptyWarning)
        assertEquals(SheetEditMessage.Draft, drafted.message)
    }

    @Test
    fun saveWithoutEmptyCellsSavesAndAudioUsesLastSavedVersion() {
        val copy = EditSource.CopySong("arirang")
        val state = SheetEditUiState.initial(copy, SheetEditSamples.draftFor(copy, emptyList()))
        assertEquals("아리랑 (내 편곡)", state.draft.title, "제공 곡 복사본은 '(내 편곡)'을 붙인다")
        assertEquals(SheetEditMessage.AudioNeedsSave, state.requestAudio().message, "저장한 적이 없으면 음원을 받을 수 없다")
        val saved = state.requestSave()
        assertEquals(SheetEditMessage.Saved, saved.message)
        assertTrue(saved.savedOnce)
        assertEquals(SheetEditMessage.Audio, saved.copy(message = null).requestAudio().message)
    }

    @Test
    fun mySheetIsAlreadySaved() {
        val sheet = MySheet("s1", MySheetOrigin.CopiedSong, "아리랑 (내 편곡)", updatedAt = KoreanDateTime(2026, 10, 19, 21, 0), songId = "arirang")
        val source = EditSource.Mine("s1")
        val state = SheetEditUiState.initial(source, SheetEditSamples.draftFor(source, listOf(sheet)))
        assertEquals("아리랑 (내 편곡)", state.draft.title)
        assertTrue(state.savedOnce)
        assertEquals(0, state.selected)
    }

    @Test
    fun fillEmptySelectsFirstEmptyCellInJeongganboYulInput() {
        val state = importState().copy(view = EditView.Staff, tab = EditTab.Cell, selected = 3).requestSave().fillEmpty()
        assertFalse(state.emptyWarning)
        assertEquals(40, state.selected)
        assertEquals(EditView.Jeongganbo, state.view)
        assertEquals(EditTab.Yul, state.tab)
    }

    @Test
    fun putAdvancesAndUndoRedoRestoreDraft() {
        val start = importState()
        val typed = start.put(ScoreNote.Pitch(Yul.Namnyeo))
        assertEquals(41, typed.selected)
        assertEquals(listOf<ScoreNote>(ScoreNote.Pitch(Yul.Namnyeo)), typed.draft.cells[40])
        assertTrue(typed.canUndo)

        val undone = typed.undo()
        assertEquals(start.draft, undone.draft)
        assertTrue(undone.canRedo)
        assertEquals(typed.draft, undone.redo().draft)
        assertFalse(undone.put(ScoreNote.Rest).canRedo, "새로 고치면 다시 실행할 기록을 지운다")
    }

    @Test
    fun historyKeepsRecentEditsOnly() {
        var state = importState()
        repeat(SheetEditUiState.HISTORY_LIMIT + 5) { state = state.select(40).put(if (it % 2 == 0) ScoreNote.Rest else ScoreNote.Hold) }
        assertEquals(SheetEditUiState.HISTORY_LIMIT, state.past.size)
    }

    @Test
    fun selectionStaysInsideScore() {
        val state = importState()
        assertEquals(0, state.select(0).moveSelection(-1).selected)
        assertEquals(53, state.select(53).moveSelection(1).selected)
        assertEquals(53, state.select(53).put(ScoreNote.Rest).selected)
    }

    @Test
    fun addAndDeleteColumnMoveSelection() {
        val added = importState().select(4).addColumn()
        assertEquals(7, added.draft.columnCount)
        assertEquals(9, added.selected, "새 각의 첫 정간")
        val deleted = added.select(60).deleteColumn()
        assertEquals(6, deleted.draft.columnCount)
        assertEquals(45, deleted.selected, "마지막 각을 지우면 새 마지막 각 첫 정간")
    }

    @Test
    fun staffNoteSelectionFollowsJeongganboAndShiftChangesPitch() {
        val staffSource = EditSource.Import(ImportKind.Staff, HwangPitch.Eb4, 0)
        val state = SheetEditUiState.initial(staffSource, SheetEditSamples.draftFor(staffSource, emptyList()), EditView.Staff)
        assertEquals(EditView.Staff, state.view)
        val picked = state.selectStaffNote(StaffNoteKey(2, 0))
        assertEquals(2, picked.selected)
        val raised = picked.shiftStaffNote(1)
        assertEquals(listOf<ScoreNote>(ScoreNote.Pitch(Yul.Jungnyeo)), raised.draft.cells[2], "太 → 仲")
        assertEquals(StaffNoteKey(2, 0), raised.staffKey)
    }

    @Test
    fun importResultChoosingMovesToNextOpenCheck() {
        val start = ImportResultUiState(ImportKind.Jeongganbo, HwangPitch.Eb4, SheetEditSamples.checks(ImportKind.Jeongganbo))
        assertEquals(22, start.selected)
        val first = start.choose("太·仲")
        assertEquals(30, first.selected)
        assertEquals(listOf<ScoreNote>(ScoreNote.Pitch(Yul.Taeju), ScoreNote.Pitch(Yul.Jungnyeo)), first.draft().cells[22])
        assertEquals(first, first.choose("없는 후보"), "후보에 없는 값은 무시한다")
        val done = first.choose("南").choose("黃")
        assertTrue(done.allDone)
        assertEquals(0, done.openChecks.size)
        assertEquals(22, done.select(22).selected, "확인한 정간도 다시 볼 수 있다")
        assertEquals(done, done.select(0), "확인 정간이 아니면 고르지 않는다")
    }

    @Test
    fun editPickSearchesSongsAndTogglesSelection() {
        val songs = listOf(EditPickSong("arirang", "아리랑", SongLevel.Intro, "세마치"), EditPickSong("doraji", "도라지타령", SongLevel.Intro, "세마치"))
        val state = EditPickUiState(tab = EditPickTab.Songs, songs = songs)
        assertEquals(listOf("도라지타령"), state.copy(query = " 도라지 ").visibleSongs.map { it.title })
        assertFalse(state.canSelect)
        val picked = state.toggle(EditPickTarget.Song("arirang"))
        assertEquals("아리랑", picked.pickedTitle)
        assertTrue(picked.canSelect)
        assertNull(picked.toggle(EditPickTarget.Song("arirang")).picked, "같은 줄을 다시 누르면 고름을 푼다")
    }
}
