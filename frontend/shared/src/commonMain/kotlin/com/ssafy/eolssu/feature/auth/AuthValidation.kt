package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.EmailRule
import com.ssafy.eolssu.core.model.NicknameRule
import com.ssafy.eolssu.core.model.PasswordRule
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_error_email_empty
import eolssu.shared.generated.resources.auth_error_email_exists
import eolssu.shared.generated.resources.auth_error_email_invalid
import eolssu.shared.generated.resources.auth_error_login_failed
import eolssu.shared.generated.resources.auth_error_login_unavailable
import eolssu.shared.generated.resources.auth_error_nickname_empty
import eolssu.shared.generated.resources.auth_error_password_empty
import eolssu.shared.generated.resources.auth_error_password_mismatch
import eolssu.shared.generated.resources.auth_error_password_rule
import org.jetbrains.compose.resources.StringResource
import eolssu.shared.generated.resources.member_api_unavailable
import eolssu.shared.generated.resources.member_api_session_expired
import eolssu.shared.generated.resources.member_api_rate_limited
import eolssu.shared.generated.resources.member_api_invalid
import eolssu.shared.generated.resources.member_api_verify_again
import eolssu.shared.generated.resources.member_api_terms_again
import eolssu.shared.generated.resources.member_api_instrument_unavailable
import eolssu.shared.generated.resources.my_error_password_wrong
import eolssu.shared.generated.resources.social_auth_merge_locked
import eolssu.shared.generated.resources.social_signup_email_exists
import eolssu.shared.generated.resources.social_signup_expired
import eolssu.shared.generated.resources.social_signup_in_use
import com.ssafy.eolssu.data.user.MemberResult

/** 입력칸 아래에 띄우는 오류. */
enum class FieldError(val message: StringResource) {
    Unavailable(Res.string.member_api_unavailable),
    SessionExpired(Res.string.member_api_session_expired),
    RateLimited(Res.string.member_api_rate_limited),
    InvalidInput(Res.string.member_api_invalid),
    VerifyAgain(Res.string.member_api_verify_again),
    TermsAgain(Res.string.member_api_terms_again),
    InstrumentUnavailable(Res.string.member_api_instrument_unavailable),
    LoginFailed(Res.string.auth_error_login_failed),
    LoginUnavailable(Res.string.auth_error_login_unavailable),
    EmailEmpty(Res.string.auth_error_email_empty),
    EmailInvalid(Res.string.auth_error_email_invalid),
    EmailAlreadyExists(Res.string.auth_error_email_exists),
    PasswordEmpty(Res.string.auth_error_password_empty),
    PasswordRule(Res.string.auth_error_password_rule),
    PasswordMismatch(Res.string.auth_error_password_mismatch),
    NicknameEmpty(Res.string.auth_error_nickname_empty),

    /** 계정 연결(AUTH-09) 본인 확인 비밀번호가 맞지 않다. */
    MergeWrongPassword(Res.string.my_error_password_wrong),

    /** 계정 연결 비밀번호를 15분 안에 5번 틀려 잠겼다. */
    MergeLocked(Res.string.social_auth_merge_locked),

    /** 소셜 가입 티켓이 없거나 만료·재사용됐다(10분·1회). 간편 로그인부터 다시 한다. */
    SignUpExpired(Res.string.social_signup_expired),

    /** 가입하는 사이 이 제공자 계정이 다른 얼쑤 계정에 연결됐다. */
    SocialAccountInUse(Res.string.social_signup_in_use),

    /** 가입하는 사이 제공자 이메일로 다른 계정이 생겼다. 간편 로그인을 다시 하면 계정 연결을 안내한다. */
    SocialEmailExists(Res.string.social_signup_email_exists),
}

/**
 * 서버에 보내기 전의 입력 검사. 계정 확인·이메일 중복·인증번호는 서버 응답을 따른다.
 * 비밀번호 규칙은 서버와 같은 '영문·숫자·특수문자를 포함한 8~128자'다. 닉네임은 서버와 같은 공백이 아닌 최대 10자([NicknameRule])다.
 */
object AuthValidation {
    private val codePattern = Regex("""^\d{6}$""")

    /** 앞뒤 공백만 잘라 서버와 같은 [EmailRule]로 검사한다. */
    fun isEmail(value: String): Boolean = EmailRule.isValid(value.trim())

    /** 인증번호는 6자리 숫자다. 앞자리 0을 지키려고 문자열 그대로 보낸다. */
    fun isCode(value: String): Boolean = codePattern.matches(value)

    fun emailError(email: String): FieldError? = when {
        email.isBlank() -> FieldError.EmailEmpty
        !isEmail(email) -> FieldError.EmailInvalid
        else -> null
    }

    fun passwordError(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.PasswordEmpty
        !PasswordRule.isValid(password) -> FieldError.PasswordRule
        else -> null
    }

    /** 비밀번호가 비어 있으면 그쪽 오류만 보이도록 확인칸은 검사하지 않는다. */
    fun passwordConfirmError(password: String, confirm: String): FieldError? =
        if (password.isNotEmpty() && password != confirm) FieldError.PasswordMismatch else null

    fun nicknameError(nickname: String): FieldError? = when {
        nickname.isBlank() -> FieldError.NicknameEmpty
        NicknameRule.isTooLong(nickname) -> FieldError.InvalidInput
        else -> null
    }
}

/** [social] 가입이면 이메일 중복을 소셜 가입 안내로 바꾼다(입력한 이메일이 없어서). */
internal fun MemberResult.fieldError(social: Boolean = false): FieldError = when (this) {
    MemberResult.VerificationRequired -> FieldError.VerifyAgain
    MemberResult.TermsRequired -> FieldError.TermsAgain
    MemberResult.EmailExists -> if (social) FieldError.SocialEmailExists else FieldError.EmailAlreadyExists
    MemberResult.TicketExpired -> FieldError.SignUpExpired
    MemberResult.SocialAccountInUse -> FieldError.SocialAccountInUse
    MemberResult.InvalidInput -> FieldError.InvalidInput
    MemberResult.Unauthenticated -> FieldError.SessionExpired
    MemberResult.RateLimited -> FieldError.RateLimited
    else -> FieldError.Unavailable
}
