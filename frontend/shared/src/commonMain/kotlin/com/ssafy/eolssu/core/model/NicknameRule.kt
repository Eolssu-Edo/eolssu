package com.ssafy.eolssu.core.model

/** 서버 닉네임 규칙: 앞뒤 공백을 뺀 값이 비어 있지 않고 최대 10자(String.length, UTF-16). 가입·소셜 가입·닉네임 수정이 함께 쓴다. */
object NicknameRule {
    const val MAX_LENGTH = 10

    fun isTooLong(nickname: String): Boolean = nickname.trim().length > MAX_LENGTH
}
