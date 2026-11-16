package com.ssafy.eolssu.feature.auth

/** AUTH-08 비밀번호 설정(이메일 가입 3/4)·AUTH-11 새 비밀번호 입력 상태. 오류는 버튼을 누른 뒤에만 보이고, 그 칸을 고치면 사라진다. */
data class PasswordSetupUiState(
    /** 다음 단계로 보내는 중. 버튼에 진행 표시를 보인다(S15P31E102-996). */
    val submitting: Boolean = false,
    val password: String = "",
    val passwordConfirm: String = "",
    val passwordError: FieldError? = null,
    val passwordConfirmError: FieldError? = null,
)
