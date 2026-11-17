package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.key
import kotlin.test.Test
import kotlin.test.assertEquals

/** 정간 전체를 잇는 이음(ㅡ)은 빈칸으로, 쪼갠 정간과 시김새가 붙은 이음은 ㅡ로 그린다(S15P31E102-1069). */
class WholeCellHoldTest {
    private val held = ScoreEvent(0, "c0-j0-r0-u0", 0, 0, 0, 1, ScoreNote.Pitch(Yul.entries.first()), 0.0, 2.0)

    private fun unit(note: ScoreNote) = ScoreDisplayUnit(0, 1, 0, 0, "-", note, held, 1.0, 1.0)

    @Test
    fun wholeCellHoldIsDrawnBlank() {
        assertEquals(ScoreNote.Blank, unit(ScoreNote.Hold).displayedNote(unitsInCell = 1, marks = emptyList()))
    }

    @Test
    fun holdInsideSplitCellKeepsItsMark() {
        assertEquals(ScoreNote.Hold, unit(ScoreNote.Hold).displayedNote(unitsInCell = 2, marks = emptyList()))
    }

    @Test
    fun holdCarryingSigimsaeKeepsItsMark() {
        assertEquals(ScoreNote.Hold, unit(ScoreNote.Hold).displayedNote(unitsInCell = 1, marks = listOf("니레")))
    }

    @Test
    fun editorKeepsSourceHoldAndViewerReadsMarksByUnitKey() {
        val hold = unit(ScoreNote.Hold)
        assertEquals(ScoreNote.Hold, hold.forDisplay(1, emptyMap(), enabled = false).note)
        assertEquals(ScoreNote.Blank, hold.forDisplay(1, emptyMap(), enabled = true).note)
        assertEquals(ScoreNote.Hold, hold.forDisplay(1, mapOf(hold.key to listOf("흘림표")), enabled = true).note)
    }

    @Test
    fun otherNotesAreUnchanged() {
        listOf(ScoreNote.Blank, ScoreNote.Rest, ScoreNote.Pitch(Yul.entries.first())).forEach {
            assertEquals(it, unit(it).displayedNote(unitsInCell = 1, marks = emptyList()))
        }
    }
}
