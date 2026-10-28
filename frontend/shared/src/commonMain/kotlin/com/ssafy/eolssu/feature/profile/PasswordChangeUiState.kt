package com.ssafy.eolssu.feature.profile

/** MY-03 비밀번호 변경 입력 상태. 오류는 '비밀번호 바꾸기'를 누른 뒤에만 보이고, 그 칸을 고치면 사라진다. */
data class PasswordChangeUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val currentError: ProfileFieldError? = null,
    val newError: ProfileFieldError? = null,
    val confirmError: ProfileFieldError? = null,
)
