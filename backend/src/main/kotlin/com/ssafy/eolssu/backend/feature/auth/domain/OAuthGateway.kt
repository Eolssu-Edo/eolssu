package com.ssafy.eolssu.backend.feature.auth.domain

enum class SocialProvider { KAKAO, GOOGLE, NAVER, APPLE }

/**
 * 제공자 식별자·이메일이 로그에 남지 않게 data class로 두지 않는다.
 * [providerToken]은 연결 해제·탈퇴 때 제공자에 해지를 요청할 토큰(Apple refresh token)이다. 저장할 때는 [ProviderTokenCipher]로 봉인한다.
 */
class OAuthIdentity(val subject: String, val email: String?, val emailVerified: Boolean, val flowStartedAt: java.time.Instant? = null, val providerToken: String? = null)

interface OAuthGateway {
    fun ready(provider: SocialProvider): Boolean
    fun allows(provider: SocialProvider, redirectUri: String): Boolean
    fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String): String
    fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String): OAuthIdentity
    /** 제공자 토큰 해지. 해지 API를 쓰지 않는 제공자는 아무것도 하지 않는다. 실패하면 예외를 던진다. */
    fun revoke(provider: SocialProvider, token: String) {}
}

class OAuthRemoteUnavailable : RuntimeException("OAuth provider unavailable")

/** 제공자 토큰을 DB에 두기 전에 봉인한다. [open]은 키가 다르거나 값이 손상되면 null이다. */
interface ProviderTokenCipher {
    fun seal(token: String): String
    fun open(sealed: String): String?
}
