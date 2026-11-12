package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ScoreTest {

    @Test fun supplementaryRegistersHaveRealHanjaAndKeepTheirPitchIdentity() {
        listOf(Triple("이", -2, "𢓡"), Triple("유", -1, "𠐭"), Triple("대", 2, "𣴘")).forEach { (name, octave, symbol) ->
            val pitch = ScoreNote.SourcePitch(name, octave)
            assertEquals(symbol, pitch.symbol)
            assertEquals(name, pitch.yul)
            assertEquals(octave, pitch.octave)
        }
    }

    private val hwang = ScoreNote.Pitch(Yul.Hwangjong)
    private val tae = ScoreNote.Pitch(Yul.Taeju)
    private val jung = ScoreNote.Pitch(Yul.Jungnyeo)

    // 각 0: 黃 / [太, 仲] / ㅡ, 각 1: △ / 黃 / 太
    private val score = Score(
        listOf(
            listOf(ScoreCell(hwang), ScoreCell(tae, jung), ScoreCell(ScoreNote.Hold)),
            listOf(ScoreCell(ScoreNote.Rest), ScoreCell(hwang), ScoreCell(tae)),
        ),
    )

    @Test
    fun noteFromSymbolCoversYulAndMarks() {
        assertEquals(ScoreNote.Pitch(Yul.CheongHwangjong), ScoreNote.fromSymbol("潢"))
        assertEquals(ScoreNote.Hold, ScoreNote.fromSymbol("ㅡ"))
        assertEquals(ScoreNote.Rest, ScoreNote.fromSymbol("△"))
        assertNull(ScoreNote.fromSymbol("?"))
    }

    @Test
    fun yulSymbolsRoundTrip() {
        Yul.entries.forEach { assertEquals(it, Yul.fromSymbol(it.symbol)) }
    }

    @Test
    fun timelineFollowsColumnThenRowOrder() {
        val timeline = score.timeline("arirang")
        assertEquals(7, timeline.size)
        assertEquals((0 until 7).toList(), timeline.map { it.index })
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1), timeline.map { it.column })
        assertEquals(listOf(0, 1, 1, 2, 0, 1, 2), timeline.map { it.row })
        assertEquals(listOf("黃", "太", "仲", "ㅡ", "△", "黃", "太"), timeline.map { it.note.symbol })
    }

    @Test
    fun splitCellSharesOneUnit() {
        val timeline = score.timeline("arirang")
        val (first, second) = timeline[1] to timeline[2]
        assertEquals(0 to 1, first.sub to second.sub)
        assertEquals(2, first.subCount)
        assertEquals(1.0, first.unitStart)
        assertEquals(0.5, first.unitDuration)
        assertEquals(1.5, second.unitStart)
        assertEquals(2.0, timeline[3].unitStart)
    }

    @Test
    fun eventIdFollowsSharedContract() {
        val timeline = score.timeline("arirang")
        assertEquals("arirang-c0-r1-s1", timeline[2].id)
        assertEquals("arirang-c1-r2-s0", timeline.last().id)
    }

    @Test
    fun totalUnitsCountsCellsNotNotes() {
        assertEquals(6, score.totalUnits)
    }

    @Test
    fun cellRequiresAtLeastOneNote() {
        assertFailsWith<IllegalArgumentException> { ScoreCell(emptyList()) }
    }
}
