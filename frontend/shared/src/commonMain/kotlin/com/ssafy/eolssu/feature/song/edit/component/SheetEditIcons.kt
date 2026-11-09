package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 악보 추가·편집 화면에만 쓰는 선 아이콘(와이어프레임 SVG 경로, 24 격자 · 선 1.8). 공통 아이콘은 `EolssuIcons`를 쓴다. */
internal object SheetEditIcons {
    val Undo = lineIcon("Undo", "M9 14L4 9l5 -5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = lineIcon("Redo", "M15 14l5 -5 -5 -5", "M20 9H10a6 6 0 0 0 0 12h3")
    val Camera = lineIcon("Camera", "M4 8h3l2 -3h6l2 3h3v11H4z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z")
    val Photo = lineIcon(
        "Photo",
        "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z",
        "M7.4 9a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z",
        "M20 15l-5 -5 -9 9",
    )
    val Folder = lineIcon("Folder", "M3 7a2 2 0 0 1 2 -2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z")
    val Erase = lineIcon("Erase", "M21 5H9l-6 7 6 7h12z", "M17 9l-6 6M11 9l6 6")
    val Download = lineIcon("Download", "M12 4v11M7 10l5 5 5 -5", "M5 19h14")
    /** 정간보: 세로 두 각을 정간으로 나눈 칸. */
    val Jeongganbo = lineIcon("Jeongganbo", "M6 3.5h12v17H6z", "M12 3.5v17", "M6 9.2h12M6 14.8h12")

    /** 오선보: 묶은 두 음표. */
    val StaffNotes = lineIcon("StaffNotes", "M10 17V6l9.5 -2v11", "M5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z", "M14.5 15a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")

    /** 내 악보: 글줄이 있는 종이. */
    val MySheet = lineIcon("MySheet", "M7 3h7l4 4v14H7z", "M14 3v4h4", "M10 12h5M10 16h5")

    val ChevronUp = lineIcon("ChevronUp", "M6 15l6 -6 6 6")

    private fun lineIcon(name: String, vararg strokes: String): ImageVector {
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
        return builder.build()
    }
}
