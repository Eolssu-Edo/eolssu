package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.application.LoginAttemptLimiter
import com.ssafy.eolssu.backend.feature.auth.application.PasswordChangeService
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisPasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.RefreshTokenMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PasswordChangeConfig {
    @Bean
    fun passwordChangeRepository(users: UserMapper, refreshTokens: RefreshTokenMapper, manager: PlatformTransactionManager): PasswordChangeRepository =
        MyBatisPasswordChangeRepository(users, refreshTokens, manager)

    // 로그인 잠금(LoginAttemptLimiter Bean)과 따로 세도록 Bean으로 두지 않고 이 서비스만 쓰는 인스턴스를 만든다.
    @Bean
    fun passwordChangeService(repository: PasswordChangeRepository, passwordEncoder: PasswordEncoder, tokenIssuer: AuthTokenIssuer, clock: Clock) =
        PasswordChangeService(repository, passwordEncoder, tokenIssuer, LoginAttemptLimiter(), clock)
}
