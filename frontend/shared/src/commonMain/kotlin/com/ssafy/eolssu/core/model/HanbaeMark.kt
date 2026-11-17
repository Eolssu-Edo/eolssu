package com.ssafy.eolssu.core.model

/** 빠르기 지시. 음높이를 꾸미는 시김새와 별도로 편집한다. */
enum class HanbaeMark(val symbol: String, val label: String, val glyph: String, val gradual: Boolean = false, val span: Boolean = false) {
    // 조금 빠르게·느리게는 시작부터 끝 정간까지만 적용하고 끝 다음부터 앞 빠르기로 돌아간다(S15P31E102-1082).
    Slower("조금느리게", "조금 느리게", "ㅈ\nㄴ", span = true),
    Faster("조금속하게", "조금 빠르게", "ㅈ\nㅅ", span = true),
    GraduallySlower("점점느리게", "점점 느리게", "ㅈ\nㅈ\nㄴ", true),
    GraduallyFaster("점점속하게", "점점 빠르게", "ㅈ\nㅈ\nㅅ", true),
    // 인식·기존 악보의 기호를 보존하려고 남긴다. 새로 넣는 선택지에는 보이지 않는다(S15P31E102-1082).
    Original("본래속도로", "본래 속도로", "ㅂ\nㅅ");

    /** 끝 위치를 함께 정해야 하는 기호. */
    val needsEnd: Boolean get() = gradual || span

    companion object {
        fun fromSymbol(value: String): HanbaeMark? {
            val name = value.substringBefore('@').filterNot { it.isWhitespace() || it in "{}·∙‧ㆍ." }
            return when (name) {
                "ㅈㄴ", "좀느리게", "조금느리게" -> Slower
                "ㅈㅅ", "좀속하게", "좀빠르게", "조금빠르게", "조금속하게" -> Faster
                "ㅈㅈㄴ", "점점느리게" -> GraduallySlower
                "ㅈㅈㅅ", "점점빠르게", "점점속하게" -> GraduallyFaster
                "ㅂㅅ", "본래속도로", "본래의속도로" -> Original
                else -> null
            }
        }
    }
}
