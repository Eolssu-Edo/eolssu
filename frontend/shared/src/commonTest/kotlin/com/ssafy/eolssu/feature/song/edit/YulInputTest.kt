package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals

class YulInputTest {
    @Test
    fun eachRegisterIsSelectedDirectlyWithoutEditingTheScore() {
        val initial = SheetEditUiState.initial(EditSource.Mine("1"), SheetEditSamples.draftFor(EditSource.Mine("1"), emptyList()))
        assertEquals(YulRegister.Base, initial.yulRegister)
        for (before in YulRegister.entries) for (chosen in YulRegister.entries) {
            val start = initial.copy(yulRegister = before)
            val selected = start.selectRegister(chosen)
            assertEquals(chosen, selected.yulRegister)
            assertEquals(selected, selected.selectRegister(chosen), "같은 버튼을 다시 눌러도 음역을 순환하지 않는다")
            assertEquals(start.draft, selected.draft, "음역 선택만으로 악보가 바뀌지 않는다")
            assertEquals(start.selected, selected.selected)
            assertEquals(start.canUndo, selected.canUndo)
        }
    }

    @Test
    fun hangulTextBecomesBaseYulAndOctave() {
        assertEquals(YulTextInput.Pitch(Yul.Hwangjong, 0), parseYulText("황"))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, 0), parseYulText(" 태 "))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, 1), parseYulText("청태"))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, 2), parseYulText("중청태"))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, -1), parseYulText("탁태"))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, -1), parseYulText("배태"), "공통 악보 표기(배)도 읽는다")
        assertEquals(YulTextInput.Pitch(Yul.Taeju, -2), parseYulText("배탁태"))
        assertEquals(YulTextInput.Pitch(Yul.Taeju, -2), parseYulText("하배태"))
        assertEquals(YulTextInput.Pitch(Yul.Jungnyeo, 0), parseYulText("중"), "'중'은 율명(중려)이다")
        assertEquals(YulTextInput.Pitch(Yul.Jungnyeo, 1), parseYulText("청중"))
        assertEquals(YulTextInput.Pitch(Yul.Jungnyeo, 2), parseYulText("중청중"))
        assertEquals(YulTextInput.Pitch(Yul.Namnyeo, 0), parseYulText("남려"))
        assertEquals(YulTextInput.Hold, parseYulText("지속"))
        assertEquals(YulTextInput.Rest, parseYulText("쉼"))
    }

    @Test
    fun unfinishedOrUnknownTextIsNotConverted() {
        assertEquals(YulTextInput.Incomplete, parseYulText(""))
        assertEquals(YulTextInput.Incomplete, parseYulText("청"))
        assertEquals(YulTextInput.Incomplete, parseYulText("중청"))
        assertEquals(YulTextInput.Incomplete, parseYulText("배탁"))
        assertEquals(YulTextInput.Unknown, parseYulText("ㅊ"), "조합 중인 낱자도 다른 율명으로 바꾸지 않는다")
        assertEquals(YulTextInput.Unknown, parseYulText("청청태"))
        assertEquals(YulTextInput.Unknown, parseYulText("도"))
    }

    @Test
    fun registeredPitchListDecidesWhatCanBeWritten() {
        val available = setOf("태" to 0, "태" to 1, "태" to 2, "임" to -1)
        assertEquals(true, isAvailablePitch(available, Yul.Taeju, 2))
        assertEquals(false, isAvailablePitch(available, Yul.Taeju, -2), "등록되지 않은 음은 막는다")
        assertEquals(true, isAvailablePitch(null, Yul.Taeju, -2), "목록을 받지 못했으면 서버 저장 때 확인한다")
    }

    @Test
    fun registerButtonWritesTheRegisteredNote() {
        val start = SheetEditUiState.initial(EditSource.Mine("1"), SheetEditSamples.draftFor(EditSource.Mine("1"), emptyList()))
            .copy(availablePitches = setOf("태" to 1))
        val byButton = start.selectRegister(YulRegister.Cheong).putYul(Yul.Taeju)
        assertEquals(byButton.draft, start.putYul(Yul.Taeju, 1).draft)
        assertEquals(ScoreNote.Pitch(Yul.CheongTaeju), byButton.draft.cells[0].single())
        assertEquals(YulRegister.Base, byButton.yulRegister, "율명을 적으면 음역은 기본으로 돌아간다")
        assertEquals(start.draft, start.putYul(Yul.Hwangjong, 0).draft, "등록되지 않은 음은 적지 않는다")
    }

    @Test
    fun choosingASubNoteEditsOnlyThatSubNoteThenMovesOn() {
        val base = SheetEditUiState.initial(EditSource.Mine("1"), SheetEditSamples.draftFor(EditSource.Mine("1"), emptyList()))
        val split = base.select(0).erase().split().put(ScoreNote.Pitch(Yul.Hwangjong)).put(ScoreNote.Pitch(Yul.Taeju))
        val index = split.selected - 1
        assertEquals(listOf<ScoreNote>(ScoreNote.Pitch(Yul.Hwangjong), ScoreNote.Pitch(Yul.Taeju)), split.draft.cells[index])

        val picked = split.selectUnit(index, 0)
        assertEquals(0, picked.selectedSub)
        val edited = picked.put(ScoreNote.Rest)
        assertEquals(listOf<ScoreNote>(ScoreNote.Rest, ScoreNote.Pitch(Yul.Taeju)), edited.draft.cells[index], "고른 소박만 바뀐다")
        assertEquals(index to 1, edited.selected to edited.selectedSub, "같은 정간의 다음 소박으로 간다")
        val last = edited.put(ScoreNote.Hold)
        assertEquals(index + 1, last.selected, "마지막 소박을 적으면 다음 정간으로 간다")
        assertEquals(null, last.selectedSub)

        assertEquals(null, base.selectUnit(1, 0).selectedSub, "나누지 않은 정간은 정간 전체를 고른다")
        assertEquals(listOf(ScoreNote.Blank, ScoreNote.Pitch(Yul.Taeju)), split.selectUnit(index, 0).erase().draft.cells[index])
    }
}
