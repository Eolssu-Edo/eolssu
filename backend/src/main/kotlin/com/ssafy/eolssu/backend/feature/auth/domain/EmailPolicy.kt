package com.ssafy.eolssu.backend.feature.auth.domain

/**
 * 인증번호 발송에 받는 이메일 형식. 앱 `EmailRule`과 같은 규칙이다.
 * ASCII 로컬부 `A-Z a-z 0-9 . _ % + -`(점으로 시작·끝·연속 금지, 64자 이하), `@`, 점으로 나눈 도메인 라벨
 * (영문·숫자·하이픈 1~63자, 하이픈으로 시작·끝 금지), 영문 2자 이상 최상위 도메인, 전체 [MAX_LENGTH]자 이하.
 * 새로 받는 이메일(가입·인증번호 발송)에만 쓴다. 실제 주소는 100자를 넘지 않아 앱과 같은 100자로 막는다(S15P31E102-1014).
 * 로그인·간편 로그인 제공자 이메일은 이미 가입한 계정이 막히지 않게 이 규칙으로 거르지 않는다.
 */
object EmailPolicy {
    const val MAX_LENGTH = 100
    private const val MAX_LOCAL_LENGTH = 64
    private val pattern = Regex(
        """[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}""",
    )

    fun isValid(email: String): Boolean =
        email.length <= MAX_LENGTH && email.indexOf('@') in 1..MAX_LOCAL_LENGTH && pattern.matches(email)
}
