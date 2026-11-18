package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.core.model.fingering.*
import com.ssafy.eolssu.data.practice.PracticeSettings
import com.ssafy.eolssu.feature.practice.component.fingerCells
import com.ssafy.eolssu.feature.practice.component.fingeringFor
import kotlin.test.*

class PracticeFingeringPageTest {
    @Test fun gangwonArirangHighNotesOpenFingering() {
        // 강원도 아리랑 2각 1~4정간의 원본 율명과 옥타브.
        val notes = listOf(
            ScoreNote.SourcePitch("황", 2), ScoreNote.SourcePitch("무", 1),
            ScoreNote.SourcePitch("태", 2), ScoreNote.SourcePitch("황", 2),
        )
        val song = fingerSong(notes.map { ScoreCell(it) })
        val ready = PracticeUiState(song = song, settings = PracticeSettings(instrumentId = GuideInstrument.Danso.id))

        for ((index, holes) in listOf(0 to "10000", 2 to "10100", 3 to "10000")) {
            val selected = ready.selectUnit(song.displayUnits[index])
            assertEquals(PracticePanel.Fingering, selected.panel)
            assertEquals(notes[index], selected.draft.note)
            assertEquals(holes.map(HoleState::parse), selected.instrument.fingeringFor(selected.draft.note)?.holes)
        }
    }

    @Test fun dansoCheongNamInScoreHasHalfHoleFingering() {
        val note = ScoreNote.SourcePitch("남", 1)
        val cell = fingerCells(fingerSong(listOf(ScoreCell(note))), GuideInstrument.Danso).single()
        assertEquals(note, cell.entries.single().note)
        assertEquals("11h00".map(HoleState::parse), cell.entries.single().holes)
    }

    @Test fun sourceCheongGoUsesSogeumFingeringInsteadOfRest() {
        val cell = fingerCells(fingerSong(listOf(ScoreCell(ScoreNote.SourcePitch("고", 1)))), GuideInstrument.Sogeum).single()
        assertFalse(cell.rest)
        assertEquals(listOf(HoleState.Closed) + List(5) { HoleState.Open }, cell.entries.single().holes)
        assertEquals(ScoreNote.SourcePitch("고", 1), cell.units.single().event.note)
    }

    @Test fun everyBuiltinPitchMapsByYulAndOctaveIncludingVariantHanja() {
        GuideInstrument.entries.forEach { instrument ->
            instrument.blows.forEach { blow ->
                instrument.notesByPitch(blow).forEach { reference ->
                    val base = Yul.fromSymbol(requireNotNull(baseYulOf(reference.hanja)))!!
                    val source = ScoreNote.SourcePitch(base.hangul, octaveOf(reference.hanja))
                    val cell = fingerCells(fingerSong(listOf(ScoreCell(source))), instrument).single()
                    assertFalse(cell.rest, "${instrument.id} ${reference.name}")
                    assertEquals(reference.holes, cell.entries.single().holes, "${instrument.id} ${reference.name}")
                }
            }
        }
    }

    @Test fun unsupportedPitchIsNotRestAndDoesNotBorrowAnotherOctave() {
        val cells = fingerCells(fingerSong(listOf(
            ScoreCell(ScoreNote.SourcePitch("고", 0)), ScoreCell(ScoreNote.Rest),
        )), GuideInstrument.Sogeum)
        assertFalse(cells[0].rest)
        assertNull(cells[0].entries.single().holes)
        assertTrue(cells[1].rest)
    }

    @Test fun holdsAndBlanksHaveNoFingeringAndKeepTheirDisplaySlots() {
        val song = fingerSong(listOf(ScoreCell(
            ScoreNote.SourcePitch("고", 1), ScoreNote.Hold, ScoreNote.Blank,
            ScoreNote.Rest, ScoreNote.SourcePitch("태", 1),
        )))
        val cell = fingerCells(song, GuideInstrument.Sogeum).single()
        assertEquals(5, cell.entries.size)
        assertNotNull(cell.entries[0].holes)
        assertNull(cell.entries[1].holes)
        assertNull(cell.entries[2].holes)
        assertNull(cell.entries[3].holes)
        assertNotNull(cell.entries[4].holes)
        assertEquals(song.displayUnits, cell.units)
        assertFalse(cell.rest)
    }
}

internal fun fingerSong(cells: List<ScoreCell>, jangdan: Jangdan? = null): Song = Song(
    "finger-test", "운지 검사", SongLevel.Intro, jangdan, 60, "", false, Score(listOf(cells)),
)
