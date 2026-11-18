package com.ssafy.eolssu.core.ui.score

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.fingering.yulGlyphParts
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.eolssu_yul_supplement
import org.jetbrains.compose.resources.Font

private val SupplementaryYulSymbols = setOf("𢓡", "𠐭", "𣴘")

/** 음표 하나를 어떤 글꼴·모양으로 그릴지. 악기와 상관없이 같은 글자는 같은 글리프로 그린다. */
internal sealed interface NoteGlyph {
    /** 세리프 글꼴의 글자 그대로. 율명이 아닌 기호(ㅡ·△)도 여기에 속한다. */
    data class Serif(val text: String) : NoteGlyph

    /** 세리프 원본에 없는 보충 평면 율명(하배이·배유·중청대). 보조 글꼴로 그린다. */
    data class Supplement(val text: String) : NoteGlyph

    /** 글꼴 어디에도 없는 율명의 예비 표시. 변을 기본 율명 왼쪽에 붙인다([ComposedGlyph]). */
    data class Composed(val radicals: String, val base: String) : NoteGlyph
}

/** 글꼴에 실제 글리프가 있으면 그 글자를, 없을 때만 변 + 기본 율명 조합을 고른다. */
internal fun noteGlyphOf(note: ScoreNote): NoteGlyph {
    val symbol = note.symbol
    if (symbol in SupplementaryYulSymbols) return NoteGlyph.Supplement(symbol)
    val pitch = note as? ScoreNote.SourcePitch
    if (pitch == null || symbol == pitch.hangul) return NoteGlyph.Serif(symbol)
    val parts = yulGlyphParts(symbol, pitch.baseYul.symbol, pitch.octave)
    return if (parts.radicals.isEmpty() && parts.base == symbol) NoteGlyph.Serif(symbol) else NoteGlyph.Composed(parts.radicals, parts.base)
}

/** 정간보 음표 글자. 대금의 배·하배 율명도 단소·소금과 같은 세리프 글리프로 그린다. */
@Composable
fun ScoreNoteGlyph(
    note: ScoreNote,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = EolssuTheme.colors.text,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    val style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size, fontWeight = fontWeight)
    when (val glyph = noteGlyphOf(note)) {
        is NoteGlyph.Serif -> Text(glyph.text, modifier, color = color, style = style)
        is NoteGlyph.Supplement -> Text(glyph.text, modifier, color = color,
            style = style.copy(fontFamily = FontFamily(Font(Res.font.eolssu_yul_supplement))))
        is NoteGlyph.Composed -> ComposedGlyph(glyph.radicals, glyph.base, size * 0.62f, size * 0.8f,
            overlap = 0.45f, modifier = modifier, color = color, fontWeight = fontWeight)
    }
}
