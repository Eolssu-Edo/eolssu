package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import org.springframework.boot.context.properties.ConfigurationProperties
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider

@ConfigurationProperties("eolssu.auth.oauth")
class OAuthProviderProperties(
    val kakao: OAuthClientSettings = OAuthClientSettings(),
    val google: OAuthClientSettings = OAuthClientSettings(),
    val naver: OAuthClientSettings = OAuthClientSettings(),
    val apple: AppleSettings = AppleSettings(),
) {
    /** 웹 인증 코드 교환(RestOAuthGateway) 설정. Apple은 [AppleOAuthGateway]가 맡으므로 빈 설정(준비 안 됨)이다. */
    fun client(provider: SocialProvider) = when (provider) {
        SocialProvider.KAKAO -> kakao
        SocialProvider.GOOGLE -> google
        SocialProvider.NAVER -> naver
        SocialProvider.APPLE -> OAuthClientSettings()
    }
}

/** 비밀값이 자동 toString으로 노출되지 않게 data class를 쓰지 않는다. */
class OAuthClientSettings(val clientId: String = "", val clientSecret: String = "", val redirectUris: List<String> = emptyList())

/**
 * Sign in with Apple 설정. [clientId]는 앱 번들 ID, [privateKey]는 Apple Developer에서 받은 .p8(PKCS#8 PEM)이다.
 * 환경 변수에 한 줄로 넣을 수 있게 `\n` 글자를 줄바꿈으로 본다. 비밀값이 toString으로 노출되지 않게 data class를 쓰지 않는다.
 * [devClientId]는 개발 앱(얼쑤 개발) 번들 ID다. Apple Developer에서 [clientId]에 그룹화해 같은 키·같은 Apple 사용자 ID를 쓴다.
 * 요청의 redirectUri가 [devRedirectUris]에 있으면 그 요청은 개발 앱의 것으로 보고 [devClientId]로 교환·검증한다. 둘 중 하나라도 비면 개발 앱은 받지 않는다.
 */
class AppleSettings(
    val clientId: String = "",
    val teamId: String = "",
    val keyId: String = "",
    val privateKey: String = "",
    val redirectUris: List<String> = emptyList(),
    val devClientId: String = "",
    val devRedirectUris: List<String> = emptyList(),
)
