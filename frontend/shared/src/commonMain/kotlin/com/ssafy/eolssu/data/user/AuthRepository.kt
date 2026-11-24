package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.EmailRule
import com.ssafy.eolssu.core.model.SocialProvider

/**
 * 이메일 로그인·로그아웃과 이메일 인증번호(`/api/auth/email-verifications`).
 * 로그인·로그아웃은 [ApiAuthRepository]가 서버로 하고, 인증번호는 아직 [SampleAuthRepository]가 예시 결과를 돌려준다.
 * 간편 로그인(AUTH-05)과 계정 연결(AUTH-09)도 여기서 서버로 한다. 시도 상태·복귀 주소 확인은 [OAuthAttempts]가 맡는다.
 */
interface AuthRepository {
    suspend fun logIn(email: String, password: String): LoginResult

    /** 서버 로그아웃을 요청하고 기기의 로그인 쿠키를 지운다. 화면이 바로 넘어가도 끝까지 하도록 기다리지 않는다. */
    fun logOut()

    /**
     * 지난 실행의 로그아웃이 서버에 닿지 못했으면 다시 보낸다. 앱 시작 때 한 번 부르고 기다리지 않는다.
     * 쿠키를 앱이 지울 수 없는 웹만 남겨 두고, 앱은 로그아웃 때 기기의 쿠키를 지우므로 아무 일도 하지 않는다.
     */
    fun retrySignOut() = Unit

    suspend fun sendCode(email: String, purpose: VerificationPurpose): SendCodeResult

    suspend fun confirmCode(verificationId: String, code: String): ConfirmCodeResult

    suspend fun signUp(request: Registration): MemberResult = MemberResult.Unavailable
    suspend fun resetPassword(token: String, password: String): MemberResult = MemberResult.Unavailable

    /** 간편 로그인 start. 받은 제공자 주소를 외부 브라우저로 연다. */
    suspend fun startSocialLogIn(provider: SocialProvider): OAuthStartResult = OAuthStartResult.Unavailable

    /** 복귀 주소의 코드·state로 간편 로그인을 끝낸다. state가 1회용이라 실패해도 같은 [callback]으로 다시 부르지 않는다. */
    suspend fun completeSocialLogIn(callback: OAuthCallback): SocialLoginResult = SocialLoginResult.Unavailable

    /** 계정 연결 확인(AUTH-09)에서 [offer]의 계정에 제공자를 연결하고 로그인한다. [password]는 [MergeOffer.needsPassword]일 때만 보낸다. */
    suspend fun mergeAccount(offer: MergeOffer, password: String?): MergeResult = MergeResult.Unavailable

    /** 소셜 가입. 성공하면 로그인한 상태가 된다. 티켓이 1회용이라 실패해도 같은 요청을 자동으로 다시 보내지 않는다. */
    suspend fun socialSignUp(request: SocialRegistration): MemberResult = MemberResult.Unavailable
}

/** 인증번호를 받는 목적(`purpose`). 계정 연결(`ACCOUNT_MERGE`)은 연결 화면이 인증번호를 쓰기로 정해지면 더한다. */
enum class VerificationPurpose { SignUp, PasswordReset }

sealed interface LoginResult {
    /** [withdrawalCancelled]: 탈퇴 대기 계정으로 로그인해 탈퇴가 취소됐다(`withdrawalCancelled`). */
    data class Success(val withdrawalCancelled: Boolean) : LoginResult

    data object WrongCredentials : LoginResult

    /** 같은 이메일로 15분 안에 5번 실패해 15분 동안 로그인할 수 없다(429). [retryAvailableAtMillis]부터 다시 시도할 수 있고, 서버가 알려 주지 않으면 null. */
    data class Locked(val retryAvailableAtMillis: Long?) : LoginResult

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
            !EmailRule.isValid(address) || password.isEmpty() -> LoginResult.WrongCredentials
            address == LockedEmail -> LoginResult.Locked(now() + 15 * 60_000L)
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
    }
}
