package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreSectionTest {
    /** 각마다 정간 수가 다른 악보(3 · 2 · 3). */
    private val score = Score(listOf(3, 2, 3).map { size -> List(size) { ScoreCell(ScoreNote.Hold) } })

    @Test
    fun betweenOrdersPositionsInPlayingOrder() {
        val section = ScoreSection.between(ScorePosition(2, 0), ScorePosition(0, 2))
        assertEquals(ScorePosition(0, 2), section.start)
        assertEquals(ScorePosition(2, 0), section.end)
        assertTrue(section.contains(1, 1))
        assertFalse(section.contains(0, 1))
        assertFalse(section.contains(2, 1))
        assertFailsWith<IllegalArgumentException> { ScoreSection(ScorePosition(1, 0), ScorePosition(0, 0)) }
    }

    @Test
    fun fullColumnsOnlyCoverColumnsWhollyInside() {
        assertEquals(1..1, ScoreSection.between(ScorePosition(0, 2), ScorePosition(2, 0)).fullColumns(score))
        assertEquals(0..2, ScoreSection.ofColumns(score, 0, 2).fullColumns(score))
        assertNull(ScoreSection(ScorePosition(0, 1), ScorePosition(0, 2)).fullColumns(score))
    }

    @Test
    fun columnMarkNeedsBothFirstAndLastCellOfTheColumn() {
        // 각의 처음은 들었지만 끝 정간이 빠지면 각 표시를 두지 않는다.
        assertEquals(1..1, ScoreSection(ScorePosition(1, 0), ScorePosition(2, 1)).fullColumns(score))
        // 각의 끝은 들었지만 처음 정간이 빠지면 두지 않는다.
        assertEquals(2..2, ScoreSection(ScorePosition(1, 1), ScorePosition(2, 2)).fullColumns(score))
        // 한 각 전체만 고르면 그 각만 표시한다.
        assertEquals(1..1, ScoreSection.ofColumns(score, 1, 1).fullColumns(score))
    }

    @Test
    fun sectionPaintsEveryCellFromStartToEndAcrossColumns() {
        val section = ScoreSection(ScorePosition(0, 2), ScorePosition(2, 0))
        val painted = score.columns.indices.flatMap { c -> score.columns[c].indices.map { ScorePosition(c, it) } }.filter { it in section }
        assertEquals(listOf(ScorePosition(0, 2), ScorePosition(1, 0), ScorePosition(1, 1), ScorePosition(2, 0)), painted)
    }

    /**
     * 각 3 · 2 · 3 정간 공통 악보. ○는 율명 음(번호), -는 앞 음의 이음, 쉼은 쉼표.
     * 0각: 쉼 · ○0 · ○1 / 1각: -(○1) · ○2 / 2각: -(○2) · 쉼 · ○3
     */
    private val heldSong: Song = run {
        val pitch = ScoreNote.Pitch(Yul.Hwangjong)
        fun event(index: Int, column: Int, row: Int, note: ScoreNote, start: Double, length: Double, noteIndex: Int?) =
            ScoreEvent(index, "e$index", column, row, 0, 1, note, start, length, noteIndex = noteIndex)
        val events = listOf(
            event(0, 0, 0, ScoreNote.Rest, 0.0, 1.0, null),
            event(1, 0, 1, pitch, 1.0, 1.0, 0),
            event(2, 0, 2, pitch, 2.0, 2.0, 1),
            event(3, 1, 1, pitch, 4.0, 2.0, 2),
            event(4, 2, 1, ScoreNote.Rest, 6.0, 1.0, null),
            event(5, 2, 2, pitch, 7.0, 1.0, 3),
        )
        fun cell(eventId: String, symbol: String) = ScoreCell(listOf(ScoreNote.Hold), units = listOf(ScoreUnit(0, 0, symbol, eventId)))
        val columns = listOf(
            listOf(cell("e0", "쉼"), cell("e1", "黃"), cell("e2", "黃")),
            listOf(cell("e2", "-"), cell("e3", "黃")),
            listOf(cell("e3", "-"), cell("e4", "쉼"), cell("e5", "黃")),
        )
        Song("held", "이음 검사", SongLevel.Intro, null, 60, "", false, Score(columns, events = events))
    }

    @Test
    fun notesSectionRunsFromFirstNoteToTheLastHeldCell() {
        // 마지막 음 ○2가 다음 각 첫 정간까지 이어지면 그 정간까지 연주한 구간이다.
        assertEquals(ScoreSection(ScorePosition(0, 1), ScorePosition(2, 0)), heldSong.notesSection(0..2))
        // 앞의 쉼표는 넣지 않고, 이음이 없는 마지막 음은 그 정간에서 끝난다.
        assertEquals(ScoreSection(ScorePosition(1, 1), ScorePosition(2, 2)), heldSong.notesSection(2..3))
        assertEquals(ScoreSection(ScorePosition(0, 2), ScorePosition(1, 0)), heldSong.notesSection(1..1))
    }

    @Test
    fun notesSectionIsNullWhenNoNoteMatches() {
        assertNull(heldSong.notesSection(10..12))
    }

    @Test
    fun positionsFollowVariableColumnLengths() {
        val positions = ScoreSection.between(ScorePosition(0, 2), ScorePosition(2, 0)).positions(score)
        assertEquals(listOf(ScorePosition(0, 2), ScorePosition(1, 0), ScorePosition(1, 1), ScorePosition(2, 0)), positions)
    }
}
