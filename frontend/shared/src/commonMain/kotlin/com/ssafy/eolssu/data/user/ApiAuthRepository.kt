package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import kotlin.time.Instant
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 이메일 로그인(AUTH-04, `POST /api/auth/sign-in`)과 로그아웃(AUTH-03, `POST /api/auth/sign-out`)을 서버로 한다.
 * 로그인 쿠키는 서버가 내려 주고 HTTP 클라이언트가 보관한다(웹은 브라우저, 앱은 [cookies]).
 * 인증번호·가입·비밀번호 재설정도 같은 서버 계약을 따른다. 예시 저장소로 대체하지 않는다.
 *
 * 로그아웃은 화면이 바로 넘어가도 끝나도록 앱 전체의 [scope]에서 한다. 서버 요청이 실패해도 기기의 쿠키는 지운다.
 */
class ApiAuthRepository(
    private val client: HttpClient,
    private val cookies: SecureCookiesStorage?,
    private val scope: CoroutineScope,
) : AuthRepository {
    private var signingOut: Job? = null

    override suspend fun logIn(email: String, password: String): LoginResult {
        // 로그아웃이 끝나기 전에 로그인하면 새 쿠키가 지워질 수 있다.
        signingOut?.join()
        val response = client.sendOrNull {
            post("/api/auth/sign-in") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(email.trim(), password))
            }
        } ?: return LoginResult.Unavailable
        return when (response.status.value) {
            200 -> response.apiResponse<SignedIn>()?.takeIf { it.isSuccess }?.result
                ?.takeIf { it.userId.isNotBlank() }?.let { LoginResult.Success(it.withdrawalCancelled) } ?: LoginResult.Unavailable
            400, 401 -> LoginResult.WrongCredentials
            429 -> LoginResult.Locked
            else -> LoginResult.Unavailable
        }
    }

    override fun logOut() {
        signingOut = scope.launch {
            client.sendOrNull { post("/api/auth/sign-out") }
            cookies?.clear()
        }
    }

    override suspend fun sendCode(email: String, purpose: VerificationPurpose): SendCodeResult {
        val response = client.sendOrNull {
            post("/api/auth/email-verifications") {
                contentType(ContentType.Application.Json)
                setBody(SendVerification(email.trim(), if (purpose == VerificationPurpose.SignUp) "SIGN_UP" else "PASSWORD_RESET"))
            }
        } ?: return SendCodeResult.Unavailable
        return when (response.status.value) {
            202 -> {
                val result = response.apiResponse<SentVerification>()?.takeIf { it.isSuccess }?.result ?: return SendCodeResult.Unavailable
                val expiry = result.expiresAt.toMillis() ?: return SendCodeResult.Unavailable
                val resend = result.resendAvailableAt.toMillis() ?: return SendCodeResult.Unavailable
                if (!result.verificationId.matches(Regex("[1-9][0-9]*"))) return SendCodeResult.Unavailable
                SendCodeResult.Sent(result.verificationId, expiry, resend)
            }
            409 -> SendCodeResult.EmailAlreadyExists
            429 -> SendCodeResult.RateLimited(response.apiResponse<VerificationError>()?.result?.details?.resendAvailableAt?.toMillis())
            else -> SendCodeResult.Unavailable
        }
    }

    override suspend fun confirmCode(verificationId: String, code: String): ConfirmCodeResult {
        if (!verificationId.matches(Regex("[1-9][0-9]*"))) return ConfirmCodeResult.Expired
        val response = client.sendOrNull {
            post("/api/auth/email-verifications/$verificationId/confirm") {
                contentType(ContentType.Application.Json)
                setBody(ConfirmVerification(code))
            }
        } ?: return ConfirmCodeResult.Unavailable
        return when (response.status.value) {
            200 -> response.apiResponse<VerifiedEmail>()?.takeIf { it.isSuccess }?.result?.takeIf { it.verificationToken.isNotBlank() }
                ?.let { ConfirmCodeResult.Confirmed(it.verificationToken, it.email) } ?: ConfirmCodeResult.Unavailable
            400 -> response.apiResponse<VerificationError>()?.result?.let {
                if (it.errorCode == "VERIFICATION_CODE_MISMATCH") ConfirmCodeResult.Mismatch(it.details?.remainingAttempts ?: 0) else ConfirmCodeResult.Unavailable
            } ?: ConfirmCodeResult.Unavailable
            404, 410 -> ConfirmCodeResult.Expired
            429 -> ConfirmCodeResult.TooManyAttempts
            else -> ConfirmCodeResult.Unavailable
        }
    }

    override suspend fun signUp(request: Registration): MemberResult {
        val response = client.sendOrNull {
            post("/api/auth/sign-up") { contentType(ContentType.Application.Json); setBody(request) }
        } ?: return MemberResult.Unavailable
        if (response.status.value == 201 && response.apiResponse<SignedUp>()?.takeIf { it.isSuccess }?.result?.userId?.isNotBlank() == true) return MemberResult.Done
        return memberFailure(response.status.value, response.apiResponse<VerificationError>()?.result?.errorCode)
    }

    override suspend fun resetPassword(token: String, password: String): MemberResult {
        val response = client.sendOrNull {
            post("/api/auth/password-reset") { contentType(ContentType.Application.Json); setBody(ResetPassword(token, password)) }
        } ?: return MemberResult.Unavailable
        return if (response.status.value == 204) MemberResult.Done
        else memberFailure(response.status.value, response.apiResponse<VerificationError>()?.result?.errorCode)
    }

    @Serializable private class SendVerification(val email: String, val purpose: String)
    @Serializable private class SentVerification(val verificationId: String, val expiresAt: String, val resendAvailableAt: String)
    @Serializable private class ConfirmVerification(val code: String)
    @Serializable private class VerifiedEmail(val verificationToken: String, val email: String)
    @Serializable private class VerificationError(val errorCode: String, val details: VerificationDetails? = null)
    @Serializable private class VerificationDetails(val remainingAttempts: Int? = null, val resendAvailableAt: String? = null)
    @Serializable private class ResetPassword(val verificationToken: String, val newPassword: String)
    @Serializable private class SignedUp(val userId: String)

    @Serializable
    private class SignInRequest(val email: String, val password: String)

    @Serializable
    private class SignedIn(val userId: String, val withdrawalCancelled: Boolean)
}

private fun String.toMillis(): Long? = runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()

internal fun memberFailure(status: Int, code: String? = null): MemberResult = when {
    code == "EMAIL_NOT_VERIFIED" -> MemberResult.VerificationRequired
    code == "TERMS_AGREEMENT_REQUIRED" -> MemberResult.TermsRequired
    code == "EMAIL_ALREADY_EXISTS" -> MemberResult.EmailExists
    status == 401 -> MemberResult.Unauthenticated
    status == 429 -> MemberResult.RateLimited
    status == 400 || status == 422 -> MemberResult.InvalidInput
    else -> MemberResult.Unavailable
}
