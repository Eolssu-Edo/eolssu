package com.ssafy.eolssu.backend.feature.auth.domain

enum class SocialProvider { KAKAO, GOOGLE, NAVER }

/** 제공자 식별자·이메일이 로그에 남지 않게 data class로 두지 않는다. */
class OAuthIdentity(val subject: String, val email: String?, val emailVerified: Boolean)

interface OAuthGateway {
    fun ready(provider: SocialProvider): Boolean
    fun allows(provider: SocialProvider, redirectUri: String): Boolean
    fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String): String
    fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String): OAuthIdentity
}

class OAuthRemoteUnavailable : RuntimeException("OAuth provider unavailable")
