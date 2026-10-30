package com.ssafy.eolssu.feature.profile

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.member_api_session_expired
import eolssu.shared.generated.resources.member_api_social_unavailable
import eolssu.shared.generated.resources.social_auth_cancelled
import eolssu.shared.generated.resources.social_auth_expired
import eolssu.shared.generated.resources.social_auth_invalid
import eolssu.shared.generated.resources.social_auth_no_browser
import eolssu.shared.generated.resources.social_auth_unavailable
import org.jetbrains.compose.resources.StringResource

/** MY-05 간편 로그인 연결 진행 단계. */
sealed interface SocialLinkStep {
    /** 서버 start를 기다린다. */
    data object Starting : SocialLinkStep

    /** 외부 브라우저의 제공자 화면에서 진행 중이다. */
    data object InBrowser : SocialLinkStep

    /** 복귀 주소의 코드로 연결 완료 요청을 보냈다. */
    data object Completing : SocialLinkStep

    /** 더 진행할 수 없다. '다시 시도'는 start부터 새로 한다. */
    data class Stopped(val problem: SocialLinkProblem) : SocialLinkStep

    /** 이 제공자 계정이 다른 얼쑤 계정에 연결돼 있다. MY-05 안내 창의 확인 뒤 마이페이지로 돌아간다. */
    data object InUse : SocialLinkStep

    /** 연결했다. 마이페이지로 돌아가 안내를 띄운다. */
    data object Linked : SocialLinkStep
}

/** 멈춘 이유. [message]의 `%1$s`에는 제공자 이름을 넣는다. [retryable]이 아니면 '확인'으로 돌아간다. */
enum class SocialLinkProblem(val message: StringResource, val retryable: Boolean = true) {
    Unsupported(Res.string.member_api_social_unavailable, retryable = false),
    SessionExpired(Res.string.member_api_session_expired, retryable = false),
    NoBrowser(Res.string.social_auth_no_browser),
    Cancelled(Res.string.social_auth_cancelled),
    Expired(Res.string.social_auth_expired),
    Invalid(Res.string.social_auth_invalid),
    Unavailable(Res.string.social_auth_unavailable),
}
