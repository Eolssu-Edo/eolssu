package com.ssafy.eolssu.feature.auth

/** AUTH-08 비밀번호 설정(가입 2/3)·AUTH-11 새 비밀번호 입력 상태. 오류는 버튼을 누른 뒤에만 보이고, 그 칸을 고치면 사라진다. */
data class PasswordSetupUiState(
    val password: String = "",
    val passwordConfirm: String = "",
    val passwordError: FieldError? = null,
    val passwordConfirmError: FieldError? = null,
)
