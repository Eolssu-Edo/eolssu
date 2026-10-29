package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.application.EmailVerificationService
import com.ssafy.eolssu.backend.feature.auth.application.MemoryVerificationRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.mail.InMemoryVerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
}
