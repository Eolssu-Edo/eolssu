package com.ssafy.eolssu.feature.auth

/** AUTH-06 이메일 가입 1/2 입력 상태. 오류는 '다음'을 누른 뒤에만 보이고, 그 칸을 고치면 사라진다. */
data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val passwordConfirmError: FieldError? = null,
)
