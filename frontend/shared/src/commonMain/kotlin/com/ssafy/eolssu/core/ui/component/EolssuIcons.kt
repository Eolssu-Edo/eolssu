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
    val ArrowRight = lineIcon("ArrowRight", "M5 12h14 M13 6l6 6 -6 6")
    val ArrowUpRight = lineIcon("ArrowUpRight", "M6 18L18 6 M6 6h12v12")
    val Sort = lineIcon("Sort", "M7 4v16M4 17l3 3 3 -3", "M17 20V4M14 7l3 -3 3 3")
    val Search = lineIcon("Search", circle(11f, 11f, 7f), "M20 20l-3.5 -3.5")
    val Close = lineIcon("Close", "M6 6l12 12M18 6L6 18")
    val User = lineIcon("User", circle(12f, 8f, 4f), "M4 21c0 -4 3.6 -6.5 8 -6.5s8 2.5 8 6.5")
    val Play = lineIcon("Play", filled = listOf("M8 5v14l11 -7z"))
    val Pause = lineIcon("Pause", filled = listOf("M8 5h3v14H8zM13 5h3v14h-3z"))
    val Restart = lineIcon("Restart", "M3 12a9 9 0 1 0 3 -6.7", "M3 4v5h5")
    val Stop = lineIcon("Stop", filled = listOf(rect(6f, 6f, 12f, 12f, 2f)))
    val Mic = lineIcon("Mic", rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3")
    /** 소리가 입력되지 않은 마이크(보드 Dialog-NoInput: 마이크 위 사선). */
    val MicOff = lineIcon("MicOff", rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3", "M4 4l16 16")
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
    /** 보드 결과 화면의 집(지붕 + 문): ‘홈으로 가기’. */
    val House = lineIcon("House", "M3 11l9 -8 9 8", "M5 10v10h5v-6h4v6h5V10")
    /** 보드 결과 화면의 저장(디스켓): ‘연습 기록 저장’. */
    val Save = lineIcon("Save", "M5 4h11l3 3v13H5z", "M8 4v5h7V4M8 20v-6h8v6")
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
    /** 연두 보드 PRACTICE-01의 연습 설정 톱니(`.page-head` 오른쪽). [Settings]는 옛 와이어프레임 슬라이더 모양이다. */
    val Cog = lineIcon(
        "Cog",
        circle(12f, 12f, 3f),
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1 -2.8 2.8l-.1 -.1a1.7 1.7 0 0 0 -1.8 -.3 1.7 1.7 0 0 0 -1 1.5V21a2 2 0 1 1 -4 0v-.1" +
            "a1.7 1.7 0 0 0 -1.1 -1.5 1.7 1.7 0 0 0 -1.8 .3l-.1 .1a2 2 0 1 1 -2.8 -2.8l.1 -.1a1.7 1.7 0 0 0 .3 -1.8 1.7 1.7 0 0 0 -1.5 -1H3" +
            "a2 2 0 1 1 0 -4h.1a1.7 1.7 0 0 0 1.5 -1.1 1.7 1.7 0 0 0 -.3 -1.8l-.1 -.1a2 2 0 1 1 2.8 -2.8l.1 .1a1.7 1.7 0 0 0 1.8 .3H9" +
            "a1.7 1.7 0 0 0 1 -1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8 -.3l.1 -.1a2 2 0 1 1 2.8 2.8l-.1 .1" +
            "a1.7 1.7 0 0 0 -.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0 -1.5 1z",
    )
    /** 연습 설정 메뉴 줄 아이콘(보드 `.menu-row .mi`): 쉬운 표기 · 장단 · 속도. */
    val NotationMark = lineIcon("NotationMark", "M5 6h14M12 6v13M8 19h8")
    val JangdanMark = lineIcon("JangdanMark", "M8 21h8M12 21V11M6 4l6 7 6 -7")
    val TempoMark = lineIcon("TempoMark", "M4 16a8 8 0 1 1 16 0", "M12 16l4 -5")
    /** 음 듣기(보드 운지법 패널 `.btn.secondary`). */
    val Speaker = lineIcon("Speaker", "M4 9h4l5 -4v14l-5 -4H4z", "M16.5 9a4.5 4.5 0 0 1 0 6")
    val Bookmark = lineIcon("Bookmark", "M6 3h12v18l-6 -4 -6 4z")
    val Target = lineIcon("Target", circle(12f, 12f, 9f), circle(12f, 12f, 5f), circle(12f, 12f, 1f))
    val Zoom = lineIcon("Zoom", circle(11f, 11f, 7f), "M20 20l-3.5 -3.5M11 8v6M8 11h6")

    // 내 악보
    val Plus = lineIcon("Plus", "M12 5v14M5 12h14")
    val Pencil = lineIcon("Pencil", "M4 20h4L19 9l-4 -4L4 16z")
    /** 내 악보 삭제(`Song-Practice` 휴지통). */
    /** 마이페이지 문의하기(말풍선). */
    val Chat = lineIcon("Chat", "M4 5h16v11H10l-5 4v-4H4z")
    /** 마이페이지 로그아웃(문 밖으로 나가는 화살표). */
    val LogOut = lineIcon("LogOut", "M10 4H5v16h5", "M14 8l4 4 -4 4M18 12H9")
    val Trash = lineIcon("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1 -13M9 7V4h6v3")
    /** 기록 고르기·비교 기록 바꾸기 버튼의 달력. */
    val Calendar = lineIcon("Calendar", rect(3f, 5f, 18f, 16f, 2f), "M3 10h18M8 3v4M16 3v4")
    /** 5초 뒤로(반시계 화살표). 가운데 숫자는 따로 적는다. */
    val ReplayBack = lineIcon("ReplayBack", "M4.5 12a7.5 7.5 0 1 0 2.4 -5.5", "M4 3.5v4h4")
    /** 5초 앞으로(시계 방향 화살표). */
    val ReplayForward = lineIcon("ReplayForward", "M19.5 12a7.5 7.5 0 1 1 -2.4 -5.5", "M20 3.5v4h-4")
    /** ‘지금 위치로’(과녁 십자). */
    val Locate = lineIcon("Locate", circle(12f, 12f, 6f), "M12 2v3M12 19v3M2 12h3M19 12h3", filled = listOf(circle(12f, 12f, 1.5f)))

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
