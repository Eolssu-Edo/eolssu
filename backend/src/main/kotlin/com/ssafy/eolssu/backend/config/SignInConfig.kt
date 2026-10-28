package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.application.LoginAttemptLimiter
import com.ssafy.eolssu.backend.feature.auth.application.SignInService
import com.ssafy.eolssu.backend.feature.auth.domain.SignInRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisSignInRepository
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db")
class SignInConfig {
    @Bean
    fun signInRepository(users: UserMapper, manager: PlatformTransactionManager): SignInRepository = MyBatisSignInRepository(users, manager)

    @Bean
    fun loginAttemptLimiter() = LoginAttemptLimiter()

    @Bean
    fun signInService(
        repository: SignInRepository,
        passwordEncoder: PasswordEncoder,
        tokenIssuer: AuthTokenIssuer,
        attempts: LoginAttemptLimiter,
        clock: Clock,
    ) = SignInService(repository, passwordEncoder, tokenIssuer, attempts, clock)
}
