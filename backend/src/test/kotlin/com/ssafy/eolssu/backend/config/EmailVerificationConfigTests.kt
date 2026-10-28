package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.InMemoryVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.SmtpVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.VerificationMailProperties
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class EmailVerificationConfigTests {
    private val config = EmailVerificationConfig()
    private val gmail = VerificationMailProperties(host = "smtp.gmail.com", username = "eolssu@example.com", password = "app-password")

    @Test
    fun `default smtp mode sends only when the SMTP values are complete`() {
        assertIs<SmtpVerificationMailSender>(config.verificationMailSender("smtp", gmail))
        val incomplete = config.verificationMailSender("smtp", VerificationMailProperties(host = "smtp.gmail.com"))
        assertFailsWith<VerificationMailUnavailable> { incomplete.send("a@example.com", VerificationPurpose.SIGN_UP, "123456") }
    }

    @Test
    fun `memory and unavailable modes ignore SMTP values and unknown modes fail at startup`() {
        assertIs<InMemoryVerificationMailSender>(config.verificationMailSender("memory", gmail))
        assertFailsWith<VerificationMailUnavailable> { config.verificationMailSender("unavailable", gmail).send("a@example.com", VerificationPurpose.SIGN_UP, "123456") }
        assertFailsWith<IllegalArgumentException> { config.verificationMailSender("gmail", gmail) }
    }
}
