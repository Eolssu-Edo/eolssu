package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.*

class SheetDocumentEditingTest {
    @Test fun replacingPitchKeepsUneditedBreathAndUnknownAnnotationAtThatUnit() {
        val source = smallScoreData()
        val original = source.copy(events = source.events.mapIndexed { i, event -> if (i == 0) event.copy(breathAfter = true, sigimsae = listOf("니레", "요성표", "퇴성")) else event })
        val changed = original.editingDraft(original.score.title, 60, null).put(0, ScoreNote.Pitch(Yul.Taeju)).first.toCommonScore()
        assertTrue(changed.events.first().breathAfter)
        assertEquals(listOf("니레", "요성표", "퇴성"), changed.events.first().sigimsae)
        val annotations = original.editingDraft(original.score.title, 60, null).annotations()
        assertEquals(listOf("니레", "요성표", "퇴성"), annotations[ScoreUnitKey("c0-j0", 0, 0)])
        assertEquals(listOf("니레"), annotations[ScoreUnitKey("c0-j0", 0, 1)])
    }
    @Test fun uneditedDocumentPreservesIrregularColumnsAndEveryOriginalField() {
        val original = smallScoreData()
        val draft = original.editingDraft(original.score.title, 60, jangdan = null)
        assertEquals(listOf(2, 1), draft.sizes)
        assertEquals(original, draft.toCommonScore())
        assertEquals(original.toScore().columns, draft.toSong().score.columns)
    }

    @Test fun changingOneCellPreservesOtherSubdivisionsTiesAndProvenance() {
        val original = smallScoreData()
        val draft = original.editingDraft(original.score.title, 60, null)
        val edited = draft.put(2, ScoreNote.Pitch(Yul.Taeju)).first.toCommonScore()
        assertEquals(original.columns[0], edited.columns[0])
        assertEquals(original.events.take(2), edited.events.take(2))
        assertEquals(original.provenance, edited.provenance)
        assertEquals(ScoreNote.SourcePitch("태", 0), edited.toScore().events!!.last().note)
    }

    @Test fun draftBlankIsNotATieAndNewColumnsKeepLyricsWithTheirCells() {
        val original = smallScoreData()
        val draft = original.editingDraft(original.score.title, 60, null)
        val erased = draft.erase(2)
        assertTrue(erased.toCommonScore().columns[1].cells[0].units.isEmpty())
        assertEquals(listOf(2, 2, 1), draft.addColumnAfter(0).sizes)
        assertEquals(draft, draft.addColumnAfter(0).deleteColumn(1))
    }

    @Test fun thirdsRescaleIntegerTicksWithoutChangingOtherMusicalTimes() {
        val original = smallScoreData()
        var edited = original.editingDraft(original.score.title, 60, null).split(2)
        edited = edited.put(2, ScoreNote.Pitch(Yul.Hwangjong)).first.split(2)
        edited = edited.put(2, ScoreNote.Pitch(Yul.Taeju)).first
        val saved = edited.toCommonScore()
        assertEquals(12, saved.music.ticksPerJeonggan)
        assertEquals(original.toScore().events!!.take(2).map { it.unitStart to it.unitDuration }, saved.toScore().events!!.take(2).map { it.unitStart to it.unitDuration })
        assertEquals(36L, saved.events.last().let { it.startTick + it.durationTick })
    }

    @Test fun partialInputRestoresAsUnfilledRatherThanAnOriginalTie() {
        val original = smallScoreData()
        val draft = original.editingDraft(original.score.title, 60, null).split(2)
        val restored = draft.toCommonScore().editingDraft(original.score.title, 60, null)
        assertEquals(draft.cells, restored.cells)
        assertEquals(listOf(2), restored.unfilledCells)
    }
}
