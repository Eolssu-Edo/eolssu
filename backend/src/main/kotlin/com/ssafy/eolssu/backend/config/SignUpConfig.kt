package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.AuthTokenIssuer
import com.ssafy.eolssu.backend.feature.auth.application.SignUpService
import com.ssafy.eolssu.backend.feature.auth.domain.SignUpRepository
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.EmailVerificationMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisSignUpRepository
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.UserTermAgreementMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db")
class SignUpConfig {
    @Bean
    fun signUpRepository(
        verifications: EmailVerificationMapper,
        users: UserMapper,
        agreements: UserTermAgreementMapper,
        instruments: InstrumentMapper,
        manager: PlatformTransactionManager,
    ): SignUpRepository = MyBatisSignUpRepository(verifications, users, agreements, instruments, manager)

    @Bean
    fun signUpService(
        repository: SignUpRepository,
        terms: TermRepository,
        passwordEncoder: PasswordEncoder,
        secrets: VerificationSecrets,
        tokenIssuer: AuthTokenIssuer,
        clock: Clock,
    ) = SignUpService(repository, terms, passwordEncoder, secrets, tokenIssuer, clock)
}
