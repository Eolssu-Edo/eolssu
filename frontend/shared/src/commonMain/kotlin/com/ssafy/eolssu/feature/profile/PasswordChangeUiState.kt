package com.ssafy.eolssu.feature.profile

/** MY-03 비밀번호 변경 입력 상태. 오류는 '비밀번호 바꾸기'를 누른 뒤에만 보이고, 그 칸을 고치면 사라진다. */
data class PasswordChangeUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val currentError: ProfileFieldError? = null,
    val newError: ProfileFieldError? = null,
    val confirmError: ProfileFieldError? = null,
    /** 어느 칸 탓도 아닌 실패(서버 오류·로그인 만료·요청 제한). 칸 대신 화면 아래에 보인다(S15P31E102-996). */
    val formError: ProfileFieldError? = null,
    /** 변경 요청 중. 완료 버튼에 진행 표시를 보인다. */
    val submitting: Boolean = false,
)
