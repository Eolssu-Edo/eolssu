package com.ssafy.eolssu.backend.feature.auth.infrastructure.mail

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl

class SmtpVerificationMailSenderTests {
    private val sent = mutableListOf<MimeMessage>()
    private var failure: RuntimeException? = null
    private val transport = object : JavaMailSenderImpl() {
        override fun doSend(mimeMessages: Array<out MimeMessage>, originalMessages: Array<out Any>?) {
            failure?.let { throw it }
            sent.addAll(mimeMessages)
        }
    }

    @Test
    fun `sends a UTF-8 Korean code mail from the configured name and address`() {
        SmtpVerificationMailSender(transport, InternetAddress("eolssu.mailservice@gmail.com", "얼쑤", "UTF-8"))
            .send("learner@example.com", VerificationPurpose.PASSWORD_RESET, "012345")
        val message = sent.single()
        val from = message.from.single() as InternetAddress
        assertEquals("eolssu.mailservice@gmail.com", from.address)
        assertEquals("얼쑤", from.personal)
        assertEquals("learner@example.com", (message.allRecipients.single() as InternetAddress).address)
        assertEquals("[얼쑤] 비밀번호 재설정 인증번호", message.subject)
        val body = message.content as String
        assertTrue(body.contains("012345") && body.contains("3분"), body)
    }

    @Test
    fun `smtp failures become mail unavailable`() {
        failure = MailSendException("smtp down")
        assertFailsWith<VerificationMailUnavailable> {
            SmtpVerificationMailSender(transport, InternetAddress("a@example.com")).send("b@example.com", VerificationPurpose.SIGN_UP, "123456")
        }
    }

    @Test
    fun `builds a STARTTLS sender only when host username and password are all set`() {
        assertNull(SmtpMailTransport.create(VerificationMailProperties(host = "smtp.gmail.com", username = "eolssu@example.com")))
        val transport = SmtpMailTransport.create(VerificationMailProperties(host = "smtp.gmail.com", username = "eolssu@example.com", password = "app-password"))!!
        assertEquals(587, transport.port)
        assertEquals("true", transport.javaMailProperties["mail.smtp.starttls.required"])
        assertEquals("true", transport.javaMailProperties["mail.smtp.auth"])
        assertEquals("3000", transport.javaMailProperties["mail.smtp.connectiontimeout"])
        val from = SmtpMailTransport.sender(VerificationMailProperties(username = "eolssu@example.com"))
        assertEquals("eolssu@example.com", from.address)
        assertNull(from.personal)
    }
}
