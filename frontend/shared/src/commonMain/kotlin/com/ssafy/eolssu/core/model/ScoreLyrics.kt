package com.ssafy.eolssu.core.model

/**
 * 가사 토큰의 자리를 유지한 표시 문자열. 장음 토큰은 빈 자리로 남겨 뒤 음절이 당겨지지 않는다.
 * 같은 표시 자리 안의 여러 음절은 함께 두며 원본 가사·음표·재생 정보는 수정하지 않는다.
 */
fun scoreLyricSlots(lyric: String?, slotCount: Int): List<String> {
    if (slotCount <= 0) return emptyList()
    val result = MutableList(slotCount) { "" }
    val tokens = lyric.orEmpty().trim().split(Regex("[\\s|]+"))
        .filter { it.isNotEmpty() }
    tokens.forEachIndexed { index, token ->
        val hangul = token.replace("{세로표}", "").filter { char ->
            char != 'ㅡ' && (char in '가'..'힣' || char in '\u1100'..'\u11ff' || char in '\u3131'..'\u318e')
        }
        result[index.coerceAtMost(result.lastIndex)] += hangul
    }
    return result
}
