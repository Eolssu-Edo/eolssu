package com.ssafy.eolssu.backend.feature.auth.infrastructure.mail

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import jakarta.mail.MessagingException
import jakarta.mail.internet.InternetAddress
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper

/** 인증번호 메일. 실패는 모두 VerificationMailUnavailable로 바꾸고, 로그에 이메일·번호를 남기지 않는다. */
class SmtpVerificationMailSender(private val transport: JavaMailSender, private val from: InternetAddress) : VerificationMailSender {
    override fun send(email: String, purpose: VerificationPurpose, code: String) {
        try {
            val message = transport.createMimeMessage()
            MimeMessageHelper(message, false, "UTF-8").apply {
                setFrom(from)
                setTo(email)
                setSubject("[얼쑤] ${purpose.label} 인증번호")
                setText("얼쑤 ${purpose.label} 인증번호는 $code 입니다.\n3분 안에 입력해 주세요.\n\n직접 요청하지 않았다면 이 메일을 무시해 주세요.")
            }
            transport.send(message)
        } catch (failure: MailException) {
            unavailable(failure)
        } catch (failure: MessagingException) {
            unavailable(failure)
        }
    }

    private fun unavailable(failure: Exception): Nothing {
        LoggerFactory.getLogger(javaClass).warn("Verification mail delivery failed: {}", failure.javaClass.simpleName)
        throw VerificationMailUnavailable()
    }

    private val VerificationPurpose.label
        get() = when (this) {
            VerificationPurpose.SIGN_UP -> "회원가입"
            VerificationPurpose.PASSWORD_RESET -> "비밀번호 재설정"
            VerificationPurpose.ACCOUNT_MERGE -> "계정 연결"
        }
}

/** AUTH_MAIL_*. password가 toString에 나오지 않게 data class로 두지 않는다. */
@ConfigurationProperties("eolssu.auth.mail")
class VerificationMailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "",
    val fromName: String = "",
)

object SmtpMailTransport {
    /** 호스트·계정·비밀번호가 모두 있을 때만 만든다. STARTTLS는 설정값 없이 고정하고, 잠금 안에서 오래 기다리지 않게 시간 제한을 짧게 둔다. */
    fun create(properties: VerificationMailProperties): JavaMailSenderImpl? {
        if (listOf(properties.host, properties.username, properties.password).any { it.isBlank() }) return null
        return JavaMailSenderImpl().apply {
            host = properties.host
            port = properties.port
            username = properties.username
            password = properties.password
            defaultEncoding = "UTF-8"
            javaMailProperties.putAll(
                mapOf(
                    "mail.transport.protocol" to "smtp",
                    "mail.smtp.auth" to "true",
                    "mail.smtp.starttls.enable" to "true",
                    "mail.smtp.starttls.required" to "true",
                    "mail.smtp.connectiontimeout" to "3000",
                    "mail.smtp.timeout" to "5000",
                    "mail.smtp.writetimeout" to "5000",
                ),
            )
        }
    }

    fun sender(properties: VerificationMailProperties) =
        InternetAddress(properties.from.ifBlank { properties.username }, properties.fromName.ifBlank { null }, "UTF-8")
}
