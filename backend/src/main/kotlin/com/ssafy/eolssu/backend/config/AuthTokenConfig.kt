package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.api.AuthCookies
import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisRefreshTokenRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.RefreshTokenMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.JwtAccessTokenSigner
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db")
class AuthTokenConfig {
    @Bean
    fun accessTokenSigner(properties: SecurityProperties): AccessTokenSigner = JwtAccessTokenSigner(properties.jwtSecret)

    @Bean
    fun refreshTokenRepository(mapper: RefreshTokenMapper): RefreshTokenRepository = MyBatisRefreshTokenRepository(mapper)

    @Bean
    fun authTokenIssuer(signer: AccessTokenSigner, refreshTokens: RefreshTokenRepository, secrets: VerificationSecrets, clock: Clock) =
        AuthTokenIssuer(signer, refreshTokens, secrets, clock)

    // 로컬 HTTP 개발에서만 AUTH_COOKIE_SECURE=false로 끈다.
    @Bean
    fun authCookies(@Value("\${eolssu.auth.cookie-secure:true}") secure: Boolean) = AuthCookies(secure)
}
