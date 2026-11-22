package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.VerificationMailProperties
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMail
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailUnavailable
import com.ssafy.eolssu.backend.feature.inquiry.infrastructure.mail.InquiryMailProperties
import com.ssafy.eolssu.backend.feature.inquiry.infrastructure.mail.SmtpInquiryMailSender
import jakarta.mail.internet.AddressException
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class InquiryConfigTests {
    private val config = InquiryConfig()
    private val gmail = VerificationMailProperties(host = "smtp.gmail.com", username = "eolssu@example.com", password = "app-password")
    private val developers = InquiryMailProperties(listOf("dev1@example.com", " dev2@example.com ", ""))
    private val mail = InquiryMail(7, "연습자", null, "제목", "내용", Instant.parse("2026-11-22T03:00:00Z"))

    @Test
    fun `smtp mode sends only when the SMTP account and recipients are both set`() {
        assertIs<SmtpInquiryMailSender>(config.inquiryMailSender("smtp", gmail, developers))
        assertFailsWith<InquiryMailUnavailable> { config.inquiryMailSender("smtp", VerificationMailProperties(host = "smtp.gmail.com"), developers).send(mail) }
        assertFailsWith<InquiryMailUnavailable> { config.inquiryMailSender("smtp", gmail, InquiryMailProperties(listOf(" "))).send(mail) }
    }

    @Test
    fun `memory accepts, unavailable refuses, and wrong values fail at startup`() {
        config.inquiryMailSender("memory", gmail, developers).send(mail)
        assertFailsWith<InquiryMailUnavailable> { config.inquiryMailSender("unavailable", gmail, developers).send(mail) }
        assertFailsWith<IllegalArgumentException> { config.inquiryMailSender("gmail", gmail, developers) }
        assertFailsWith<AddressException> { config.inquiryMailSender("smtp", gmail, InquiryMailProperties(listOf("not an address"))) }
    }

    @Test
    fun `mail shows who sent it in KST and the title in the subject`() {
        assertEquals("[얼쑤 문의] 제목", SmtpInquiryMailSender.subject(mail))
        val body = SmtpInquiryMailSender.body(mail)
        assertContains(body, "보낸 회원: 연습자 (회원 ID 7)")
        assertContains(body, "회원 이메일: 없음(소셜 가입)")
        assertContains(body, "보낸 시각: 2026-11-22 12:00:00 (KST)")
        assertContains(body, "제목: 제목\n\n내용")
    }
}
