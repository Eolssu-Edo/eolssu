package com.ssafy.eolssu.backend.feature.auth.infrastructure.mail

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class InMemoryVerificationMailSenderTests {
    @Test
    fun `local mailbox exposes delivered mail only through the in process port and redacts toString`() {
        val sender = InMemoryVerificationMailSender()
        sender.send("local@example.test", VerificationPurpose.SIGN_UP, "012345")
        val delivered = sender.messages().single()
        assertEquals("local@example.test", delivered.email)
        assertEquals(VerificationPurpose.SIGN_UP, delivered.purpose)
        assertEquals("012345", delivered.code)
        assertFalse(delivered.toString().contains("012345"))
    }
}
