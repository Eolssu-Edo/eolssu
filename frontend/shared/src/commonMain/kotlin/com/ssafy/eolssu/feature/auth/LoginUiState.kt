package com.ssafy.eolssu.feature.auth

/** AUTH-04 이메일 로그인 입력 상태. 실패 문구는 비밀번호 칸 아래에, 로그인 결과 안내는 [notice] 창으로 띄운다. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val error: FieldError? = null,
    val notice: LoginNotice? = null,
)

/** 로그인 결과 안내 창. */
enum class LoginNotice {
    /** 같은 이메일로 15분 안에 5번 실패해 15분 동안 막혔다. */
    Locked,

    /** 탈퇴 대기 계정으로 로그인해 탈퇴가 취소됐다. 확인을 누르면 홈으로 간다. */
    WithdrawalCancelled,
}
