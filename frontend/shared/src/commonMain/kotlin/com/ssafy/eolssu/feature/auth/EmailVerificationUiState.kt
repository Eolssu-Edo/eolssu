package com.ssafy.eolssu.feature.auth

/** 인증번호 칸 아래 오류. */
sealed interface CodeError {
    data object Unavailable : CodeError
    /** 6자리 숫자가 아니다. */
    data object Invalid : CodeError

    data class Mismatch(val remainingAttempts: Int) : CodeError

    /** 3분이 지났다. 새 인증번호를 받아야 한다. */
    data object Expired : CodeError

    /** 5번 틀렸다. 새 인증번호를 받아야 한다. */
    data object TooManyAttempts : CodeError

    /**
     * 남은 시간이 있는데 서버가 이 인증번호를 받지 않았다(410: 새 번호로 바뀌었거나 이미 인증에 썼다).
     * 3분이 지났다는 안내는 틀리므로 따로 둔다. 새 인증번호를 받아야 한다.
     */
    data object NoLongerValid : CodeError
}

/** 새 인증번호를 받기 전까지 남는 오류. */
internal val CodeError.needsNewCode: Boolean
    get() = this == CodeError.Expired || this == CodeError.TooManyAttempts || this == CodeError.NoLongerValid

/**
 * AUTH-06 이메일 인증(가입 1/3)·AUTH-10 비밀번호 찾기(1/2) 입력 상태.
 * 인증번호를 보내면 [verificationId]가 생기고 인증번호 칸이 나온다. 남은 시간은 화면이 [secondsLeft]로 매초 다시 센다.
 */
data class EmailVerificationUiState(
    val email: String = "",
    val code: String = "",
    val emailError: FieldError? = null,
    val codeError: CodeError? = null,
    val verificationId: String? = null,
    val expiresAtMillis: Long = 0,
    val resendAvailableAtMillis: Long = 0,
    val verificationToken: String? = null,
) {
    val codeSent: Boolean get() = verificationId != null

    fun secondsLeft(now: Long): Long = ceilSeconds(expiresAtMillis - now)

    fun resendSecondsLeft(now: Long): Long = ceilSeconds(resendAvailableAtMillis - now)

    /** 시간이 지나면 '확인'을 누르기 전에도 만료로 보인다. */
    fun codeErrorAt(now: Long): CodeError? =
        if (codeSent && codeError == null && now >= expiresAtMillis) CodeError.Expired else codeError

    /** 만료됐거나 5번 틀려 새 인증번호를 받아야 한다. 이때는 '확인'을 막고 바로 다시 받게 한다. */
    fun needsNewCode(now: Long): Boolean = codeErrorAt(now)?.needsNewCode == true

    private fun ceilSeconds(millis: Long): Long = (millis.coerceAtLeast(0) + 999) / 1000
}
