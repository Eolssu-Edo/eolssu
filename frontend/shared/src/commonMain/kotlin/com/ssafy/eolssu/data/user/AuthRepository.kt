package com.ssafy.eolssu.data.user

/**
 * 이메일 로그인·로그아웃과 이메일 인증번호(`/api/auth/email-verifications`).
 * 로그인·로그아웃은 [ApiAuthRepository]가 서버로 하고, 인증번호는 아직 [SampleAuthRepository]가 예시 결과를 돌려준다.
 * 가입·비밀번호 재설정·계정 연결 요청은 가입 API를 붙일 때 여기에 더한다.
 */
interface AuthRepository {
    suspend fun logIn(email: String, password: String): LoginResult

    /** 서버 로그아웃을 요청하고 기기의 로그인 쿠키를 지운다. 화면이 바로 넘어가도 끝까지 하도록 기다리지 않는다. */
    fun logOut()

    suspend fun sendCode(email: String, purpose: VerificationPurpose): SendCodeResult

    suspend fun confirmCode(verificationId: String, code: String): ConfirmCodeResult

    suspend fun signUp(request: Registration): MemberResult = MemberResult.Unavailable
    suspend fun resetPassword(token: String, password: String): MemberResult = MemberResult.Unavailable
}

/** 인증번호를 받는 목적(`purpose`). 계정 연결(`ACCOUNT_MERGE`)은 연결 화면이 인증번호를 쓰기로 정해지면 더한다. */
enum class VerificationPurpose { SignUp, PasswordReset }

sealed interface LoginResult {
    /** [withdrawalCancelled]: 탈퇴 대기 계정으로 로그인해 탈퇴가 취소됐다(`withdrawalCancelled`). */
    data class Success(val withdrawalCancelled: Boolean) : LoginResult

    data object WrongCredentials : LoginResult

    /** 같은 이메일로 15분 안에 5번 실패해 15분 동안 로그인할 수 없다(429). */
    data object Locked : LoginResult

    /** 서버에 닿지 못했거나 서버가 처리하지 못했다(연결 실패·시간 초과·5xx). */
    data object Unavailable : LoginResult
}

sealed interface SendCodeResult {
    data object Unavailable : SendCodeResult
    data class RateLimited(val resendAvailableAtMillis: Long?) : SendCodeResult
    /** 인증번호는 [expiresAtMillis]까지 유효하고, [resendAvailableAtMillis]부터 다시 받을 수 있다. */
    data class Sent(val verificationId: String, val expiresAtMillis: Long, val resendAvailableAtMillis: Long) : SendCodeResult

    /** 가입: 이미 가입된 이메일(409 `EMAIL_ALREADY_EXISTS`). 탈퇴 대기 회원도 여기에 해당한다. 재설정은 가입 여부를 숨겨 항상 [Sent]다. */
    data object EmailAlreadyExists : SendCodeResult
}

sealed interface ConfirmCodeResult {
    data object Unavailable : ConfirmCodeResult
    data class Confirmed(val verificationToken: String, val email: String? = null) : ConfirmCodeResult

    /** 400 `VERIFICATION_CODE_MISMATCH`의 `remainingAttempts`. */
    data class Mismatch(val remainingAttempts: Int) : ConfirmCodeResult

    /** 5번 틀려 이 인증번호를 쓸 수 없다(429). */
    data object TooManyAttempts : ConfirmCodeResult

    /** 3분이 지났다(410 `VERIFICATION_EXPIRED`). */
    data object Expired : ConfirmCodeResult
}

/** 인증번호 유효 시간·재발송 간격(서버 정책). */
const val VerificationCodeValidMillis = 3 * 60 * 1000L
const val VerificationResendMillis = 60 * 1000L

/**
 * 메일 없이 화면 흐름을 확인하는 예시. 6자리 숫자면 어떤 인증번호든 맞는 것으로 보고, 시간이 지나면 만료로 본다.
 * 서버에서만 생기는 결과는 예시 계정으로 확인한다: [LockedEmail]은 로그인 잠김, [WithdrawnEmail]은 탈퇴 취소,
 * 이 두 계정과 [RegisteredEmail]은 가입 때 이미 가입된 이메일이다. 그 밖의 이메일 형식·비밀번호면 로그인에 성공한다.
 */
class SampleAuthRepository(private val now: () -> Long) : AuthRepository {
    private val expiries = mutableMapOf<String, Long>()

    override suspend fun logIn(email: String, password: String): LoginResult {
        val address = email.trim().lowercase()
        return when {
            !emailPattern.matches(address) || password.isEmpty() -> LoginResult.WrongCredentials
            address == LockedEmail -> LoginResult.Locked
            else -> LoginResult.Success(withdrawalCancelled = address == WithdrawnEmail)
        }
    }

    override fun logOut() = Unit

    override suspend fun sendCode(email: String, purpose: VerificationPurpose): SendCodeResult {
        if (purpose == VerificationPurpose.SignUp && email.trim().lowercase() in registeredEmails) return SendCodeResult.EmailAlreadyExists
        val sentAt = now()
        val id = "sample-${expiries.size + 1}"
        expiries[id] = sentAt + VerificationCodeValidMillis
        return SendCodeResult.Sent(id, sentAt + VerificationCodeValidMillis, sentAt + VerificationResendMillis)
    }

    override suspend fun confirmCode(verificationId: String, code: String): ConfirmCodeResult {
        val expiresAt = expiries[verificationId] ?: return ConfirmCodeResult.Expired
        return if (now() >= expiresAt) ConfirmCodeResult.Expired else ConfirmCodeResult.Confirmed("sample-token-$verificationId")
    }

    companion object {
        const val RegisteredEmail = "registered@example.com"
        const val LockedEmail = "locked@example.com"
        const val WithdrawnEmail = "withdrawn@example.com"
        private val registeredEmails = setOf(RegisteredEmail, LockedEmail, WithdrawnEmail)
        private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    }
}
