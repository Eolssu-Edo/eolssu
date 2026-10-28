package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class WithdrawalServiceTests {
    private var now = Instant.parse("2026-10-22T03:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val repository = MemoryWithdrawalRepository()
    private var matchedInsideTransaction = false
    private val encoder = object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?) = "hashed:$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            if (repository.inTransaction) matchedInsideTransaction = true
            return encodedPassword == "hashed:$rawPassword"
        }
    }
    private val attempts = LoginAttemptLimiter()
    private val service = WithdrawalService(repository, encoder, attempts, clock)

    init {
        repository.hashes[7] = "hashed:Current!234"
    }

    @Test
    fun `the right password makes the member withdrawal pending until the first 04_00 KST 30 days later and revokes every token`() {
        val withdrawn = assertIs<WithdrawalResult.Withdrawn>(service.withdraw(7, "Current!234"))
        assertEquals(Instant.parse("2026-10-22T03:00:00Z"), withdrawn.requestedAt)
        assertEquals(Instant.parse("2026-11-21T19:00:00Z"), withdrawn.purgeAt)
        assertEquals(Triple(7L, withdrawn.requestedAt, withdrawn.purgeAt), repository.pending.single())
        assertEquals(listOf(7L), repository.revoked)
        assertFalse(matchedInsideTransaction)
    }

    @Test
    fun `purge time is the first 04_00 KST at or after 30 days`() {
        assertEquals(Instant.parse("2026-11-27T19:00:00Z"), WithdrawalPolicy.purgeAt(Instant.parse("2026-10-28T18:59:59Z")))
        assertEquals(Instant.parse("2026-11-27T19:00:00Z"), WithdrawalPolicy.purgeAt(Instant.parse("2026-10-28T19:00:00Z")))
        assertEquals(Instant.parse("2026-11-28T19:00:00Z"), WithdrawalPolicy.purgeAt(Instant.parse("2026-10-28T19:00:01Z")))
    }

    @Test
    fun `a wrong password is a mismatch and the fifth failure locks for 15 minutes`() {
        repeat(4) { assertEquals(WithdrawalResult.CurrentPasswordMismatch, service.withdraw(7, "Wrong!234")) }
        assertEquals(WithdrawalResult.TooManyAttempts(now.plusSeconds(900)), service.withdraw(7, "Wrong!234"))
        assertIs<WithdrawalResult.TooManyAttempts>(service.withdraw(7, "Current!234"))
        assertTrue(repository.pending.isEmpty())
    }

    @Test
    fun `password change and withdrawal share the current password lock`() {
        val passwordChange = PasswordChangeService(MemoryPasswordChangeRepository().apply { hashes[7] = "hashed:Current!234" }, encoder,
            AuthTokenIssuer(
                object : AccessTokenSigner {
                    override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = "access"
                },
                MemoryPasswordChangeRepository(), EmailVerificationSecrets(ByteArray(32)), clock,
            ),
            attempts, clock,
        )
        repeat(3) { assertEquals(PasswordChangeResult.CurrentPasswordMismatch, passwordChange.change(7, "Wrong!234", "NewPassword!567")) }
        assertEquals(WithdrawalResult.CurrentPasswordMismatch, service.withdraw(7, "Wrong!234"))
        assertIs<WithdrawalResult.TooManyAttempts>(service.withdraw(7, "Wrong!234"))
        assertIs<PasswordChangeResult.TooManyAttempts>(passwordChange.change(7, "Current!234", "NewPassword!567"))
    }

    @Test
    fun `a member that is not active is not found`() {
        assertEquals(WithdrawalResult.NotFound, service.withdraw(8, "Current!234"))
    }

    @Test
    fun `a password changed after verification is a mismatch and nothing is saved`() {
        repository.beforeLockedRead = { repository.hashes[7] = "hashed:Other!234" }
        assertEquals(WithdrawalResult.CurrentPasswordMismatch, service.withdraw(7, "Current!234"))
        assertTrue(repository.pending.isEmpty())
        assertTrue(repository.revoked.isEmpty())
        assertNull(attempts.lockedUntil("member:7", now))
    }
}

internal class MemoryWithdrawalRepository : WithdrawalRepository {
    val hashes = mutableMapOf<Long, String>()
    val pending = mutableListOf<Triple<Long, Instant, Instant>>()
    val revoked = mutableListOf<Long>()
    var inTransaction = false
    var beforeLockedRead: () -> Unit = {}

    override fun <T : Any> inTransaction(action: () -> T): T {
        inTransaction = true
        try { return action() } finally { inTransaction = false }
    }
    override fun findActivePasswordHash(userId: Long) = hashes[userId]
    override fun findActivePasswordHashForUpdate(userId: Long): String? {
        beforeLockedRead()
        return hashes[userId]
    }
    override fun markWithdrawalPending(userId: Long, requestedAt: Instant, purgeAt: Instant) {
        hashes.remove(userId)
        pending += Triple(userId, requestedAt, purgeAt)
    }
    override fun revokeAllRefreshTokens(userId: Long, now: Instant) { revoked += userId }
}
