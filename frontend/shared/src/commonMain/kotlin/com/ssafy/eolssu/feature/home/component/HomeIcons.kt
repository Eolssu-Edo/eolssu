package com.ssafy.eolssu.feature.home.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 와이어프레임의 선 아이콘(24×24, 선 굵기 1.8). `Icon(tint = ...)`으로 색을 입힌다.
 * 다른 기능도 쓰게 되면 `core/ui/component`로 옮긴다.
 */
internal object HomeIcons {
    val Mic = lineIcon("Mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1 -6 0V6a3 3 0 0 1 3 -3z", "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Flute = lineIcon(
        "Flute",
        "M4 10h16a2 2 0 0 1 0 4H4a2 2 0 0 1 0 -4z",
        filled = listOf(dot(8f, 12f), dot(11f, 12f), dot(14f, 12f), dot(17f, 12f)),
    )
    val Hand = lineIcon(
        "Hand",
        "M8 13V5a1.5 1.5 0 0 1 3 0v6M11 10V4a1.5 1.5 0 0 1 3 0v7M14 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1 -12 0v-3a1.5 1.5 0 0 1 3 0",
    )
    val Grid = lineIcon("Grid", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z", "M4 12h16M12 4v16")
    val Book = lineIcon("Book", "M4 5a2 2 0 0 1 2 -2h14v16H6a2 2 0 0 0 -2 2z", "M4 19a2 2 0 0 1 2 -2h14")
    val Check = lineIcon("Check", "M5 12l5 5 9 -11")
    val ChevronDown = lineIcon("ChevronDown", "M6 9l6 6 6 -6")

    private fun dot(cx: Float, cy: Float) = "M${cx - 0.9f} ${cy}a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z"

    private fun lineIcon(name: String, vararg strokes: String, filled: List<String> = emptyList()): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach { data ->
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        filled.forEach { data ->
            builder.addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
        }
        return builder.build()
    }
}
