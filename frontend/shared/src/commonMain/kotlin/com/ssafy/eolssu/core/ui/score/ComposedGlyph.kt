package com.ssafy.eolssu.core.ui.score

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.ssafy.eolssu.core.theme.EolssuTheme
import kotlin.math.roundToInt

/**
 * 변 + 율명을 나란히 그린다. 변 글자는 왼쪽에만 획이 있어 [overlap] 비율만큼 다음 글자를 당겨 붙인다.
 * 정간보 쉽게 읽기의 탁황(亻黃)·청황(氵黃)도 이 모양이고, 부수만 [radicalColor]로 칠한다.
 * 율명 표시에서는 글꼴에 없는 글자의 예비 경로로만 쓴다. 변이 율명보다 작으면 획이 가늘어 보이므로
 * 기본 [radicalWeight]를 [fontWeight]보다 한 단계(100) 굵게 둬 율명의 획 굵기에 맞춘다(SemiBold → Bold).
 */
@Composable
fun ComposedGlyph(
    radicals: String,
    base: String,
    radicalSize: TextUnit,
    baseSize: TextUnit,
    overlap: Float,
    modifier: Modifier = Modifier,
    color: Color = EolssuTheme.colors.text,
    fontWeight: FontWeight = FontWeight.SemiBold,
    radicalWeight: FontWeight = if (radicalSize.type == baseSize.type && radicalSize.value < baseSize.value) {
        FontWeight(minOf(fontWeight.weight + 100, 900))
    } else {
        fontWeight
    },
    radicalColor: Color = color,
) {
    val style = EolssuTheme.serif.yul.copy(fontWeight = fontWeight)
    Layout(
        content = {
            radicals.forEach { r ->
                Text(r.toString(), color = radicalColor, style = style.copy(fontSize = radicalSize, lineHeight = radicalSize, fontWeight = radicalWeight))
            }
            Text(base, color = color, style = style.copy(fontSize = baseSize, lineHeight = baseSize))
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOf { it.height }
        val xs = mutableListOf<Int>()
        var x = 0
        placeables.forEachIndexed { i, p ->
            xs += x
            x += if (i < placeables.lastIndex) (p.width * (1 - overlap)).roundToInt() else p.width
        }
        layout(x, height) {
            placeables.forEachIndexed { i, p -> p.place(xs[i], (height - p.height) / 2) }
        }
    }
}
