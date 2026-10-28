package com.ssafy.eolssu.core.model

/** 서버 비밀번호 규칙: 영문·숫자·특수문자를 모두 넣은 8~128자. 가입·비밀번호 찾기·비밀번호 변경이 함께 쓴다. */
object PasswordRule {
    private val length = 8..128

    fun isValid(password: String): Boolean =
        password.length in length &&
            password.any { it in 'a'..'z' || it in 'A'..'Z' } &&
            password.any { it in '0'..'9' } &&
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
}
