package com.ssafy.eolssu.core.model

/**
 * 정간보에 적는 율명. 낮은 음 → 높은 음 순서로 선언한다(운지법 패널의 이전·다음 음 이동이 이 순서를 쓴다).
 *
 * @property symbol 악보에 적는 한자. 서버·데이터의 정간보 값과 같다.
 * @property hangul 율명 아래에 붙이는 한글 읽는 법.
 * @property fullName 율명 전체 이름. 운지법·율명 설명과 접근성 이름에 쓴다.
 */
enum class Yul(val symbol: String, val hangul: String, val fullName: String) {
    Hwangjong("黃", "황", "황종"),
    Daeryeo("大", "대", "대려"),
    Taeju("太", "태", "태주"),
    Hyeopjong("夾", "협", "협종"),
    Goseon("姑", "고", "고선"),
    Jungnyeo("仲", "중", "중려"),
    Yubin("蕤", "유", "유빈"),
    Imjong("林", "임", "임종"),
    Ichik("夷", "이", "이칙"),
    Namnyeo("南", "남", "남려"),
    Muyeok("無", "무", "무역"),
    Eungjong("應", "응", "응종"),

    /** 황종보다 한 옥타브 높은 음. 글자 왼쪽의 삼수변(氵)이 한 옥타브 위를 뜻한다. */
    CheongHwangjong("潢", "청황", "청황종"),
    CheongTaeju("汰", "청태", "청태주"),
    CheongImjong("淋", "청임", "청임종"),
    ;

    companion object {
        private val bySymbol = entries.associateBy { it.symbol }

        fun fromSymbol(symbol: String): Yul? = bySymbol[symbol]
    }
}
