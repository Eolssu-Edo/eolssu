package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.song.edit.JangguCell
import com.ssafy.eolssu.feature.song.edit.JangguStroke

/** 장단 칸 사이 간격. 칸 크기를 정할 때도 같은 값을 쓴다. */
internal val JangguCellGap = 2.dp

/** 기본 칸 크기. 가장 긴 장단이 창 너비에 들어가지 않을 때만 모든 장단의 칸을 같은 비율로 줄인다. */
internal val JangguCellMaxSize = 18.dp

/** 가장 긴 장단 [maxCells]칸이 [available] 너비에 들어가는 칸 크기. 모든 장단이 같은 값을 써 세로 폭을 맞춘다. */
internal fun jangguCellSize(available: Dp, maxCells: Int): Dp {
    if (maxCells <= 0) return JangguCellMaxSize
    val fit = (available - JangguCellGap * (maxCells - 1)) / maxCells
    return minOf(JangguCellMaxSize, fit).coerceAtLeast(8.dp)
}

/**
 * 장단 한 주기를 같은 크기의 네모 칸으로 보인다. 빈칸은 비워 두고, 큰 박이 시작되는 칸은 테두리를 굵게 한다.
 * 글꼴에 따라 모양이 달라지지 않게 기호는 직접 그린다(S15P31E102-1138). 이름이 따로 읽히므로 접근성 트리에서는 뺀다.
 */
@Composable
internal fun JangguPatternCells(cells: List<JangguCell>, cellSize: Dp, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(JangguCellGap)) {
        cells.forEach { cell ->
            Canvas(Modifier.size(cellSize)) {
                val border = (if (cell.groupStart) 1.5.dp else 1.dp).toPx()
                drawRoundRect(
                    color = if (cell.groupStart) color else color.copy(alpha = 0.35f),
                    topLeft = Offset(border / 2, border / 2),
                    size = Size(size.width - border, size.height - border),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(border),
                )
                cell.stroke?.let { drawStroke(it, color) }
            }
        }
    }
}

/** 24칸 좌표계의 기호를 칸 크기의 78%에 맞춰 가운데에 그린다. */
private fun DrawScope.drawStroke(stroke: JangguStroke, color: androidx.compose.ui.graphics.Color) {
    val unit = size.minDimension * 0.78f / 24f
    val origin = Offset((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f)
    fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    val line = Stroke(width = maxOf(1.6f * unit, 1.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun bar(x: Float) = drawLine(color, p(x, 4f), p(x, 20f), line.width, StrokeCap.Round)
    fun dot(x: Float) = drawCircle(color, 1.4f * unit, p(x, 12f))
    when (stroke) {
        JangguStroke.Hap -> {
            drawCircle(color, 6f * unit, p(12f, 12f), style = line)
            drawLine(color, p(12f, 3f), p(12f, 21f), line.width, StrokeCap.Round)
        }
        JangguStroke.Buk -> drawCircle(color, 6f * unit, p(12f, 12f), style = line)
        JangguStroke.Chae -> bar(12f)
        JangguStroke.GiDeok -> { dot(7f); bar(14f) }
        JangguStroke.DeokGi -> { bar(10f); dot(17f) }
        JangguStroke.DeokDeok -> { bar(9f); bar(15f) }
        JangguStroke.Roll -> drawPath(Path().apply {
            listOf(12f to 3f, 9f to 7f, 15f to 11f, 9f to 15f, 15f to 19f, 12f to 21f).forEachIndexed { i, (x, y) ->
                val at = p(x, y)
                if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
            }
        }, color, style = line)
    }
}
