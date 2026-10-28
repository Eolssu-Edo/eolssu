package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.module.kotlin.jacksonMapperBuilder

class ScoreJsonNotesTests {
    private val scoreNotes = ScoreJsonNotes(jacksonMapperBuilder().build())
    private val pitches = listOf(ScorePitchRow(1, "중", 0, 5), ScorePitchRow(2, "임", 0, 7), ScorePitchRow(3, "무", 0, 10))
        .associateBy { it.yul to it.octave.toInt() }

    // 박당 4틱. 중(1박) · 쉼(1박) · 임(반 박, 시김새) · 무(1.5박)
    private fun score(schemaVersion: Int = 1, thirdYul: String = "임") = """
        {"schemaVersion":$schemaVersion,"music":{"ticksPerBeat":4},"events":[
          {"kind":"NOTE","yul":"중","octave":0,"startTick":0,"durationTick":4,"sigimsae":[]},
          {"kind":"REST","yul":null,"octave":null,"startTick":4,"durationTick":4,"sigimsae":[]},
          {"kind":"NOTE","yul":"$thirdYul","octave":0,"startTick":8,"durationTick":2,"sigimsae":["추성"]},
          {"kind":"NOTE","yul":"무","octave":0,"startTick":10,"durationTick":6,"sigimsae":[]}]}
    """

    @Test
    fun `numbers only notes and keeps where each starts after a rest`() {
        assertEquals(
            listOf(ScoreNote(0, 5, 0.0, 1.0, false, 1), ScoreNote(1, 7, 2.0, 0.5, true, 2), ScoreNote(2, 10, 2.5, 1.5, false, 3)),
            scoreNotes.notes(score(), 0, 2, pitches),
        )
    }

    @Test
    fun `sends only the practice range with its original numbers and beats`() {
        assertEquals(
            listOf(ScoreNote(1, 7, 2.0, 0.5, true, 2), ScoreNote(2, 10, 2.5, 1.5, false, 3)),
            scoreNotes.notes(score(), 1, 2, pitches),
        )
    }

    @ParameterizedTest
    @CsvSource("0,3", "-1,1", "2,1")
    fun `rejects a range outside the notes`(start: Int, end: Int) {
        assertFailsWith<ScoreNotesException> { scoreNotes.notes(score(), start, end, pitches) }
    }

    @Test
    fun `rejects scores it cannot read`() {
        assertFailsWith<ScoreNotesException> { scoreNotes.notes(score(thirdYul = "응"), 0, 2, pitches) } // pitches에 없는 율명
        assertFailsWith<ScoreNotesException> { scoreNotes.notes(score(schemaVersion = 2), 0, 2, pitches) }
        assertFailsWith<ScoreNotesException> { scoreNotes.notes("""{"notes":[{"yul":"황"}]}""", 0, 0, pitches) }
        assertFailsWith<ScoreNotesException> { scoreNotes.notes("not json", 0, 0, pitches) }
    }
}
