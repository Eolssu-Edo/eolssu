package com.ssafy.eolssu.backend.feature.inquiry.infrastructure.mail

import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMail
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailSender
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailUnavailable
import jakarta.mail.MessagingException
import jakarta.mail.internet.InternetAddress
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/** 문의 메일. 인증 메일과 같은 SMTP 계정으로 보낸다. 실패는 모두 InquiryMailUnavailable로 바꾸고, 로그에 문의 내용·이메일을 남기지 않는다. */
class SmtpInquiryMailSender(
    private val transport: JavaMailSender,
    private val from: InternetAddress,
    private val recipients: List<InternetAddress>,
) : InquiryMailSender {
    override fun send(mail: InquiryMail) {
        try {
            val message = transport.createMimeMessage()
            MimeMessageHelper(message, false, "UTF-8").apply {
                setFrom(from)
                setTo(recipients.toTypedArray())
                mail.email?.let { setReplyTo(it) }
                setSubject(subject(mail))
                setText(body(mail))
            }
            transport.send(message)
        } catch (failure: MailException) {
            unavailable(failure)
        } catch (failure: MessagingException) {
            unavailable(failure)
        }
    }

    private fun unavailable(failure: Exception): Nothing {
        LoggerFactory.getLogger(javaClass).warn("Inquiry mail delivery failed: {}", failure.javaClass.simpleName)
        throw InquiryMailUnavailable()
    }

    companion object {
        private val KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))

        internal fun subject(mail: InquiryMail) = "[얼쑤 문의] ${mail.title}"

        internal fun body(mail: InquiryMail) = buildString {
            appendLine("얼쑤 앱 마이페이지에서 문의가 왔어요.")
            appendLine()
            appendLine("보낸 회원: ${mail.nickname} (회원 ID ${mail.userId})")
            appendLine("회원 이메일: ${mail.email ?: "없음(소셜 가입) - 답장할 주소가 없어요"}")
            appendLine("보낸 시각: ${KST.format(mail.sentAt)} (KST)")
            appendLine()
            appendLine("제목: ${mail.title}")
            appendLine()
            append(mail.content)
        }
    }
}

/** INQUIRY_MAIL_RECIPIENTS. 쉼표로 구분한 개발자 메일 주소 목록이다. */
@ConfigurationProperties("eolssu.inquiry")
class InquiryMailProperties(val recipients: List<String> = emptyList())
