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
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordChangeServiceTests {
    private var now = Instant.parse("2026-10-28T03:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val repository = MemoryPasswordChangeRepository()
    private var matchedInsideTransaction = false
    private val encoder = object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?) = "hashed:$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            if (repository.inTransaction) matchedInsideTransaction = true
            return encodedPassword == "hashed:$rawPassword"
        }
    }
    private var signingAvailable = true
    private val issuer = AuthTokenIssuer(
        object : AccessTokenSigner {
            override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = if (signingAvailable) "access-$userId" else throw TokenIssuingUnavailable()
        },
        repository, EmailVerificationSecrets(ByteArray(32)), clock,
    )
    private val service = PasswordChangeService(repository, encoder, issuer, LoginAttemptLimiter(), clock)

    init {
        repository.hashes[7] = "hashed:Current!234"
    }

    @Test
    fun `the right current password saves the new hash revokes every refresh token and issues tokens for this device`() {
        val changed = assertIs<PasswordChangeResult.Changed>(service.change(7, "Current!234", "NewPassword!567"))
        assertEquals("access-7", changed.tokens.accessToken)
        assertEquals("hashed:NewPassword!567", repository.hashes[7])
        assertEquals(listOf(7L to "revoke-all", 7L to "insert"), repository.events)
        assertFalse(matchedInsideTransaction)
    }

    @Test
    fun `a wrong current password is a mismatch and the fifth failure locks for 15 minutes`() {
        repeat(4) { assertEquals(PasswordChangeResult.CurrentPasswordMismatch, service.change(7, "Wrong!234", "NewPassword!567")) }
        assertEquals(PasswordChangeResult.TooManyAttempts(now.plusSeconds(900)), service.change(7, "Wrong!234", "NewPassword!567"))
        now = now.plusSeconds(899)
        assertIs<PasswordChangeResult.TooManyAttempts>(service.change(7, "Current!234", "NewPassword!567"))
        now = now.plusSeconds(1)
        assertIs<PasswordChangeResult.Changed>(service.change(7, "Current!234", "NewPassword!567"))
    }

    @Test
    fun `a new password that breaks the rule or equals the current one is invalid and is not counted as a failure`() {
        repeat(5) {
            assertEquals(PasswordChangeResult.InvalidRequest, service.change(7, "Current!234", "Current!234"))
            assertEquals(PasswordChangeResult.InvalidRequest, service.change(7, "Current!234", "short1!"))
            assertEquals(PasswordChangeResult.InvalidRequest, service.change(7, "Wrong!234", "short1!"))
        }
        assertEquals("hashed:Current!234", repository.hashes[7])
        assertIs<PasswordChangeResult.Changed>(service.change(7, "Current!234", "NewPassword!567"))
    }

    @Test
    fun `a member that is not active is not found`() {
        assertEquals(PasswordChangeResult.NotFound, service.change(8, "Current!234", "NewPassword!567"))
    }

    @Test
    fun `a password changed after verification is a mismatch and nothing is saved`() {
        repository.beforeLockedRead = { repository.hashes[7] = "hashed:Other!234" }
        assertEquals(PasswordChangeResult.CurrentPasswordMismatch, service.change(7, "Current!234", "NewPassword!567"))
        assertEquals("hashed:Other!234", repository.hashes[7])
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `missing signing key is unavailable and rolls the change back`() {
        signingAvailable = false
        assertEquals(PasswordChangeResult.Unavailable, service.change(7, "Current!234", "NewPassword!567"))
        assertEquals("hashed:Current!234", repository.hashes[7])
        assertTrue(repository.events.isEmpty())
    }
}

internal class MemoryPasswordChangeRepository : PasswordChangeRepository, RefreshTokenRepository {
    val hashes = mutableMapOf<Long, String>()
    val events = mutableListOf<Pair<Long, String>>()
    var inTransaction = false
    var beforeLockedRead: () -> Unit = {}

    // 실제 저장소처럼 예외가 나면 트랜잭션 안의 변경을 되돌린다.
    override fun <T : Any> inTransaction(action: () -> T): T {
        val savedHashes = hashes.toMap()
        val savedEvents = events.toList()
        inTransaction = true
        try {
            return action()
        } catch (e: RuntimeException) {
            hashes.clear(); hashes.putAll(savedHashes)
            events.clear(); events.addAll(savedEvents)
            throw e
        } finally {
            inTransaction = false
        }
    }
    override fun findActivePasswordHash(userId: Long) = hashes[userId]
    override fun findActivePasswordHashForUpdate(userId: Long): String? {
        beforeLockedRead()
        return hashes[userId]
    }
    override fun updatePasswordHash(userId: Long, passwordHash: String) { hashes[userId] = passwordHash }
    override fun revokeAllRefreshTokens(userId: Long, now: Instant) { events += userId to "revoke-all" }
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant, now: Instant): Long {
        events += userId to "insert"
        return 1L
    }
}
