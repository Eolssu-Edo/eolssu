package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.PasswordRule
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_error_email_empty
import eolssu.shared.generated.resources.auth_error_email_exists
import eolssu.shared.generated.resources.auth_error_email_invalid
import eolssu.shared.generated.resources.auth_error_login_failed
import eolssu.shared.generated.resources.auth_error_nickname_empty
import eolssu.shared.generated.resources.auth_error_password_empty
import eolssu.shared.generated.resources.auth_error_password_mismatch
import eolssu.shared.generated.resources.auth_error_password_rule
import org.jetbrains.compose.resources.StringResource

/** 입력칸 아래에 띄우는 오류. */
enum class FieldError(val message: StringResource) {
    LoginFailed(Res.string.auth_error_login_failed),
    EmailEmpty(Res.string.auth_error_email_empty),
    EmailInvalid(Res.string.auth_error_email_invalid),
    EmailAlreadyExists(Res.string.auth_error_email_exists),
    PasswordEmpty(Res.string.auth_error_password_empty),
    PasswordRule(Res.string.auth_error_password_rule),
    PasswordMismatch(Res.string.auth_error_password_mismatch),
    NicknameEmpty(Res.string.auth_error_nickname_empty),
}

/**
 * 서버에 보내기 전의 입력 검사. 계정 확인·이메일 중복·인증번호는 서버(지금은 예시 저장소) 응답을 따른다.
 * 비밀번호 규칙은 서버와 같은 '영문·숫자·특수문자를 포함한 8~128자'다. 닉네임 규칙은 아직 '[규칙 안내]' 자리라 비어 있는지만 본다.
 */
object AuthValidation {
    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val codePattern = Regex("""^\d{6}$""")

    fun isEmail(value: String): Boolean = emailPattern.matches(value.trim())

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

    fun nicknameError(nickname: String): FieldError? = if (nickname.isBlank()) FieldError.NicknameEmpty else null
}
