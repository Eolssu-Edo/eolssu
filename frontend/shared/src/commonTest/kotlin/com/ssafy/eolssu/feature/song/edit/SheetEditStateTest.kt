package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.Instrument
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
    fun backClosesTheAdaptationPanelOnlyWhileItIsOnScreen() {
        val draft = importState().draft
        val opened = importState().copy(tab = EditTab.Adaptation, adaptation = SheetAdaptationUi(baseline = draft))
        assertTrue(opened.adaptationVisible, "펼친 입력판의 음역 맞추기 탭은 뒤로 가기로 먼저 닫는다")
        assertFalse(opened.copy(inputPanelExpanded = false).adaptationVisible, "입력판을 접은 채 연 변환 악보는 패널이 보이지 않아 편집기를 닫는다")
        assertFalse(opened.copy(tab = EditTab.Yul).adaptationVisible)
        assertFalse(opened.copy(adaptation = null).adaptationVisible)
    }

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
    fun saveWithoutEmptyCellsSavesAndAudioNeedsAServerSheet() {
        val copy = EditSource.CopySong("arirang")
        val state = SheetEditUiState.initial(copy, SheetEditSamples.draftFor(copy, emptyList()))
        assertEquals("아리랑 (내 편곡)", state.draft.title, "제공 곡 복사본은 '(내 편곡)'을 붙인다")
        assertEquals(SheetEditMessage.AudioNeedsSave, state.requestAudio().message, "저장한 적이 없으면 음원을 만들 수 없다")
        val saved = state.requestSave()
        assertEquals(SheetEditMessage.Saved, saved.message)
        assertTrue(saved.savedOnce)
        assertNull(saved.serverSheetId, "저장 API를 붙이기 전이라 서버 악보가 없다")
        assertEquals(SheetEditMessage.ComingSoon, saved.copy(message = null).requestAudio().message)
        assertEquals(SheetAudioUi.None, saved.audio)
    }

    @Test
    fun savedServerSheetRequestsAudioOnlyWhenNothingIsInProgress() {
        val source = EditSource.Mine("12")
        val state = SheetEditUiState.initial(source, SheetEditSamples.draftFor(source, emptyList()))
        assertEquals("12", state.serverSheetId)
        val requesting = state.requestAudio()
        assertEquals(SheetAudioUi.Requesting(null), requesting.audio)
        assertNull(requesting.message)
        assertEquals(requesting, requesting.requestAudio(), "요청 중에는 다시 요청하지 않는다")
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
        assertEquals(0, state.select(-1).selected)
        assertEquals(53, state.select(54).selected)
        assertEquals(53, state.select(53).put(ScoreNote.Rest).selected)
    }

    @Test
    fun addAndDeleteColumnMoveSelection() {
        val added = importState().select(4).addColumn()
        assertEquals(7, added.draft.columnCount)
        assertEquals(54, added.selected, "맨 끝에 더한 새 각의 첫 정간")
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
        val state = EditPickUiState(instrument = Instrument.Danso, songsLoad = EditPickLoad.Ready, tab = EditPickTab.Songs, songs = songs)
        assertEquals(listOf("도라지타령"), state.copy(query = " 도라지 ").visibleSongs.map { it.title })
        assertFalse(state.canSelect)
        val picked = state.toggle(EditPickTarget.Song("arirang"))
        assertEquals("아리랑", picked.pickedTitle)
        assertTrue(picked.canSelect)
        assertNull(picked.toggle(EditPickTarget.Song("arirang")).picked, "같은 줄을 다시 누르면 고름을 푼다")
    }

    @Test
    fun editPickShowsNoSheetUntilInstrumentAndClearsOnInstrumentChange() {
        val start = EditPickUiState()
        assertEquals(EditPickLoad.NeedsInstrument, start.load, "악기를 고르기 전에는 악보 목록을 보이지 않는다")
        assertFalse(start.toggle(EditPickTarget.Song("1")).canSelect)
        val loaded = start.chooseInstrument(Instrument.Danso).copy(songsLoad = EditPickLoad.Ready,
            songs = listOf(EditPickSong("1", "아리랑", SongLevel.Intro, "세마치"))).toggle(EditPickTarget.Song("1"))
        assertTrue(loaded.canSelect)
        val changed = loaded.chooseInstrument(Instrument.Sogeum)
        assertEquals(EditPickLoad.Loading, changed.load)
        assertNull(changed.picked, "악기를 바꾸면 고른 악보를 푼다")
        assertTrue(changed.songs.isEmpty() && changed.sheets.isEmpty(), "다른 악기의 악보를 남기지 않는다")
    }
}
