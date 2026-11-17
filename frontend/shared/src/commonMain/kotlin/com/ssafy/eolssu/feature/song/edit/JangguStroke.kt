package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanPattern

/** 장단 고르기 칸에 그리는 장구 기호. 같은 소리의 다른 구음(덩·떵, 쿵·궁, 덕·따·다, 기덕·기닥)은 한 기호로 묶는다. */
internal enum class JangguStroke { Hap, Buk, Chae, GiDeok, DeokGi, DeokDeok, Roll }

/** 장단 한 칸. [stroke]가 null이면 앞 소리를 잇는 빈칸이고, [groupStart]는 큰 박이 시작되는 칸이다. */
internal data class JangguCell(val stroke: JangguStroke?, val groupStart: Boolean)

private fun jangguStroke(gueum: String): JangguStroke? = when (gueum) {
    "덩", "떵" -> JangguStroke.Hap
    "쿵", "궁" -> JangguStroke.Buk
    "덕", "따", "다" -> JangguStroke.Chae
    "기덕", "기닥" -> JangguStroke.GiDeok
    "덕기" -> JangguStroke.DeokGi
    "덕덕" -> JangguStroke.DeokDeok
    "더러러러" -> JangguStroke.Roll
    else -> null
}

/**
 * 카탈로그 장단 패턴을 기호 칸으로 바꾼다. 모르는 구음이 있거나 묶음 수가 칸 수와 맞지 않으면
 * 기호를 추측하지 않고 null을 돌려 칸 줄을 숨긴다(S15P31E102-1138).
 */
internal fun CatalogJangdanPattern.jangguCells(): List<JangguCell>? {
    if (beats.isEmpty() || group.isEmpty() || group.any { it <= 0 } || group.sum() != beats.size) return null
    val starts = group.runningFold(0) { at, size -> at + size }.dropLast(1).toSet()
    return beats.mapIndexed { index, gueum ->
        val stroke = if (gueum.isEmpty()) null else jangguStroke(gueum) ?: return null
        JangguCell(stroke, index in starts)
    }
}
