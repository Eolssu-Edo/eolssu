package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.image
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_legend_closed
import eolssu.shared.generated.resources.guide_legend_half
import eolssu.shared.generated.resources.guide_legend_open
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 구멍 표시 색. 와이어프레임 그림의 막음 색(#121212)과 '?' 색이다. */
private val HoleInk = Color(0xFF121212)

/** 가로 띠로 자를 때 사진 위아래에서 각각 잘라내는 원본 픽셀(연습 화면 보드 `viewBox 0 150 2000 h-300`). */
private const val FigureCropPx = 150

/**
 * 악기 사진 위에 구멍 상태를 겹친 그림. 막음은 검은 원, 반 막음은 왼쪽 반원, 확인 필요는 '?'.
 * 좌표는 사진 원본(가로 2000px) 기준이라 그린 너비에 맞춰 줄인다. 운지법 보기·기초부터 배우기·연습 화면이 함께 쓴다.
 *
 * @param cropVertical 사진의 위아래 여백을 잘라 가로로 긴 띠로 보인다(연습 화면의 ‘운지법과 함께 보기’ 줄).
 */
@Composable
fun FingeringFigure(
    instrument: GuideInstrument,
    holes: List<HoleState>,
    modifier: Modifier = Modifier,
    description: String? = null,
    cropVertical: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val muted = EolssuTheme.colors.textMuted
    val painter = painterResource(instrument.image)
    val cropTop = if (cropVertical) FigureCropPx else 0
    val visibleHeight = instrument.imageHeight - cropTop * 2
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(instrument.imageWidth.toFloat() / visibleHeight)
            .clipToBounds()
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val k = size.width / instrument.imageWidth
        translate(top = -cropTop * k) {
            with(painter) { draw(Size(size.width, instrument.imageHeight * k)) }
            val r = (instrument.holeRadius - 2) * k
            instrument.holes.forEachIndexed { i, point ->
                val center = Offset(point.x * k, point.y * k)
                when (holes.getOrElse(i) { HoleState.Open }) {
                    HoleState.Closed -> drawCircle(HoleInk, r, center)
                    HoleState.Half -> drawPath(
                        Path().apply { arcTo(Rect(center, r), 90f, 180f, forceMoveTo = true); close() },
                        HoleInk,
                    )
                    HoleState.Unknown -> {
                        val layout = measurer.measure(
                            "?",
                            TextStyle(fontSize = (36 * k).toSp(), fontWeight = FontWeight.Bold, color = muted),
                        )
                        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                    HoleState.Open -> Unit
                }
            }
        }
    }
}

/** 구멍 표시 범례(`.legend`): 막는 구멍 · 여는 구멍 · 반 막음. [centered]이면 가운데에, 아니면 왼쪽부터 놓는다. */
@Composable
fun HoleLegend(modifier: Modifier = Modifier, centered: Boolean = true) {
    val colors = EolssuTheme.colors
    Row(
        modifier,
        horizontalArrangement = if (centered) Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(stringResource(Res.string.guide_legend_closed)) { drawCircle(colors.text) }
        LegendItem(stringResource(Res.string.guide_legend_open)) {}
        LegendItem(stringResource(Res.string.guide_legend_half)) {
            drawArc(colors.text, 90f, 180f, useCenter = true)
        }
    }
}

@Composable
private fun LegendItem(label: String, fill: DrawScope.() -> Unit) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(12.dp)) {
            fill()
            val stroke = 1.5.dp.toPx()
            drawCircle(colors.text, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
        }
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
}
