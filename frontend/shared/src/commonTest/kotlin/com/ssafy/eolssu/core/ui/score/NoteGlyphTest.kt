package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteGlyphTest {
    private val baseYul = listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")

    @Test
    fun everySourceOctaveUsesARealFontGlyphInsteadOfRadicalComposition() {
        val supplementary = setOf("𢓡", "𠐭", "𣴘")
        for (octave in -2..2) for (yul in baseYul) {
            val note = ScoreNote.SourcePitch(yul, octave)
            val expected = if (note.symbol in supplementary) NoteGlyph.Supplement(note.symbol) else NoteGlyph.Serif(note.symbol)
            assertEquals(expected, noteGlyphOf(note), "$yul/$octave ${note.symbol}")
        }
    }

    @Test
    fun daegeumLowNotesUseTheSameSerifPathAsDansoNotes() {
        assertEquals(NoteGlyph.Serif("僙"), noteGlyphOf(ScoreNote.SourcePitch("황", -1)))
        assertEquals(NoteGlyph.Serif("㑣"), noteGlyphOf(ScoreNote.SourcePitch("임", -1)))
        assertEquals(NoteGlyph.Serif("㣴"), noteGlyphOf(ScoreNote.SourcePitch("황", -2)))
        assertEquals(NoteGlyph.Serif("林"), noteGlyphOf(ScoreNote.SourcePitch("임", 0)))
        Yul.entries.forEach { assertEquals(NoteGlyph.Serif(it.symbol), noteGlyphOf(ScoreNote.Pitch(it))) }
    }

    @Test
    fun onlyNotesWithoutAnyFontGlyphFallBackToComposition() {
        // 옥타브 ±3 이상은 원본 표기 한자가 없어 기본 율명만 그린다.
        assertEquals(NoteGlyph.Composed("", "黃"), noteGlyphOf(ScoreNote.SourcePitch("황", 3)))
        assertEquals(NoteGlyph.Serif("ㅡ"), noteGlyphOf(ScoreNote.Hold))
        assertEquals(NoteGlyph.Serif("△"), noteGlyphOf(ScoreNote.Rest))
    }
}
