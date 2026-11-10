package com.ssafy.eolssu.core.model

/** 서버 닉네임 규칙: 앞뒤 공백을 뺀 값이 비어 있지 않고 최대 10자(String.length, UTF-16). 가입·소셜 가입·닉네임 수정이 함께 쓴다. */
object NicknameRule {
    const val MAX_LENGTH = 10

    fun isTooLong(nickname: String): Boolean = nickname.trim().length > MAX_LENGTH

    /**
     * 입력칸에 남길 값. 서버와 같은 [MAX_LENGTH]자(String.length)까지만 받는다.
     * 이미 가득 찬 칸에 더 치면 이전 값을 그대로 두어(가운데 끼워 넣어도 끝 글자가 밀려 지워지지 않게) 입력을 막고,
     * 붙여넣기처럼 한 번에 넘치면 앞에서부터 [MAX_LENGTH]자만 남긴다. 이모지처럼 두 칸짜리 글자는 반으로 자르지 않는다.
     */
    fun limit(previous: String, input: String): String {
        if (input.length <= MAX_LENGTH) return input
        if (previous.length >= MAX_LENGTH && input.length > previous.length) return previous
        val end = if (input[MAX_LENGTH - 1].isHighSurrogate()) MAX_LENGTH - 1 else MAX_LENGTH
        return input.substring(0, end)
    }
}
