package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.SocialProvider

/**
 * MY-04 회원 탈퇴 입력 상태. [reauth]는 간편 로그인 재인증으로 탈퇴하는 중일 때의 진행 상태다.
 * [withdrawn]이 되면 Route가 로그인 화면으로 보낸다(재인증은 브라우저에서 돌아온 뒤에 끝나서, 버튼을 눌러 기다리는 결과가 없다).
 */
data class WithdrawUiState(
    val password: String = "",
    val error: ProfileFieldError? = null,
    val reauth: WithdrawReauth? = null,
    val withdrawn: Boolean = false,
)

/** 간편 로그인 재인증 진행. 멈추면 [problem]에 이유가 있고, 다시 누르면 처음부터 한다. */
data class WithdrawReauth(val provider: SocialProvider, val step: Step, val problem: SocialLinkProblem? = null) {
    enum class Step { Starting, InBrowser, Completing, Stopped }

    /** 제공자 화면에 다녀오거나 서버를 기다리는 중이라 다른 탈퇴 동작을 막는다. */
    val busy: Boolean get() = step != Step.Stopped
}
