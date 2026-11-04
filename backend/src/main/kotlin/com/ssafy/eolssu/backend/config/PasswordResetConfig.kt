package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.PasswordResetService
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordResetRepository
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.AuthSessionCache

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PasswordResetConfig {
    @Bean
    fun passwordResetRepository(users: UserMapper, verifications: EmailVerificationMapper, refreshTokens: RefreshTokenMapper, manager: PlatformTransactionManager, sessions: AuthSessionCache): PasswordResetRepository =
        MyBatisPasswordResetRepository(users, verifications, refreshTokens, manager, sessions)
    @Bean
    fun passwordResetService(repository: PasswordResetRepository, passwordEncoder: PasswordEncoder, secrets: VerificationSecrets, clock: Clock) =
        PasswordResetService(repository, passwordEncoder, secrets, clock)
}
