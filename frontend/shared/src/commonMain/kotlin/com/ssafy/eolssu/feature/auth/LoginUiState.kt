package com.ssafy.eolssu.feature.auth

/** AUTH-04 이메일 로그인 입력 상태. 실패 문구는 비밀번호 칸 아래에 띄운다. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val error: FieldError? = null,
)
