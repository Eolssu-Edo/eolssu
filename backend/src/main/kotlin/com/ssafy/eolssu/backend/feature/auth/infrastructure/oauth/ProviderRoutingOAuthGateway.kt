package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.ssafy.eolssu.backend.feature.auth.domain.OAuthGateway
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider

/** Apple은 [apple](기본 로그인 코드 교환), 나머지 제공자는 [web](웹 인증 코드 교환)으로 보낸다. */
class ProviderRoutingOAuthGateway(private val web: OAuthGateway, private val apple: OAuthGateway) : OAuthGateway {
    private fun of(provider: SocialProvider) = if (provider == SocialProvider.APPLE) apple else web
    override fun ready(provider: SocialProvider) = of(provider).ready(provider)
    override fun allows(provider: SocialProvider, redirectUri: String) = of(provider).allows(provider, redirectUri)
    override fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String) =
        of(provider).authorizationUrl(provider, redirectUri, state, verifier)
    override fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String) =
        of(provider).identify(provider, redirectUri, code, state, verifier)
    override fun revoke(provider: SocialProvider, token: String) = of(provider).revoke(provider, token)
}
