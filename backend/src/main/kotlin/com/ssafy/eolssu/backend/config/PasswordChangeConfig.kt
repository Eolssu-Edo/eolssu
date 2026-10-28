package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.application.LoginAttemptLimiter
import com.ssafy.eolssu.backend.feature.auth.application.PasswordChangeService
import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalService
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisPasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisWithdrawalRepository
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
    // 현재 비밀번호를 다시 확인하는 비밀번호 변경(USR-04)·탈퇴(USR-05)가 함께 쓰는 잠금. 로그인 잠금(LoginAttemptLimiter Bean)과 따로 센다.
    // 같은 타입 Bean이 둘이면 로그인 주입이 모호해지므로 Bean으로 두지 않는다.
    private val currentPasswordAttempts = LoginAttemptLimiter()

    @Bean
    fun passwordChangeRepository(users: UserMapper, refreshTokens: RefreshTokenMapper, manager: PlatformTransactionManager): PasswordChangeRepository =
        MyBatisPasswordChangeRepository(users, refreshTokens, manager)

    @Bean
    fun passwordChangeService(repository: PasswordChangeRepository, passwordEncoder: PasswordEncoder, tokenIssuer: AuthTokenIssuer, clock: Clock) =
        PasswordChangeService(repository, passwordEncoder, tokenIssuer, currentPasswordAttempts, clock)

    @Bean
    fun withdrawalRepository(users: UserMapper, refreshTokens: RefreshTokenMapper, manager: PlatformTransactionManager): WithdrawalRepository =
        MyBatisWithdrawalRepository(users, refreshTokens, manager)

    @Bean
    fun withdrawalService(repository: WithdrawalRepository, passwordEncoder: PasswordEncoder, clock: Clock) =
        WithdrawalService(repository, passwordEncoder, currentPasswordAttempts, clock)
}
