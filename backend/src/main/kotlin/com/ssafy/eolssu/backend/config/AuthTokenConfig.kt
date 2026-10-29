package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.security.ActiveMemberCheck
import com.ssafy.eolssu.backend.feature.auth.api.AuthCookies
import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.application.SignOutService
import com.ssafy.eolssu.backend.feature.auth.application.TokenRefreshService
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisRefreshTokenRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.RefreshTokenMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.JwtAccessTokenSigner
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class AuthTokenConfig {
    @Bean
    fun accessTokenSigner(properties: SecurityProperties): AccessTokenSigner = JwtAccessTokenSigner(properties.jwtSecret)

    // 발급(RefreshTokenRepository)과 재발급·로그아웃(TokenRefreshRepository)이 같은 저장소를 쓴다.
    @Bean
    fun refreshTokenRepository(mapper: RefreshTokenMapper, users: UserMapper, manager: PlatformTransactionManager) =
        MyBatisRefreshTokenRepository(mapper, users, manager)

    @Bean
    fun authTokenIssuer(signer: AccessTokenSigner, refreshTokens: RefreshTokenRepository, secrets: VerificationSecrets, clock: Clock, properties: SecurityProperties) =
        AuthTokenIssuer(signer, refreshTokens, secrets, clock, properties.nonExpiringUserIds)

    @Bean
    fun nonExpiringSessionCheck(mapper: RefreshTokenMapper) = NonExpiringSessionCheck { userId, hash ->
        mapper.hasActiveNonExpiringSession(userId, hash)
    }

    @Bean
    fun tokenRefreshService(repository: TokenRefreshRepository, tokenIssuer: AuthTokenIssuer, secrets: VerificationSecrets, clock: Clock) =
        TokenRefreshService(repository, tokenIssuer, secrets, clock)

    @Bean
    fun signOutService(repository: TokenRefreshRepository, secrets: VerificationSecrets, clock: Clock) = SignOutService(repository, secrets, clock)

    // 보안 필터가 요청마다 토큰의 회원이 ACTIVE인지 확인한다(USR-05 규칙 2).
    @Bean
    fun activeMemberCheck(users: UserMapper) = ActiveMemberCheck { userId -> users.findStatus(userId) == "ACTIVE" }

    // 로컬 HTTP 개발에서만 AUTH_COOKIE_SECURE=false로 끈다.
    @Bean
    fun authCookies(@Value("\${eolssu.auth.cookie-secure:true}") secure: Boolean) = AuthCookies(secure)
}
