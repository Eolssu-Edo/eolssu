package com.ssafy.eolssu.core.ui.score

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import kotlin.math.roundToInt

/** 글꼴의 15자 밖인 청·배성도 옥타브를 빼지 않는다. 기존 guide의 부수 조합 방식과 같은 표시다. */
@Composable
fun ScoreNoteGlyph(
    note: ScoreNote,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = EolssuTheme.colors.text,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    val pitch = note as? ScoreNote.SourcePitch
    val style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size, fontWeight = fontWeight)
    if (pitch == null || pitch.fingeringYul != null || pitch.symbol == pitch.hangul) {
        Text(note.symbol, modifier, color = color, style = style)
        return
    }
    // data/src/scores/review_page.html HANJA: 하배 彳, 배 亻, 청 氵, 중청은 청성을 한 번 더 올린다.
    val radical = when (pitch.octave) { -2 -> "彳"; -1 -> "亻"; 1 -> "氵"; 2 -> "氵氵"; else -> "" }
    Layout(content = {
        radical.forEach { Text(it.toString(), color = color, style = style.copy(fontSize = size * 0.62f, lineHeight = size * 0.62f)) }
        Text(pitch.baseYul.symbol, color = color, style = style.copy(fontSize = size * 0.8f, lineHeight = size * 0.8f))
    }, modifier = modifier) { measurables, constraints ->
        val parts = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = parts.maxOf { it.height }
        var width = 0
        val positions = parts.mapIndexed { index, part ->
            width.also { width += if (index == parts.lastIndex) part.width else (part.width * 0.55f).roundToInt() }
        }
        layout(width, height) { parts.forEachIndexed { index, part -> part.place(positions[index], (height - part.height) / 2) } }
    }
}
