package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 와이어프레임의 선 아이콘(24×24, 선 굵기 1.8). `Icon(tint = ...)`으로 색을 입힌다.
 * 사각형·원은 같은 모양의 경로로 옮겼다.
 */
object EolssuIcons {
    val Back = lineIcon("Back", "M15 18l-6 -6 6 -6")
    val Chevron = lineIcon("Chevron", "M9 6l6 6 -6 6")
    val ChevronDown = lineIcon("ChevronDown", "M6 9l6 6 6 -6")
    val Search = lineIcon("Search", circle(11f, 11f, 7f), "M20 20l-3.5 -3.5")
    val Close = lineIcon("Close", "M6 6l12 12M18 6L6 18")
    val Play = lineIcon("Play", filled = listOf("M8 5v14l11 -7z"))
    val Pause = lineIcon("Pause", filled = listOf("M8 5h3v14H8zM13 5h3v14h-3z"))
    val Restart = lineIcon("Restart", "M3 12a9 9 0 1 0 3 -6.7", "M3 4v5h5")
    val Stop = lineIcon("Stop", filled = listOf(rect(6f, 6f, 12f, 12f, 2f)))
    val Mic = lineIcon("Mic", rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Metronome = lineIcon("Metronome", "M9 3h6l3 18H6z", "M12 15l5 -9")
    val Speed = lineIcon("Speed", "M4 14a8 8 0 1 1 16 0", "M12 14l4 -5")
    val Hand = lineIcon(
        "Hand",
        "M8 13V5a1.5 1.5 0 0 1 3 0v6M11 10V4a1.5 1.5 0 0 1 3 0v7M14 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1 -12 0v-3a1.5 1.5 0 0 1 3 0",
    )
    val Text = lineIcon("Text", "M4 6h16M4 12h10M4 18h7")
    val Check = lineIcon("Check", "M5 12l5 5 9 -11")
    val Headphone = lineIcon(
        "Headphone",
        "M4 14v-2a8 8 0 0 1 16 0v2",
        rect(4f, 14f, 4f, 6f, 1f),
        rect(16f, 14f, 4f, 6f, 1f),
    )
    val Lock = lineIcon("Lock", rect(5f, 11f, 14f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4")
    val Info = lineIcon("Info", circle(12f, 12f, 9f), "M12 11v5M12 8h0.01")
    val Alert = lineIcon("Alert", "M12 3l10 18H2z", "M12 10v4M12 17h0.01")
    val Ear = lineIcon("Ear", "M6 9a6 6 0 0 1 12 0c0 3 -2 4 -3 6s-1 4 -4 4", "M9 9a3 3 0 0 1 6 0")
    val Book = lineIcon("Book", "M4 5a2 2 0 0 1 2 -2h14v16H6a2 2 0 0 0 -2 2z", "M4 19a2 2 0 0 1 2 -2h14")
    val Home = lineIcon("Home", "M3 11l9 -7 9 7v9a1 1 0 0 1 -1 1h-5v-6h-6v6H4a1 1 0 0 1 -1 -1z")
    val Grid = lineIcon("Grid", rect(4f, 4f, 16f, 16f, 2f), "M4 12h16M12 4v16")
    val Flute = lineIcon(
        "Flute",
        rect(2f, 10f, 20f, 4f, 2f),
        filled = listOf(dot(8f, 12f), dot(11f, 12f), dot(14f, 12f), dot(17f, 12f)),
    )
    val Next = Chevron
    val Settings = lineIcon(
        "Settings",
        "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1",
        circle(15f, 6f, 2f),
        circle(9f, 12f, 2f),
        circle(17f, 18f, 2f),
    )
    val Zoom = lineIcon("Zoom", circle(11f, 11f, 7f), "M20 20l-3.5 -3.5M11 8v6M8 11h6")

    private fun dot(cx: Float, cy: Float) = circle(cx, cy, 0.9f)

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r" +
            "v${h - 2 * r}a$r $r 0 0 1 ${-r} $r" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}" +
            "v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

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
