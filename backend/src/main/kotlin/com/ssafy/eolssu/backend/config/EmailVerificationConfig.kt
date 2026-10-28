package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.EmailVerificationService
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.InMemoryVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.EmailVerificationMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisEmailVerificationRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.util.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db")
class EmailVerificationConfig {
    @Bean
    fun verificationSecrets(@Value("\${eolssu.auth.code-hmac-secret:}") encoded: String): VerificationSecrets {
        val key = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("AUTH_CODE_HMAC_SECRET must be Base64 encoded")
        }
        return EmailVerificationSecrets(key)
    }

    @Bean
    fun verificationMailSender(@Value("\${eolssu.auth.mail-mode:unavailable}") mode: String): VerificationMailSender = when (mode) {
        "memory" -> InMemoryVerificationMailSender()
        "unavailable" -> object : VerificationMailSender {
            override fun send(email: String, purpose: VerificationPurpose, code: String): Unit = throw VerificationMailUnavailable()
        }
        else -> throw IllegalArgumentException("AUTH_MAIL_MODE must be unavailable or memory")
    }

    @Bean
    fun emailVerificationRepository(mapper: EmailVerificationMapper, jdbc: JdbcTemplate, manager: PlatformTransactionManager): EmailVerificationRepository =
        MyBatisEmailVerificationRepository(mapper, jdbc, manager)

    @Bean
    fun emailVerificationService(repository: EmailVerificationRepository, mail: VerificationMailSender, secrets: VerificationSecrets, clock: Clock) =
        EmailVerificationService(repository, mail, secrets, clock)
}
