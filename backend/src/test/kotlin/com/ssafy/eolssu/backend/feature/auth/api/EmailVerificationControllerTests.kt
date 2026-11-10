package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.auth.application.EmailVerificationService
import com.ssafy.eolssu.backend.feature.auth.application.MemoryVerificationRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.InMemoryVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class EmailVerificationControllerTests {
    @Test
    fun `registered signup response explains that login cancels pending withdrawal`() {
        val repository = MemoryVerificationRepository().apply { members.add("pending@example.test") }
        val controller = EmailVerificationController(EmailVerificationService(
            repository, InMemoryVerificationMailSender(), EmailVerificationSecrets(ByteArray(32)),
            Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC),
        ))
        val response = MockMvcBuilders.standaloneSetup(controller).build().perform(
            post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"pending@example.test","purpose":"SIGN_UP"}"""),
        ).andExpect(status().isConflict).andReturn().response
        val message = JsonMapper.builder().build().readTree(response.contentAsByteArray).path("message").asString()
        assertTrue(message.contains("로그인") && message.contains("탈퇴") && message.contains("취소"), message)
    }

    @Test
    fun `malformed email is common 400 for sign up and password reset without storing or sending`() {
        val repository = MemoryVerificationRepository().apply { members.add("member@example.com") }
        val mail = InMemoryVerificationMailSender()
        val controller = EmailVerificationController(EmailVerificationService(
            repository, mail, EmailVerificationSecrets(ByteArray(32)),
            Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC),
        ))
        val mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler()).build()
        for (purpose in listOf("SIGN_UP", "PASSWORD_RESET")) {
            for (email in listOf("a@b", "a@b.", "@b.com", "a@.com", "a..b@c.com", "a@b..com", "a b@c.com", "a@-b.com")) {
                mvc.perform(
                    post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","purpose":"$purpose"}"""),
                ).andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
            }
        }
        assertTrue(repository.records.isEmpty())
        assertEquals(0, mail.messages().size)
    }

    @Test
    fun `password reset for an unknown well formed email stays 202 without delivery`() {
        val repository = MemoryVerificationRepository()
        val mail = InMemoryVerificationMailSender()
        val controller = EmailVerificationController(EmailVerificationService(
            repository, mail, EmailVerificationSecrets(ByteArray(32)),
            Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC),
        ))
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler()).build().perform(
            post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"absent@example.com","purpose":"PASSWORD_RESET"}"""),
        ).andExpect(status().isAccepted).andExpect(jsonPath("$.result.verificationId").value("1"))
        assertEquals(1, repository.records.size)
        assertEquals(0, mail.messages().size)
    }
}
