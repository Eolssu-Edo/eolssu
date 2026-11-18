package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AuthTokenIssuerTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val secrets = EmailVerificationSecrets(ByteArray(32) { it.toByte() })
    private val stored = mutableListOf<Triple<Long, String, Instant?>>()
    private val repository = object : RefreshTokenRepository {
        override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long {
            stored.add(Triple(userId, tokenHash, expiresAt)); return stored.size.toLong()
        }
    }
    private val signer = object : AccessTokenSigner {
        override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = "access-$userId-${Duration.between(issuedAt, expiresAt).toMinutes()}"
    }

    @Test
    fun `issues a 30 minute access token and a 14 day refresh token stored only as a hash`() {
        val tokens = AuthTokenIssuer(signer, repository, secrets, clock).issue(42)
        assertEquals("access-42-30", tokens.accessToken)
        assertEquals(now.plus(AuthTokenPolicy.ACCESS_TOKEN_TTL), tokens.accessTokenExpiresAt)
        assertTrue(tokens.refreshToken.matches(Regex("[A-Za-z0-9_-]{43}")))
        val (userId, hash, expiresAt) = stored.single()
        assertEquals(42, userId)
        assertEquals(secrets.hashToken(tokens.refreshToken), hash)
        assertNotEquals(tokens.refreshToken, hash)
        assertEquals(now.plus(Duration.ofDays(14)), expiresAt)
    }

    @Test
    fun `only explicitly selected users receive tokens without expiration`() {
        val issuer = AuthTokenIssuer(
            com.ssafy.eolssu.backend.feature.auth.infrastructure.security.JwtAccessTokenSigner("test-signing-key-at-least-32-bytes-long!!"),
            repository, secrets, clock, setOf(42),
        )
        val permanent = issuer.issue(42)
        kotlin.test.assertNull(permanent.accessTokenExpiresAt)
        kotlin.test.assertNull(permanent.refreshTokenExpiresAt)
        kotlin.test.assertNull(stored.single().third)
        val claims = com.nimbusds.jwt.SignedJWT.parse(permanent.accessToken).jwtClaimsSet
        kotlin.test.assertNull(claims.expirationTime)
        assertEquals(secrets.hashToken(permanent.refreshToken), claims.getStringClaim("session_hash"))
        val ordinary = issuer.issue(43)
        assertEquals(now.plusSeconds(1800), ordinary.accessTokenExpiresAt)
        assertEquals(now.plusSeconds(1209600), ordinary.refreshTokenExpiresAt)
    }

    @Test
    fun `unavailable signing stores no refresh token`() {
        val unavailable = object : AccessTokenSigner {
            override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant): String = throw TokenIssuingUnavailable()
        }
        assertFailsWith<TokenIssuingUnavailable> { AuthTokenIssuer(unavailable, repository, secrets, clock).issue(42) }
        assertTrue(stored.isEmpty())
    }
}
