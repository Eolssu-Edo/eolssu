package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import org.springframework.boot.context.properties.ConfigurationProperties
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider

@ConfigurationProperties("eolssu.auth.oauth")
class OAuthProviderProperties(
    val kakao: OAuthClientSettings = OAuthClientSettings(),
    val google: OAuthClientSettings = OAuthClientSettings(),
    val naver: OAuthClientSettings = OAuthClientSettings(),
) {
    fun client(provider: SocialProvider) = when (provider) {
        SocialProvider.KAKAO -> kakao
        SocialProvider.GOOGLE -> google
        SocialProvider.NAVER -> naver
    }
}

/** 비밀값이 자동 toString으로 노출되지 않게 data class를 쓰지 않는다. */
class OAuthClientSettings(val clientId: String = "", val clientSecret: String = "", val redirectUris: List<String> = emptyList())
