package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.SocialProvider

/**
 * AUTH-09 계정 연결 확인 상태. [provider]·[email]·[needsPassword]는 간편 로그인 409 응답의 계정 연결 제안에서 온다.
 * 제안이 없거나 10분이 지났으면 [expired]로 간편 로그인부터 다시 하게 한다. [error]는 비밀번호 칸이 없을 때도 보이는 요청 오류다.
 */
data class AccountLinkUiState(
    val provider: SocialProvider = SocialProvider.Kakao,
    val email: String = "",
    val needsPassword: Boolean = false,
    val password: String = "",
    val passwordError: FieldError? = null,
    val error: FieldError? = null,
    val linking: Boolean = false,
    val expired: Boolean = false,
)
