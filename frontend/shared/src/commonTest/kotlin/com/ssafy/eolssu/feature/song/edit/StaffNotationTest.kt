package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffNotationTest {

    @Test
    fun hwangEFlat4SpellsWireframePitchesWithFlats() {
        // 와이어프레임 EDIT-03 SOL 표: 黃 미♭(E♭4) · 太 파(F4) · 仲 라♭(A♭4) · 林 시♭(B♭4) · 南 도(C5) · 無 레♭(D♭5) · 潢 미♭(E♭5) · 汰 파(F5)
        val expected = listOf("미♭(E♭4)", "파(F4)", "라♭(A♭4)", "시♭(B♭4)", "도(C5)", "레♭(D♭5)", "미♭(E♭5)", "파(F5)")
        assertEquals(expected, StaffPitchOrder.map { staffPitch(it, HwangPitch.Eb4).solfegeName })
        // 오선 위치: 맨 아래 줄 E4 기준 黃(E♭4)은 같은 줄, 汰(F5)는 맨 위 줄(8칸 위).
        assertEquals(0, staffPitch(Yul.Hwangjong, HwangPitch.Eb4).stepsAboveBottomLine)
        assertEquals(8, staffPitch(Yul.CheongTaeju, HwangPitch.Eb4).stepsAboveBottomLine)
    }

    @Test
    fun otherHwangMovesEveryYulTogether() {
        assertEquals("D4", staffPitch(Yul.Hwangjong, HwangPitch.D4).name)
        assertEquals("A4", staffPitch(Yul.Imjong, HwangPitch.D4).name)
        assertEquals(-1, staffPitch(Yul.Hwangjong, HwangPitch.D4).stepsAboveBottomLine, "D4는 맨 아래 줄 바로 아래 칸")
    }

    @Test
    fun holdsExtendNotesAndSplitCellsBecomeShortNotes() {
        val draft = SheetDraft(
            "예시", Jangdan("세마치", listOf(3, 3, 3), beats = 3, subdivision = 3), 60,
            cells = listOf("黃", "ㅡ", "太", "黃", "ㅡ", "太仲", "仲", "ㅡ", "ㅡ").map { c -> c.map { ScoreNote.fromSymbol(it.toString())!! } },
        )
        val notes = staffNotes(draft)
        assertEquals(listOf(2.0, 1.0, 2.0, 0.5, 0.5, 3.0), notes.map { it.length })
        assertEquals(listOf(2, 1, 2, 1, 1, 3), notes.map { it.span })
        assertEquals(StaffNoteKey(5, 1), notes[4].key)
        assertEquals(StaffDuration.DottedQuarter, staffDuration(notes.last().length))
        assertEquals(StaffDuration.Sixteenth, staffDuration(notes[3].length))
    }

    @Test
    fun holdAtNewSystemStartsTiedNoteAndBlankIsSkipped() {
        val jangdan = Jangdan("한 정간", listOf(1), beats = 1, subdivision = 1)
        val draft = SheetDraft("예시", jangdan, 60, listOf(listOf(ScoreNote.Pitch(Yul.Imjong)), emptyList(), listOf(ScoreNote.Hold), listOf(ScoreNote.Rest)))
        // 한 줄 = 2정간. 정간 2의 지속은 새 줄이라 같은 높이의 새 음표가 된다.
        val notes = staffNotes(draft)
        assertEquals(listOf(0, 2, 3), notes.map { it.key.start })
        assertEquals(Yul.Imjong, notes[1].yul)
        assertNull(notes[2].yul)
        assertEquals(notes[1], notes.pick(StaffNoteKey(2, 0)))
        assertEquals(notes[0], notes.pick(StaffNoteKey(1, 0)), "음표가 없는 정간이면 첫 음표")
    }

    @Test
    fun staffShowsTwoSystemsAroundSelectedCell() {
        // 세마치(한 각 9정간): 한 줄 18정간, 8각이면 4줄.
        assertEquals(0..1, visibleStaffSystems(cell = 3, cellsPerSystem = 18, systemCount = 4))
        assertEquals(0..1, visibleStaffSystems(cell = 35, cellsPerSystem = 18, systemCount = 4))
        assertEquals(2..3, visibleStaffSystems(cell = 36, cellsPerSystem = 18, systemCount = 4))
        assertEquals(2..2, visibleStaffSystems(cell = 40, cellsPerSystem = 18, systemCount = 3), "마지막 묶음은 남은 줄만")
        assertEquals(2..2, visibleStaffSystems(cell = 999, cellsPerSystem = 18, systemCount = 3))
        assertTrue(visibleStaffSystems(cell = 0, cellsPerSystem = 18, systemCount = 0).isEmpty())
    }
}
