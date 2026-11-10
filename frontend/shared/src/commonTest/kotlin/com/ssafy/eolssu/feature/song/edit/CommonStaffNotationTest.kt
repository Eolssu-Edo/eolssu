package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommonStaffNotationTest {
    @Test fun selectingTheSecondUnitOfASplitCellEditsItsPitchAndPreservesTheDocument() {
        val sample = smallScoreData()
        val original = sample.copy(columns = sample.columns.map { column ->
            column.copy(cells = column.cells.map { cell -> cell.copy(lyric = "가사 ${cell.id}") })
        })
        val draft = original.editingDraft(original.score.title, 60, null)
        val glyphs = assertIs<CommonStaffProjection.Ready>(draft.commonStaffProjection()).glyphs
        val first = glyphs.first()
        assertEquals("E♭4", first.pitch?.name)
        assertEquals(16, first.denominator)
        assertEquals(1, first.dots)
        val tied = glyphs.filter { it.event.id == "c0-j0-r0-u1" }
        assertTrue(tied.size > 1)
        assertTrue(tied.first().tieToNext)
        assertTrue(tied.last().tieFromPrevious)
        assertTrue(tied.all { it.key == StaffNoteKey(0, 1) })
        assertEquals("A♭5", tied.first().pitch?.name)
        assertEquals(List(tied.size) { if (it == 0) CommonStaffAccidental.Flat else null }, tied.map { it.accidental })

        val state = SheetEditUiState.initial(EditSource.Mine("31"), draft, EditView.Staff)
            .copy(availablePitches = setOf("중" to 1, "임" to 1))
            .selectStaffNote(tied.last().key)
        assertEquals(1, state.selectedSub)
        assertFalse(state.canShiftCommonStaffPitch(-1))
        val edited = state.shiftStaffNote(1)
        assertEquals(ScoreNote.SourcePitch("임", 1), edited.draft.cells[0][1])
        assertEquals(state.draft, edited.undo().draft)
        val saved = edited.draft.toCommonScore()
        val updated = original.columns[0].cells[0]
        assertEquals(updated.copy(units = updated.units.mapIndexed { index, unit ->
            if (index == 1) unit.copy(symbol = "청임") else unit
        }), saved.columns[0].cells[0])
        assertEquals(original.columns[0].cells[1], saved.columns[0].cells[1])
        assertEquals(original.music, saved.music)
        assertEquals(original.events.map { if (it.id == tied.first().event.id) it.copy(yul = "임") else it }, saved.events)
        assertEquals(ScoreNote.SourcePitch("임", 1), saved.toScore().events?.get(1)?.note)
    }

    @Test fun tripletsUseTheWrittenValueAndRestsRemainRests() {
        val original = smallScoreData()
        val positions = listOf("하배황", "쉼", "중청응")
        val units = positions.mapIndexed { index, symbol -> CommonScoreData.Unit(index, 0, symbol, "c0-j0-r$index-u0") }
        val triplet = original.copy(
            music = original.music.copy(bpmUnit = "QUARTER", ticksPerBeat = 3, ticksPerJeonggan = 3),
            columns = listOf(CommonScoreData.Column(0, listOf(CommonScoreData.Cell("c0-j0", 0, units)))),
            events = units.mapIndexed { index, unit ->
                val note = listOf("황" to -2, null, "응" to 2)[index]
                CommonScoreData.Event(unit.eventId, if (note == null) "REST" else "NOTE", note?.first, note?.second,
                    index.toLong(), 1, "c0-j0", false, emptyList())
            },
        )
        val glyphs = assertIs<CommonStaffProjection.Ready>(triplet.editingDraft("셋잇단음표", 60, null).commonStaffProjection()).glyphs
        assertEquals(listOf("E♭2", null, "D7"), glyphs.map { it.pitch?.name })
        assertTrue(glyphs.all { it.tuplet && it.denominator == 8 && it.dots == 0 })
    }

    @Test fun naturalSignOnlyCancelsAnEarlierFlatAtTheSameStaffPosition() {
        val original = smallScoreData()
        val notes = listOf("대" to 0, "황" to 0, "대" to 1, "대" to 0, "대" to 0)
        val cells = notes.mapIndexed { index, (yul, octave) ->
            val id = "c0-j$index"
            CommonScoreData.Cell(id, index, listOf(CommonScoreData.Unit(0, 0, ScoreNote.SourcePitch(yul, octave).hangul, "$id-r0-u0")))
        }
        val score = original.copy(
            music = original.music.copy(bpmUnit = "QUARTER", ticksPerBeat = 1, ticksPerJeonggan = 1),
            columns = listOf(CommonScoreData.Column(0, cells)),
            events = notes.mapIndexed { index, (yul, octave) ->
                val cell = cells[index]
                CommonScoreData.Event(cell.units.single().eventId, "NOTE", yul, octave, index.toLong(), 1, cell.id, false, emptyList())
            },
        )
        val glyphs = assertIs<CommonStaffProjection.Ready>(score.editingDraft("임시표", 60, null).commonStaffProjection()).glyphs
        assertEquals(listOf("E4", "E♭4", "E5", "E4", "E4"), glyphs.map { it.pitch?.name })
        assertEquals(listOf(null, CommonStaffAccidental.Flat, null, CommonStaffAccidental.Natural, null), glyphs.map { it.accidental })
    }

    @Test fun incompleteAndUnsupportedScoresHaveDistinctFallbacks() {
        val original = smallScoreData()
        val draft = original.editingDraft("악보", 60, null)
        val dTuning = original.copy(music = original.music.copy(hwangHz = 293.66)).editingDraft("악보", 60, null)
        assertEquals("D4", assertIs<CommonStaffProjection.Ready>(dTuning.commonStaffProjection()).glyphs.first().pitch?.name)
        assertEquals(CommonStaffProjection.Unsupported.Incomplete, draft.erase(2).commonStaffProjection())
        assertEquals(CommonStaffProjection.Unsupported.Tuning,
            original.copy(music = original.music.copy(hwangHz = 315.0)).editingDraft("악보", 60, null).commonStaffProjection())
        assertEquals(CommonStaffProjection.Unsupported.TimingUnit,
            original.copy(music = original.music.copy(bpmUnit = "JEONGGAN")).editingDraft("악보", 60, null).commonStaffProjection())
        assertEquals(CommonStaffProjection.Unsupported.Duration,
            original.copy(music = original.music.copy(bpmUnit = "QUARTER", ticksPerBeat = 5))
                .editingDraft("악보", 60, null).commonStaffProjection())
    }
}
