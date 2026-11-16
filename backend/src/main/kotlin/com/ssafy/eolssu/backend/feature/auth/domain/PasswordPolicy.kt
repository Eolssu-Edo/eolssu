package com.ssafy.eolssu.backend.feature.auth.domain

/** 가입·비밀번호 변경·재설정 공통 규칙: 영문·숫자·특수문자를 모두 포함한 8~64자(앱과 같음, S15P31E102-1014). 공백은 특수문자로 치지 않는다. */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    fun isValid(password: String): Boolean =
        password.codePointCount(0, password.length) in MIN_LENGTH..MAX_LENGTH &&
            password.any { it in 'a'..'z' || it in 'A'..'Z' } &&
            password.any { it in '0'..'9' } &&
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
}
