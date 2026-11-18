package com.ssafy.eolssu.feature.profile

/** MY-02 닉네임 수정 입력 상태. 처음에는 지금 닉네임이 들어 있다. */
data class NicknameEditUiState(
    val nickname: String = "",
    val error: ProfileFieldError? = null,
    /** 저장 요청 중. 저장 버튼에 진행 표시를 보인다. */
    val submitting: Boolean = false,
)
