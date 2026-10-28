package com.ssafy.eolssu.core.model

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_provider_google
import eolssu.shared.generated.resources.auth_provider_kakao
import eolssu.shared.generated.resources.auth_provider_naver
import org.jetbrains.compose.resources.StringResource

/** 간편 로그인 제공자. 로그인(AUTH-04·05)과 마이페이지 연결(MY-01·05)이 함께 쓴다. [mark]는 제공사 공식 버튼을 붙이기 전까지 쓰는 글자 표시다. */
enum class SocialProvider(val displayName: StringResource, val mark: String) {
    Kakao(Res.string.auth_provider_kakao, "K"),
    Naver(Res.string.auth_provider_naver, "N"),
    Google(Res.string.auth_provider_google, "G"),
}
