package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.AuthoringPhysicalRange
import com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.*

class SheetUnresolvedPitchEditingTest {
    @Test fun highAndLowUnresolvedAdaptationsKeepTheirPitchThroughAnnotationAndReload() {
        val cases = listOf(Triple("응" to 2, 2, "대" to 3), Triple("황" to -2, -1, "응" to -3))
        for ((before, transpose, expected) in cases) {
            val source = withPitch(smallScoreData(), TIED_NOTE, before.first, before.second)
            source.toScore()
            val draft = source.editingDraft("미해결 음", 60, null)
            val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
                AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
            val plan = planPitchAdaptation(draft.sourcePitchNotes(), emptyList()).first { it.transpose == transpose }
            val adapted = draft.applyAdaptation(plan, capability)
            assertEquals(ScoreNote.SourcePitch(expected.first, expected.second), adapted.cells[0][1])
            for ((cell, sub) in listOf(0 to 0, 0 to 1)) {
                val encoded = adapted.toggleSigimsae(cell, Sigimsae.Chuseong, sub).toCommonScore()
                assertPitch(expected, encoded, TIED_NOTE)
                assertEquals(source.events.map { Triple(it.id, it.startTick, it.durationTick) },
                    encoded.events.map { Triple(it.id, it.startTick, it.durationTick) })
                assertEquals(source.events.map { it.breathAfter }, encoded.events.map { it.breathAfter })
                val restored = encoded.editingDraft("미해결 음", 60, null)
                assertEquals(ScoreNote.SourcePitch(expected.first, expected.second), restored.cells[0][1])
                assertPitch(expected, restored.toggleSigimsae(0, Sigimsae.Toeseong).toCommonScore(), TIED_NOTE)
            }
        }
    }

    @Test fun rawExtremePitchesSurviveSplitsColumnMovesAndReload() {
        for (octave in listOf(-6, -3, 3, 5)) {
            val source = withPitch(smallScoreData(), LAST_NOTE, "대", octave)
            val draft = source.editingDraft("구조 편집", 60, null)
            val split = draft.split(2).putAt(2, 1, ScoreNote.Rest)
            assertPitch("대" to octave, split.toCommonScore(), LAST_NOTE)
            val moved = split.addColumnAfter(0).toCommonScore()
            assertPitch("대" to octave, moved, "c2-j0-r0-u0")
            val restored = moved.editingDraft("구조 편집", 60, null)
            assertPitch("대" to octave, restored.deleteColumn(1).toCommonScore(), LAST_NOTE)
        }
    }

    @Test fun replacingAnExtremeNoteWithTheSameBaseYulReallyChangesItsOctave() {
        for (octave in listOf(-3, 3)) {
            val draft = withPitch(smallScoreData(), LAST_NOTE, "대", octave).editingDraft("음 교정", 60, null)
            val corrected = draft.putAt(2, 0, ScoreNote.SourcePitch("대", 0)).toCommonScore()
            assertPitch("대" to 0, corrected, LAST_NOTE)
            corrected.toScore()
        }
    }

    @Test fun missingExtremeGlyphKeepsItsEventAndCrossCellTieWhenAnotherNoteChanges() {
        val source = withPitch(smallScoreData(), TIED_NOTE, "중", 3, missingGlyph = true)
        val draft = source.editingDraft("미표기 원음", 60, null)
        val encoded = draft.toggleSigimsae(0, Sigimsae.Chuseong).toCommonScore()
        assertPitch("중" to 3, encoded, TIED_NOTE)
        assertEquals(source.events.map { Triple(it.id, it.startTick, it.durationTick) },
            encoded.events.map { Triple(it.id, it.startTick, it.durationTick) })
        assertEquals(TIED_NOTE, encoded.columns[0].cells[1].units.single().eventId)
    }

    @Test fun undoAndRedoRestoreTheActualExtremePitch() {
        val draft = withPitch(smallScoreData(), LAST_NOTE, "대", 3).editingDraft("되돌리기", 60, null)
        val initial = SheetEditUiState(EditSource.Mine("31"), draft).selectUnit(2, 0)
        val edited = initial.toggleSigimsae(Sigimsae.Chuseong)
        val undone = edited.undo()
        assertEquals(draft, undone.draft)
        assertPitch("대" to 3, undone.draft.toCommonScore(), LAST_NOTE)
        assertEquals(edited.draft, undone.redo().draft)
        assertPitch("대" to 3, undone.redo().draft.toCommonScore(), LAST_NOTE)
    }

    @Test fun editingAnotherPitchDoesNotSilentlyRepairAMismatchedNormalGlyph() {
        val source = smallScoreData().let { score -> score.copy(events = score.events.mapIndexed { index, event ->
            if (index == 0) event.copy(yul = "태") else event
        }) }
        val encoded = source.editingDraft("표기 불일치", 60, null).putAt(0, 1, ScoreNote.SourcePitch("태", 1)).toCommonScore()
        assertEquals(source.events.first(), encoded.events.first())
        assertFails { encoded.toScore() }
    }

    private fun withPitch(source: CommonScoreData, id: String, yul: String, octave: Int, missingGlyph: Boolean = false): CommonScoreData =
        source.copy(events = source.events.map { if (it.id == id) it.copy(kind = "NOTE", yul = yul, octave = octave) else it },
            columns = source.columns.map { column -> column.copy(cells = column.cells.map { cell ->
                cell.copy(units = cell.units.map { unit ->
                    if (unit.eventId == id && id == "${cell.id}-r${unit.row}-u${unit.col}")
                        unit.copy(symbol = if (missingGlyph) "" else ScoreNote.SourcePitch(yul, octave).hangul) else unit
                })
            }) })

    private fun assertPitch(expected: Pair<String, Int>, score: CommonScoreData, id: String) {
        val note = score.events.single { it.id == id }
        assertEquals("NOTE", note.kind)
        assertEquals(expected, note.yul to note.octave)
    }

    private companion object {
        const val TIED_NOTE = "c0-j0-r0-u1"
        const val LAST_NOTE = "c1-j0-r0-u0"
    }
}
