package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class TokenRefreshServiceTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val secrets = EmailVerificationSecrets(ByteArray(32))
    private val repository = MemoryTokenRefreshRepository()
    private var signingAvailable = true
    private val issuer = AuthTokenIssuer(
        object : AccessTokenSigner {
            override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = if (signingAvailable) "access-$userId" else throw TokenIssuingUnavailable()
        },
        repository, secrets, clock,
    )
    private val service = TokenRefreshService(repository, issuer, secrets, clock)

    private fun stored(token: String, userId: Long = 7, expiresAt: Instant = now.plusSeconds(60), revoked: String? = null): Long {
        val id = repository.insert(userId, secrets.hashToken(token), expiresAt, now.minusSeconds(60))
        if (revoked != null) repository.tokens.getValue(id).revokeReason = revoked
        return id
    }

    @Test
    fun `rotates a valid refresh token for an active member`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        val old = stored("old-token")
        val refreshed = assertIs<TokenRefreshResult.Refreshed>(service.refresh("old-token"))
        assertEquals("access-7", refreshed.tokens.accessToken)
        assertEquals("ROTATED", repository.tokens.getValue(old).revokeReason)
        val new = repository.tokens.values.single { it.revokeReason == null }
        assertEquals(secrets.hashToken(refreshed.tokens.refreshToken), new.hash)
    }

    @Test
    fun `missing unknown expired or inactive member tokens are unauthenticated without rotation`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        repository.statuses[8] = MemberStatus.WITHDRAWAL_PENDING
        val expired = stored("expired", expiresAt = now)
        val pending = stored("pending", userId = 8)
        for (token in listOf(null, "", "unknown", "expired", "pending")) {
            assertEquals(TokenRefreshResult.Unauthenticated, service.refresh(token))
        }
        assertNull(repository.tokens.getValue(expired).revokeReason)
        assertNull(repository.tokens.getValue(pending).revokeReason)
        assertEquals(2, repository.tokens.size)
    }

    @Test
    fun `reusing a revoked token revokes every active token of the member`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        stored("rotated", revoked = "ROTATED")
        val active = stored("other-device")
        val otherMember = stored("someone-else", userId = 9)
        assertEquals(TokenRefreshResult.Unauthenticated, service.refresh("rotated"))
        assertEquals("REUSE_DETECTED", repository.tokens.getValue(active).revokeReason)
        assertNull(repository.tokens.getValue(otherMember).revokeReason)
    }

    @Test
    fun `a token revoked by password change sign out or withdrawal is only unauthenticated`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        val active = stored("this-device")
        for (reason in listOf("PASSWORD_CHANGED", "SIGN_OUT", "WITHDRAWN", "REUSE_DETECTED")) {
            stored("old-$reason", revoked = reason)
            assertEquals(TokenRefreshResult.Unauthenticated, service.refresh("old-$reason"))
        }
        assertNull(repository.tokens.getValue(active).revokeReason)
    }

    @Test
    fun `null expiration is rejected when user is no longer selected`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        repository.insert(7, secrets.hashToken("permanent"), null, now)
        assertEquals(TokenRefreshResult.Unauthenticated, service.refresh("permanent"))
    }

    @Test
    fun `selected non expiring refresh rotates and old session is revoked`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        val permanentIssuer = AuthTokenIssuer(
            com.ssafy.eolssu.backend.feature.auth.infrastructure.security.JwtAccessTokenSigner("test-signing-key-at-least-32-bytes-long!!"),
            repository, secrets, clock, setOf(7),
        )
        val tokens = permanentIssuer.issue(7)
        val permanentService = TokenRefreshService(repository, permanentIssuer, secrets, clock)
        val result = assertIs<TokenRefreshResult.Refreshed>(permanentService.refresh(tokens.refreshToken))
        assertNull(result.tokens.refreshTokenExpiresAt)
        assertEquals("ROTATED", repository.tokens.values.first().revokeReason)
    }

    @Test
    fun `missing signing key is unavailable`() {
        repository.statuses[7] = MemberStatus.ACTIVE
        stored("old-token")
        signingAvailable = false
        assertEquals(TokenRefreshResult.Unavailable, service.refresh("old-token"))
    }
}

internal class MemoryTokenRefreshRepository : TokenRefreshRepository, RefreshTokenRepository {
    class Stored(val id: Long, val userId: Long, val hash: String, val expiresAt: Instant?, var revokeReason: String? = null)

    val tokens = linkedMapOf<Long, Stored>()
    val statuses = mutableMapOf<Long, MemberStatus>()

    override fun <T : Any> inTransaction(action: () -> T): T = action()
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long {
        val id = tokens.size + 1L
        tokens[id] = Stored(id, userId, tokenHash, expiresAt)
        return id
    }
    override fun find(tokenHash: String) = findForUpdate(tokenHash)
    override fun findForUpdate(tokenHash: String) = tokens.values.firstOrNull { it.hash == tokenHash }?.let {
        StoredRefreshToken(it.id, it.userId, it.expiresAt, it.revokeReason?.let(RevokeReason::valueOf))
    }
    override fun revoke(id: Long, reason: RevokeReason, now: Instant) { tokens.getValue(id).revokeReason = reason.name }
    override fun revokeAll(userId: Long, reason: RevokeReason, now: Instant) {
        tokens.values.filter { it.userId == userId && it.revokeReason == null }.forEach { it.revokeReason = reason.name }
    }
    override fun memberStatusForUpdate(userId: Long) = statuses[userId]
}
