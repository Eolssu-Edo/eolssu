package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.SignedInMember
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
 * 로그인에 성공하면 응답의 `userId`를, 로그아웃하면 빈 값을 [member]에 남긴다(회원별 기록을 다른 회원으로 보내지 않기 위해).
 * 간편 로그인 완료·계정 연결도 로그인이므로 같은 방식으로 [member]를 바꾼다. 둘 다 1회용 값을 써서 HTTP 클라이언트가 401에 재전송하지 않는다.
 */
class ApiAuthRepository(
    private val client: HttpClient,
    private val cookies: SecureCookiesStorage?,
    private val member: SignedInMember,
    private val scope: CoroutineScope,
) : AuthRepository {
    private var signingOut: Job? = null

    override suspend fun logIn(email: String, password: String): LoginResult {
        // 로그아웃이 끝나기 전에 로그인하면 새 쿠키가 지워질 수 있다.
        signingOut?.join()
        // 쿠키는 200 응답에서만 바뀌므로 회원도 그때만 바꾼다. 실패하면 이전 회원(만료된 세션 포함)이 그대로 남는다.
        return member.changing {
            val response = client.sendOrNull {
                post("/api/auth/sign-in") {
                    contentType(ContentType.Application.Json)
                    setBody(SignInRequest(email.trim(), password))
                }
            } ?: return@changing LoginResult.Unavailable
            when (response.status.value) {
                200 -> {
                    val signedIn = response.apiResponse<SignedIn>()?.takeIf { it.isSuccess }?.result
                        ?.takeIf { it.userId.isNotBlank() } ?: return@changing LoginResult.Unavailable
                    member.set(signedIn.userId)
                    LoginResult.Success(withdrawalCancelled = signedIn.withdrawalCancelled)
                }
                400, 401 -> LoginResult.WrongCredentials
                429 -> LoginResult.Locked(response.apiResponse<RetryError>()?.result?.details?.retryAvailableAt?.toMillis())
                else -> LoginResult.Unavailable
            }
        }
    }

    override fun logOut() {
        signingOut = scope.launch {
            member.changing {
                member.set(null)
                client.sendOrNull { post("/api/auth/sign-out") }
                cookies?.clear()
            }
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

    override suspend fun startSocialLogIn(provider: SocialProvider): OAuthStartResult =
        startOAuth(client, "/api/auth/oauth/${provider.pathName}/start", provider)

    override suspend fun completeSocialLogIn(callback: OAuthCallback): SocialLoginResult {
        signingOut?.join()
        return member.changing {
            val response = client.sendOrNull {
                post("/api/auth/oauth/${callback.provider.pathName}") {
                    contentType(ContentType.Application.Json)
                    setBody(OAuthComplete(callback.authorizationCode, callback.redirectUri, callback.state))
                }
            } ?: return@changing SocialLoginResult.Unavailable
            when (response.status.value) {
                200 -> {
                    val signedIn = response.apiResponse<SignedIn>()?.takeIf { it.isSuccess }?.result
                        ?.takeIf { it.userId.isNotBlank() } ?: return@changing SocialLoginResult.Unavailable
                    member.set(signedIn.userId)
                    SocialLoginResult.Success(withdrawalCancelled = signedIn.withdrawalCancelled)
                }
                409 -> if (response.apiResponse<VerificationError>()?.result?.errorCode == "SOCIAL_SIGN_UP_REQUIRED") {
                    val details = response.apiResponse<SignUpError>()?.result?.details ?: return@changing SocialLoginResult.Unavailable
                    val expiry = details.expiresAt.toMillis() ?: return@changing SocialLoginResult.Unavailable
                    SocialLoginResult.SignUpRequired(SignUpTicket(callback.provider, details.signupTicket, expiry))
                } else {
                    val details = response.apiResponse<MergeError>()?.result?.details ?: return@changing SocialLoginResult.Unavailable
                    val expiry = details.expiresAt.toMillis() ?: return@changing SocialLoginResult.Unavailable
                    SocialLoginResult.MergeAvailable(
                        MergeOffer(callback.provider, details.maskedEmail, !details.providerEmailVerified, details.mergeTicket, expiry),
                    )
                }
                401 -> if (response.apiResponse<VerificationError>()?.result?.errorCode == "SOCIAL_ACCOUNT_NOT_REGISTERED") {
                    SocialLoginResult.NotRegistered
                } else {
                    SocialLoginResult.Restart
                }
                400, 410 -> SocialLoginResult.Restart
                else -> SocialLoginResult.Unavailable
            }
        }
    }

    override suspend fun mergeAccount(offer: MergeOffer, password: String?): MergeResult {
        signingOut?.join()
        return member.changing {
            val response = client.sendOrNull {
                post("/api/auth/oauth/merge") {
                    contentType(ContentType.Application.Json)
                    setBody(Merge(offer.ticket, password?.takeIf { offer.needsPassword }))
                }
            } ?: return@changing MergeResult.Unavailable
            when (response.status.value) {
                200 -> {
                    val merged = response.apiResponse<SignedUp>()?.takeIf { it.isSuccess }?.result
                        ?.takeIf { it.userId.isNotBlank() } ?: return@changing MergeResult.Unavailable
                    member.set(merged.userId)
                    MergeResult.Success
                }
                422 -> MergeResult.PasswordRequired
                401 -> MergeResult.WrongPassword
                429 -> MergeResult.Locked
                400, 410 -> MergeResult.Expired
                else -> MergeResult.Unavailable
            }
        }
    }

    override suspend fun socialSignUp(request: SocialRegistration): MemberResult {
        signingOut?.join()
        return member.changing {
            val response = client.sendOrNull {
                post("/api/auth/oauth/sign-up") {
                    contentType(ContentType.Application.Json)
                    setBody(SocialSignUp(request.ticket.ticket, request.nickname, request.agreements, request.instrumentId))
                }
            } ?: return@changing MemberResult.Unavailable
            if (response.status.value == 201) {
                val signedUp = response.apiResponse<SignedUp>()?.takeIf { it.isSuccess }?.result
                    ?.takeIf { it.userId.isNotBlank() } ?: return@changing MemberResult.Unavailable
                member.set(signedUp.userId)
                return@changing MemberResult.Done
            }
            memberFailure(response.status.value, response.apiResponse<VerificationError>()?.result?.errorCode)
        }
    }

    @Serializable private class SocialSignUp(val signupTicket: String, val nickname: String, val agreements: List<TermAgreement>, val instrumentId: String? = null)
    @Serializable private class SignUpError(val errorCode: String, val details: SignUpDetails? = null)
    @Serializable private class SignUpDetails(val signupTicket: String, val expiresAt: String)
    @Serializable private class OAuthComplete(val authorizationCode: String, val redirectUri: String, val state: String)
    @Serializable private class Merge(val mergeTicket: String, val password: String? = null)
    @Serializable private class MergeError(val errorCode: String, val details: MergeDetails? = null)
    @Serializable private class MergeDetails(val mergeTicket: String, val maskedEmail: String, val providerEmailVerified: Boolean, val expiresAt: String)
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

    @Serializable private class RetryError(val errorCode: String, val details: RetryDetails? = null)
    @Serializable private class RetryDetails(val retryAvailableAt: String? = null)
}

internal fun String.toMillis(): Long? = runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()

/** 로그인·연결 start(`{redirectUri}` → `authorizationUrl`·`expiresAt`). 401은 연결 start에서만 생긴다. */
internal suspend fun startOAuth(client: HttpClient, path: String, provider: SocialProvider): OAuthStartResult {
    val response = client.sendOrNull {
        post(path) { contentType(ContentType.Application.Json); setBody(OAuthStart(OAuthRedirect.uriFor(provider))) }
    } ?: return OAuthStartResult.Unavailable
    return when (response.status.value) {
        200 -> {
            val result = response.apiResponse<OAuthStarted>()?.takeIf { it.isSuccess }?.result ?: return OAuthStartResult.Unavailable
            val expiry = result.expiresAt.toMillis() ?: return OAuthStartResult.Unavailable
            OAuthStartResult.Started(result.authorizationUrl, expiry)
        }
        401 -> OAuthStartResult.Unauthenticated
        else -> OAuthStartResult.Unavailable
    }
}

@Serializable private class OAuthStart(val redirectUri: String)
@Serializable private class OAuthStarted(val authorizationUrl: String, val expiresAt: String)

internal fun memberFailure(status: Int, code: String? = null): MemberResult = when {
    code == "EMAIL_NOT_VERIFIED" -> MemberResult.VerificationRequired
    code == "TERMS_AGREEMENT_REQUIRED" -> MemberResult.TermsRequired
    code == "EMAIL_ALREADY_EXISTS" -> MemberResult.EmailExists
    code == "SIGNUP_TICKET_EXPIRED" || code == "WITHDRAWAL_TICKET_EXPIRED" -> MemberResult.TicketExpired
    code == "SOCIAL_ACCOUNT_IN_USE" -> MemberResult.SocialAccountInUse
    code == "LAST_LOGIN_METHOD" -> MemberResult.LastLoginMethod
    status == 401 -> MemberResult.Unauthenticated
    status == 429 -> MemberResult.RateLimited
    status == 400 || status == 422 -> MemberResult.InvalidInput
    else -> MemberResult.Unavailable
}
