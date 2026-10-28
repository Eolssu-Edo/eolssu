package com.ssafy.eolssu.backend.feature.auth.domain

/** 가입·비밀번호 변경·재설정 공통 규칙: 영문·숫자·특수문자를 모두 포함한 8~128자. 공백은 특수문자로 치지 않는다. */
object PasswordPolicy {
    fun isValid(password: String): Boolean =
        password.codePointCount(0, password.length) in 8..128 &&
            password.any { it in 'a'..'z' || it in 'A'..'Z' } &&
            password.any { it in '0'..'9' } &&
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
}
