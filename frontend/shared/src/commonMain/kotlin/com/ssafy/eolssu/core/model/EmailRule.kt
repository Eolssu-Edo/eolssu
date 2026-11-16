package com.ssafy.eolssu.core.model

/**
 * 서버 인증번호 발송의 이메일 형식 규칙(백엔드 `EmailPolicy`와 같다). 가입·비밀번호 찾기·로그인 입력이 함께 쓴다.
 * ASCII 로컬부 `A-Z a-z 0-9 . _ % + -`(점으로 시작·끝·연속 금지, 64자 이하), `@`, 점으로 나눈 도메인 라벨
 * (영문·숫자·하이픈 1~63자, 하이픈으로 시작·끝 금지), 영문 2자 이상 최상위 도메인.
 * 전체 길이는 서버(320자)보다 짧은 앱 상한 [InputLimit.EMAIL]자 이하다(S15P31E102-996).
 */
object EmailRule {
    private const val MaxLength = InputLimit.EMAIL
    private const val MaxLocalLength = 64
    private val pattern = Regex(
        """[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}""",
    )

    fun isValid(email: String): Boolean =
        email.length <= MaxLength && email.indexOf('@') in 1..MaxLocalLength && pattern.matches(email)
}
