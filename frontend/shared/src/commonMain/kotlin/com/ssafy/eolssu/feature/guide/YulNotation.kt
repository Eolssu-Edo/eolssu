package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.model.Yul

/*
 * 운지표의 율명 글자 규칙(와이어프레임 guide-data.js). 운지법 보기와 기초부터 배우기가 함께 쓴다.
 * 청성(氵)·탁성(亻)·중청 글자를 기본 12율명으로 되돌린 뒤 서양 음이름·음높이를 센다.
 */

/** 기본 12율명(황종 → 응종). */
private val BaseYul: List<String> = Yul.entries.take(12).map { it.symbol }

/** 청성·탁성·중청 글자 → 기본 율명. */
private val BaseOf: Map<String, String> = mapOf(
    "潢" to "黃", "汰" to "太", "㳞" to "仲", "淋" to "林", "湳" to "南", "潕" to "無", "㶐" to "應",
    "㶂" to "黃", "㶃" to "太", "㶄" to "夾", "㴩" to "姑", "㶌" to "仲", "㶍" to "林", "㶝" to "南",
    "浹" to "夾", "㴌" to "姑", "汏" to "大", "洟" to "夷", "㶋" to "蕤",
    "㑣" to "林", "㑥" to "夷", "㑲" to "南", "㒇" to "無", "㒣" to "應",
)

/** 기본 율명에서 몇 옥타브 떨어졌는지. 탁성 -1, 청성 1, 중청 2. 없으면 0. */
private val OctaveOf: Map<String, Int> = buildMap {
    "㑣㑥㑲㒇㒣".forEach { put(it.toString(), -1) }
    "潢汏汰浹㴌㳞㶋淋洟湳潕㶐".forEach { put(it.toString(), 1) }
    "㶂㶃㶄㴩㶌㶍㶝".forEach { put(it.toString(), 2) }
}

/** C부터 반음씩 올린 서양 음이름(내림표 표기, 고정 도). 음계 안 자리를 뜻하는 계이름이 아니다. */
private val Solfege12 = listOf("도", "레♭", "레", "미♭", "미", "파", "솔♭", "솔", "라♭", "라", "시♭", "시")

/** 운지표에 없는 글자의 음높이 순서. 낮은 음부터 나열할 때 맨 뒤로 보낸다. */
internal const val UNKNOWN_PITCH = 999

/** [hanja]의 기본 율명. 운지표에 없는 글자면 null. */
fun baseYulOf(hanja: String): String? = BaseOf[hanja] ?: hanja.takeIf { it in BaseYul }

fun octaveOf(hanja: String): Int = OctaveOf[hanja] ?: 0

/**
 * [hanja]의 서양 음이름. 악기의 황종 높이 [hwang](C에서 반음 수)에서 반음 단위로 센다. 옥타브는 적지 않는다.
 * 운지표에 없는 글자면 빈 문자열.
 */
fun solfegeOf(hanja: String, hwang: Int): String {
    val i = BaseYul.indexOf(BaseOf[hanja] ?: hanja)
    return if (i < 0) "" else Solfege12[(hwang + i) % 12]
}

/** 율명 옆에 덧붙이는 서양 음이름 표시. 율명이 주이고 이것은 참고라서 괄호로 감싼다. 음이름이 없으면 빈 문자열. */
fun westernNameLabel(name: String): String = if (name.isEmpty()) "" else "($name)"

/** 낮은 음부터 나열할 때 쓰는 음높이 순서. 황종 = 0, 반음마다 1. */
fun pitchIndexOf(hanja: String): Int {
    val i = BaseYul.indexOf(BaseOf[hanja] ?: hanja)
    return if (i < 0) UNKNOWN_PITCH else octaveOf(hanja) * 12 + i
}

/**
 * 세리프 글꼴에 있는 율명 한자(`tools/fonts/build_fonts.py`의 `SERIF_HANJA`와 같은 율명 15자).
 * 나머지 청성·탁성·중청 글자는 글꼴에 없어서 변(氵·亻)과 기본 율명을 나란히 그린다.
 */
internal val SerifYulGlyphs: Set<String> = Yul.entries.filter { it.drawnAs == null }.map { it.symbol }.toSet()

/** 율명 글자를 그릴 조각. [radicals]를 작게 앞에 붙이고 [base]를 그린다. [radicals]가 비면 글자 그대로다. */
data class HanjaGlyphParts(val radicals: String, val base: String)

/**
 * 글꼴에 없는 율명을 변 + 글꼴에 있는 율명으로 나눈다. 탁성은 亻, 청성은 氵, 중청은 氵를 하나 더 붙인다.
 * 예: 㑣 → 亻 + 林, 㳞 → 氵 + 仲, 㶂 → 氵 + 潢, 㴩 → 氵氵 + 姑.
 */
fun glyphPartsOf(hanja: String): HanjaGlyphParts {
    if (hanja in SerifYulGlyphs) return HanjaGlyphParts("", hanja)
    val base = baseYulOf(hanja) ?: return HanjaGlyphParts("", hanja)
    return when (val octave = octaveOf(hanja)) {
        -1 -> HanjaGlyphParts("亻", base)
        1 -> HanjaGlyphParts("氵", base)
        2 -> {
            val oneUp = SerifYulGlyphs.firstOrNull { BaseOf[it] == base && octaveOf(it) == 1 }
            if (oneUp != null) HanjaGlyphParts("氵", oneUp) else HanjaGlyphParts("氵氵", base)
        }
        else -> error("율명 $hanja 의 옥타브 $octave 는 그릴 수 없다")
    }
}
