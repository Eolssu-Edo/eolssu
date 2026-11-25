package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.SmtpMailTransport
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.VerificationMailProperties
import com.ssafy.eolssu.backend.feature.inquiry.application.InquiryService
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailSender
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailUnavailable
import com.ssafy.eolssu.backend.feature.inquiry.infrastructure.mail.InquiryMailProperties
import com.ssafy.eolssu.backend.feature.inquiry.infrastructure.mail.SmtpInquiryMailSender
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import jakarta.mail.internet.InternetAddress
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class InquiryConfig {
    // 인증 메일과 같은 AUTH_MAIL_MODE·AUTH_MAIL_* SMTP 계정을 쓴다. smtp인데 계정이 비었거나 받는 주소가 없으면 기동은 하고 발송만 503이다.
    // memory는 로컬 개발용으로 보내지 않고 성공 처리한다. 형식이 틀린 받는 주소는 기동을 막는다.
    @Bean
    fun inquiryMailSender(
        @Value("\${eolssu.auth.mail-mode:smtp}") mode: String,
        mail: VerificationMailProperties,
        inquiry: InquiryMailProperties,
    ): InquiryMailSender {
        val recipients = inquiry.recipients.map(String::trim).filter(String::isNotEmpty).map { address ->
            InternetAddress(address, true)
        }
        return when (mode) {
            "smtp" -> {
                val transport = SmtpMailTransport.create(mail)
                if (transport == null || recipients.isEmpty()) {
                    LoggerFactory.getLogger(javaClass).warn("Inquiry mail is unavailable: SMTP account or INQUIRY_MAIL_RECIPIENTS is not set")
                    refusingSender()
                } else {
                    SmtpInquiryMailSender(transport, SmtpMailTransport.sender(mail), recipients)
                }
            }
            "memory" -> InquiryMailSender { }
            "unavailable" -> refusingSender()
            else -> throw IllegalArgumentException("AUTH_MAIL_MODE must be smtp, memory or unavailable")
        }
    }

    private fun refusingSender() = InquiryMailSender { throw InquiryMailUnavailable() }

    @Bean
    fun inquiryService(members: MyInfoRepository, sender: InquiryMailSender, clock: Clock) = InquiryService(members, sender, clock)
}
