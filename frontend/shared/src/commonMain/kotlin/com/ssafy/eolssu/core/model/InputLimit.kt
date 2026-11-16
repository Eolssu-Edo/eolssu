package com.ssafy.eolssu.core.model

/**
 * 입력칸 글자 수 상한(S15P31E102-996). 서버가 받는 길이 이하로 두어 넘는 값을 보내 400을 받는 일이 없게 하고,
 * 서버 상한이 비현실적으로 큰 칸(이메일·새 비밀번호)은 실제로 쓰일 만한 길이로 더 줄인다.
 * 글자 수는 서버와 같이 유니코드 코드 포인트로 센다(이모지 같은 UTF-16 서로게이트 쌍도 한 글자). 닉네임은 [NicknameRule]을 쓴다.
 */
object InputLimit {
    /**
     * 가입·인증번호 발송 등에서 사용하는 이메일 입력 상한.
     */
    const val EMAIL = 100

    /** 로그인 화면의 아이디(이메일) 입력 상한. */
    const val LOGIN_ID = 64

    /**
     * 모든 비밀번호 칸(가입·재설정·변경·로그인·탈퇴·현재 비밀번호·계정 연결). [PasswordRule.MaxLength]와 같은 64자다.
     * 서버는 아직 128자까지 받는다. 서버를 64자로 맞추는 일은 S15P31E102-1014에서 한다.
     */
    const val PASSWORD = PasswordRule.MaxLength

    /** 내 악보 제목(불러오기·편집, 서버 1~50자). */
    const val SHEET_TITLE = 50

    /** 연습 기록 메모(REC-01, 서버 500자). */
    const val MEMO = 500

    /** 곡 검색어. 서버에 보내지 않고 목록을 거르기만 하므로 곡 제목 길이 정도로 둔다. */
    const val SEARCH = 50

    /**
     * 빠르기(전체·구간 목표) 칸 자릿수. 999까지 받는다. 값의 범위 검사는 화면이 따로 한다.
     * 각·정간 번호 칸은 고정 자릿수 대신 편집 중인 악보의 각·정간 수에서 자릿수를 정한다.
     */
    const val BPM_DIGITS = 3

    /** [text]의 글자 수(코드 포인트). */
    fun length(text: CharSequence): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val character = text[index++]
            if (character.isHighSurrogate() && index < text.length && text[index].isLowSurrogate()) index++
            count++
        }
        return count
    }

    /**
     * 입력칸에 남길 값. [max]자까지만 받는다.
     * 이미 가득 찬 칸에 더 치면 이전 값을 그대로 두어(가운데 끼워 넣어도 끝 글자가 밀려 지워지지 않게) 입력을 막고,
     * 붙여넣기처럼 한 번에 넘치면 앞에서부터 [max]자만 남긴다. 서로게이트 쌍은 반으로 자르지 않는다.
     */
    fun limit(previous: String, input: String, max: Int): String {
        if (length(input) <= max) return input
        if (length(previous) >= max && input.length > previous.length) return previous
        return input.substring(0, endOfFirst(input, max))
    }

    /** [text]에서 앞쪽 [count]자가 끝나는 UTF-16 위치. */
    fun endOfFirst(text: CharSequence, count: Int): Int {
        var seen = 0
        var index = 0
        while (index < text.length && seen < count) {
            val character = text[index++]
            if (character.isHighSurrogate() && index < text.length && text[index].isLowSurrogate()) index++
            seen++
        }
        return index
    }
}
