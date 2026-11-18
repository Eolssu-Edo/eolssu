package com.ssafy.eolssu.core.model

/** 비밀번호 조건 하나. 가입·비밀번호 찾기 화면의 체크리스트가 이 순서대로 보인다. */
enum class PasswordCheck {
    /** 영문(a-z, A-Z)을 하나 이상 넣었다. */
    Letter,

    /** 숫자(0-9)를 하나 이상 넣었다. */
    Digit,

    /** 영문·숫자·공백이 아닌 글자를 하나 이상 넣었다. */
    Special,

    /** [PasswordRule.MinLength]자 이상이다. */
    MinLength,
}

/**
 * 새 비밀번호 규칙: 영문·숫자·특수문자를 모두 넣은 8~64자. 가입·비밀번호 찾기·비밀번호 변경이 함께 쓴다.
 * 서버는 아직 8~128자를 받지만 앱은 모든 비밀번호 칸에서 64자까지만 받는다(S15P31E102-996, [InputLimit.PASSWORD]).
 * 화면 체크리스트([checks])와 제출 검사([isValid])가 같은 판정을 쓰도록 한곳에 둔다.
 */
object PasswordRule {
    const val MinLength = 8
    const val MaxLength = 64

    /** [password]가 채운 조건. 비어 있으면 빈 집합이다. */
    fun checks(password: String): Set<PasswordCheck> = buildSet {
        if (password.any { it in 'a'..'z' || it in 'A'..'Z' }) add(PasswordCheck.Letter)
        if (password.any { it in '0'..'9' }) add(PasswordCheck.Digit)
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) add(PasswordCheck.Special)
        if (password.length >= MinLength) add(PasswordCheck.MinLength)
    }

    /** 체크리스트 네 조건을 모두 채웠는지. 최대 길이는 따로 [isTooLong]으로 본다. */
    fun meetsAllChecks(password: String): Boolean = checks(password).size == PasswordCheck.entries.size

    fun isTooLong(password: String): Boolean = password.length > MaxLength

    fun isValid(password: String): Boolean = meetsAllChecks(password) && !isTooLong(password)
}
