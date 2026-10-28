package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.EmailVerificationService
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.InMemoryVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.SmtpMailTransport
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.SmtpVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.VerificationMailProperties
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.EmailVerificationMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisEmailVerificationRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class EmailVerificationConfig {
    @Bean
    fun verificationSecrets(@Value("\${eolssu.auth.code-hmac-secret:}") encoded: String): VerificationSecrets {
        // 비어 있으면 앱은 기동하고 인증번호 발송·확인만 503이다(운영 .env에 아직 없을 수 있다). 형식이 틀린 값은 기동을 막는다.
        if (encoded.isBlank()) {
            LoggerFactory.getLogger(javaClass).warn("AUTH_CODE_HMAC_SECRET is not set; email verification is unavailable")
            return EmailVerificationSecrets(null)
        }
        val key = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("AUTH_CODE_HMAC_SECRET must be Base64 encoded")
        }
        return EmailVerificationSecrets(key)
    }

    // smtp(기본): AUTH_MAIL_HOST·USERNAME·PASSWORD가 모두 있으면 실제 발송, 하나라도 없으면 발송만 거절한다.
    // memory: 로컬 개발용 메모리 대역. unavailable: 항상 발송 거절.
    @Bean
    fun verificationMailSender(
        @Value("\${eolssu.auth.mail-mode:smtp}") mode: String,
        mail: VerificationMailProperties,
    ): VerificationMailSender = when (mode) {
        "smtp" -> SmtpMailTransport.create(mail)?.let { SmtpVerificationMailSender(it, SmtpMailTransport.sender(mail)) } ?: refusingMailSender()
        "memory" -> InMemoryVerificationMailSender()
        "unavailable" -> refusingMailSender()
        else -> throw IllegalArgumentException("AUTH_MAIL_MODE must be smtp, memory or unavailable")
    }

    private fun refusingMailSender() = object : VerificationMailSender {
        override fun send(email: String, purpose: VerificationPurpose, code: String): Unit = throw VerificationMailUnavailable()
    }

    @Bean
    fun emailVerificationRepository(mapper: EmailVerificationMapper, jdbc: JdbcTemplate, manager: PlatformTransactionManager): EmailVerificationRepository =
        MyBatisEmailVerificationRepository(mapper, jdbc, manager)

    @Bean
    fun emailVerificationService(repository: EmailVerificationRepository, mail: VerificationMailSender, secrets: VerificationSecrets, clock: Clock) =
        EmailVerificationService(repository, mail, secrets, clock)
}
