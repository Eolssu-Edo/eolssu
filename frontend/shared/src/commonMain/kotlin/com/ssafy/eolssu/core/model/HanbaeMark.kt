package com.ssafy.eolssu.core.model

/** 빠르기 지시. 음높이를 꾸미는 시김새와 별도로 편집한다. */
enum class HanbaeMark(val symbol: String, val label: String, val glyph: String, val gradual: Boolean = false) {
    Slower("조금느리게", "조금 느리게", "ㅈ\nㄴ"),
    Faster("조금속하게", "조금 빠르게", "ㅈ\nㅅ"),
    GraduallySlower("점점느리게", "점점 느리게", "ㅈ\nㅈ\nㄴ", true),
    GraduallyFaster("점점속하게", "점점 빠르게", "ㅈ\nㅈ\nㅅ", true),
    Original("본래속도로", "본래 속도로", "ㅂ\nㅅ");

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
