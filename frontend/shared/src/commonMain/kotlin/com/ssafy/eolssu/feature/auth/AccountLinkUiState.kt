package com.ssafy.eolssu.feature.auth

/** AUTH-09 계정 연결 확인 입력 상태. 비밀번호는 제공자가 이메일 확인 여부를 주지 않았을 때만 쓴다. */
data class AccountLinkUiState(
    val password: String = "",
    val passwordError: FieldError? = null,
)
