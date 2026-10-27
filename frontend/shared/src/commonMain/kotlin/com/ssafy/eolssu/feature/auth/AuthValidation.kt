package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_error_email_empty
import eolssu.shared.generated.resources.auth_error_email_invalid
import eolssu.shared.generated.resources.auth_error_login_failed
import eolssu.shared.generated.resources.auth_error_nickname_empty
import eolssu.shared.generated.resources.auth_error_password_empty
import eolssu.shared.generated.resources.auth_error_password_mismatch
import org.jetbrains.compose.resources.StringResource

/** 입력칸 아래에 띄우는 오류. */
enum class FieldError(val message: StringResource) {
    LoginFailed(Res.string.auth_error_login_failed),
    EmailEmpty(Res.string.auth_error_email_empty),
    EmailInvalid(Res.string.auth_error_email_invalid),
    PasswordEmpty(Res.string.auth_error_password_empty),
    PasswordMismatch(Res.string.auth_error_password_mismatch),
    NicknameEmpty(Res.string.auth_error_nickname_empty),
}

/**
 * 서버가 없을 때의 최소 입력 검사. 서버가 붙으면 계정 확인·이메일 중복·비밀번호 규칙은 서버 응답을 따른다.
 * 비밀번호·닉네임 규칙은 기획에서 '[규칙 안내]' 자리로 남아 있어 비어 있는지만 본다.
 */
object AuthValidation {
    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun isEmail(value: String): Boolean = emailPattern.matches(value.trim())

    /** 이메일 형식이 맞고 비밀번호가 있으면 로그인 성공으로 본다. 아니면 와이어프레임의 로그인 실패 문구를 띄운다. */
    fun loginError(email: String, password: String): FieldError? =
        if (isEmail(email) && password.isNotEmpty()) null else FieldError.LoginFailed

    fun emailError(email: String): FieldError? = when {
        email.isBlank() -> FieldError.EmailEmpty
        !isEmail(email) -> FieldError.EmailInvalid
        else -> null
    }

    fun passwordError(password: String): FieldError? = if (password.isEmpty()) FieldError.PasswordEmpty else null

    /** 비밀번호가 비어 있으면 그쪽 오류만 보이도록 확인칸은 검사하지 않는다. */
    fun passwordConfirmError(password: String, confirm: String): FieldError? =
        if (password.isNotEmpty() && password != confirm) FieldError.PasswordMismatch else null

    fun nicknameError(nickname: String): FieldError? = if (nickname.isBlank()) FieldError.NicknameEmpty else null
}
