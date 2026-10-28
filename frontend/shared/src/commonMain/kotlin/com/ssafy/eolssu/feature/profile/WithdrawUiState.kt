package com.ssafy.eolssu.feature.profile

/** MY-04 회원 탈퇴 입력 상태. */
data class WithdrawUiState(
    val password: String = "",
    val error: ProfileFieldError? = null,
)
