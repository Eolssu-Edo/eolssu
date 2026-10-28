package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class SignOutServiceTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val secrets = EmailVerificationSecrets(ByteArray(32))
    private val repository = MemoryTokenRefreshRepository()
    private val service = SignOutService(repository, secrets, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `revokes only the signed in member's own refresh token`() {
        val own = repository.insert(7, secrets.hashToken("own"), now.plusSeconds(60), now)
        val other = repository.insert(9, secrets.hashToken("other"), now.plusSeconds(60), now)
        service.signOut(7, "other")
        assertNull(repository.tokens.getValue(other).revokeReason)
        service.signOut(7, null)
        service.signOut(7, "own")
        assertEquals("SIGN_OUT", repository.tokens.getValue(own).revokeReason)
        service.signOut(7, "own")
        assertEquals("SIGN_OUT", repository.tokens.getValue(own).revokeReason)
    }
}
