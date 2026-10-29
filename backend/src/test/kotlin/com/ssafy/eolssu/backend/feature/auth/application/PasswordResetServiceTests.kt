package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordResetServiceTests {
    private var now = Instant.parse("2026-10-29T00:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val repository = MemoryRepository()
    private var afterEncode: () -> Unit = {}
    private val encoder = object : PasswordEncoder {
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            assertFalse(repository.inside, "Argon2 must stay outside the transaction")
            return "hashed:$rawPassword" == encodedPassword
        }
        override fun encode(rawPassword: CharSequence?): String {
            assertFalse(repository.inside, "Argon2 must stay outside the transaction")
            afterEncode()
            return "hashed:$rawPassword"
        }
    }
    private val service = PasswordResetService(repository, encoder, EmailVerificationSecrets(null), clock)

    @Test fun `locks user before proof and refresh after hashing outside the transaction`() {
        assertEquals(PasswordResetResult.Reset,service.reset("proof","NewPassword!567"))
        assertEquals(listOf("user-lock","proof-lock","password","consume","refresh"),repository.events)
        assertEquals("hashed:NewPassword!567",repository.member.passwordHash)
    }
    @Test fun `proof expiring during hash computation is rejected before writes`() {
        afterEncode={ now=now.plusSeconds(1800) }
        assertEquals(PasswordResetResult.EmailNotVerified,service.reset("proof","NewPassword!567"))
        assertEquals(listOf("user-lock","proof-lock"),repository.events)
    }
    @Test fun `expiry is checked after waiting for the proof lock`() {
        repository.onProofLock={ now=now.plusSeconds(1800) }
        assertEquals(PasswordResetResult.EmailNotVerified,service.reset("proof","NewPassword!567"))
        assertEquals(listOf("user-lock","proof-lock"),repository.events)
    }
    @Test fun `another password change while hashing cannot be overwritten`() {
        afterEncode={ repository.member=ResetMember(1,"member@example.com","hashed:Changed!234") }
        assertEquals(PasswordResetResult.EmailNotVerified,service.reset("proof","NewPassword!567"))
        assertEquals(listOf("user-lock"),repository.events)
        assertEquals("hashed:Changed!234",repository.member.passwordHash)
    }
    @Test fun `proof consumed or invalidated while hashing is rechecked`() {
        for (invalidate in listOf(false,true)) {
            repository.events.clear()
            repository.consumed=null; repository.invalidated=null
            afterEncode={ if (invalidate) repository.invalidated=now else repository.consumed=now }
            assertEquals(PasswordResetResult.EmailNotVerified,service.reset("proof","NewPassword!567"))
            assertEquals(listOf("user-lock","proof-lock"),repository.events)
        }
    }
    private class MemoryRepository : PasswordResetRepository {
        var onProofLock: () -> Unit = {}
        var inside=false
        var member=ResetMember(1,"member@example.com","hashed:Current!234")
        var consumed: Instant?=null
        var invalidated: Instant?=null
        val events=mutableListOf<String>()
        override fun <T : Any> inTransaction(action: () -> T): T { inside=true; try { return action() } finally { inside=false } }
        override fun findVerification(tokenHash: String)=ConsumableVerification(7,"member@example.com",VerificationPurpose.PASSWORD_RESET,Instant.parse("2026-10-29T00:30:00Z"),consumed,invalidated)
        override fun findVerificationForUpdate(tokenHash: String): ConsumableVerification { events+="proof-lock"; onProofLock(); return findVerification(tokenHash) }
        override fun findMember(email: String)=member
        override fun findMemberForUpdate(userId: Long): ResetMember { events+="user-lock"; return member }
        override fun updatePasswordHash(userId: Long,passwordHash: String) { events+="password"; member=ResetMember(member.id,member.email,passwordHash) }
        override fun markConsumed(verificationId: Long,now: Instant) { events+="consume"; consumed=now }
        override fun revokeAllRefreshTokens(userId: Long,now: Instant) { events+="refresh" }
    }
}
