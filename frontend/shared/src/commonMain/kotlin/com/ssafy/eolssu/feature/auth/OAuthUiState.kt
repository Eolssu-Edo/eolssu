package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.member_api_social_unavailable
import eolssu.shared.generated.resources.social_auth_cancelled
import eolssu.shared.generated.resources.social_auth_expired
import eolssu.shared.generated.resources.social_auth_invalid
import eolssu.shared.generated.resources.social_auth_no_browser
import eolssu.shared.generated.resources.social_auth_unavailable
import org.jetbrains.compose.resources.StringResource

/** AUTH-05 간편 로그인 진행 단계. */
sealed interface OAuthStep {
    /** 서버 start를 기다린다. */
    data object Starting : OAuthStep

    /** 외부 브라우저의 제공자 화면에서 진행 중이다. 복귀 주소로 앱이 다시 열리기를 기다린다. */
    data object InBrowser : OAuthStep

    /** 복귀 주소의 코드로 완료 요청을 보냈다. */
    data object Completing : OAuthStep

    /** 더 진행할 수 없다. '다시 시도'는 start부터 새로 한다(같은 state·코드를 다시 보내지 않음). */
    data class Stopped(val problem: OAuthProblem) : OAuthStep

    /** 연결된 계정도 같은 이메일 계정도 없다. '가입된 계정이 없어요' 창. */
    data object NoAccount : OAuthStep

    /** 탈퇴 대기 계정으로 로그인해 탈퇴가 취소됐다. 안내 창의 확인 뒤 홈. */
    data object WithdrawalCancelled : OAuthStep

    /** 로그인했다. 홈으로 간다. */
    data object LoggedIn : OAuthStep

    /** 같은 이메일로 가입한 계정이 있다. 계정 연결 확인(AUTH-09)으로 간다. */
    data object MergeAvailable : OAuthStep

    /** 처음 온 제공자 계정이다. 약관 동의(동의가 없을 때)·프로필 설정(AUTH-07)으로 가서 가입한다. */
    data object SignUpRequired : OAuthStep
}

/** 멈춘 이유. [message]의 `%1$s`에는 제공자 이름을 넣는다. */
enum class OAuthProblem(val message: StringResource) {
    /** 이 플랫폼은 외부 브라우저 연결이 없다(iOS·웹은 아직 미지원). */
    Unsupported(Res.string.member_api_social_unavailable),
    NoBrowser(Res.string.social_auth_no_browser),
    Cancelled(Res.string.social_auth_cancelled),
    Expired(Res.string.social_auth_expired),
    Invalid(Res.string.social_auth_invalid),

    /** 제공자·서버 장애(503)나 연결 실패. */
    Unavailable(Res.string.social_auth_unavailable),
}

/** 다시 시도로 풀 수 있는 멈춤인지. 미지원 플랫폼은 다시 해도 같다. */
val OAuthProblem.retryable: Boolean get() = this != OAuthProblem.Unsupported
