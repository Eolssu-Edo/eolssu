package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.AesProviderTokenCipher
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

/** Apple refresh token을 봉인해 연결에 두고, 연결 해제·탈퇴 신청 뒤 해지하는지 확인한다. */
class AppleTokenLifecycleTests {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val secrets = EmailVerificationSecrets(null)
    private val cipher = AesProviderTokenCipher("apple-key")
    private val redirect = "https://example.test/api/auth/ios/oauth/apple/callback"
    private val members = Members()
    private val proofs = Proofs()
    private val gateway = AppleFake()
    private val revocation = ProviderTokenRevocation(members, gateway, cipher)
    private val service = SocialAuthService(members, proofs, OAuthProofService(proofs, secrets, clock), gateway, secrets,
        AuthTokenIssuer(object : AccessTokenSigner { override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = "access" },
            object : RefreshTokenRepository { override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant) = 1L }, secrets, clock),
        org.mockito.Mockito.mock(PasswordResetRepository::class.java), NoPasswords, LoginAttemptLimiter(), clock,
        withdrawals = Withdrawals(), cipher = cipher, revocation = revocation)

    private fun linkApple(token: String = "refresh-1") {
        gateway.token = token
        val started = service.start(SocialProvider.APPLE, redirect, 7)
        service.link(7, SocialProvider.APPLE, "code", redirect, gateway.lastState, started.binding)
    }

    @Test fun `linking apple keeps only a sealed refresh token`() {
        linkApple()
        val stored = members.links.single()
        assertEquals("APPLE", stored.provider)
        val sealed = checkNotNull(members.tokens[stored.id])
        assertFalse(sealed.contains("refresh-1"))
        assertEquals("refresh-1", cipher.open(sealed))
    }

    @Test fun `signing in again replaces the stored token`() {
        linkApple("refresh-1")
        gateway.token = "refresh-2"
        val started = service.start(SocialProvider.APPLE, redirect, null)
        service.login(SocialProvider.APPLE, "code", redirect, gateway.lastState, started.binding)
        assertEquals("refresh-2", cipher.open(checkNotNull(members.tokens[members.links.single().id])))
    }

    @Test fun `unlinking revokes the token after the link is removed even when apple fails`() {
        linkApple()
        members.passwordHash = "hash"
        service.unlink(7, SocialProvider.APPLE)
        assertTrue(members.links.isEmpty())
        assertEquals(listOf("refresh-1"), gateway.revoked)
        gateway.failRevoke = true
        linkApple("refresh-2")
        service.unlink(7, SocialProvider.APPLE)
        assertTrue(members.links.isEmpty())
    }

    @Test fun `withdrawal request revokes and clears stored tokens`() {
        linkApple()
        revocation.revokeAll(7)
        assertEquals(listOf("refresh-1"), gateway.revoked)
        assertNull(members.tokens[members.links.single().id])
        revocation.revokeAll(7)
        assertEquals(listOf("refresh-1"), gateway.revoked)
    }

    @Test fun `password withdrawal revokes apple tokens after it is committed`() {
        linkApple()
        val repository = object : WithdrawalRepository by Withdrawals() {
            override fun findActivePasswordHash(userId: Long) = "hash"
            override fun findActivePasswordHashForUpdate(userId: Long) = "hash"
        }
        val accepting = object : PasswordEncoder {
            override fun encode(rawPassword: CharSequence?) = "unused"
            override fun matches(rawPassword: CharSequence?, encodedPassword: String?) = true
        }
        assertIs<WithdrawalResult.Withdrawn>(WithdrawalService(repository, accepting, LoginAttemptLimiter(), clock, revocation).withdraw(7, "password"))
        assertEquals(listOf("refresh-1"), gateway.revoked)
    }

    @Test fun `failed revocation keeps the token for purge and does not throw`() {
        linkApple()
        gateway.failRevoke = true
        revocation.revokeAll(7)
        assertNotNull(members.tokens[members.links.single().id])
    }

    private object NoPasswords : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?) = "unused"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?) = false
    }

    private inner class AppleFake : OAuthGateway {
        var token: String? = null
        var lastState = ""
        var failRevoke = false
        val revoked = mutableListOf<String>()
        override fun ready(provider: SocialProvider) = true
        override fun allows(provider: SocialProvider, redirectUri: String) = redirectUri == redirect
        override fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String): String { lastState = state; return "https://appleid.apple.com/auth/authorize?state=$state" }
        override fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String) =
            OAuthIdentity("apple-subject", "relay@privaterelay.appleid.com", false, providerToken = token)
        override fun revoke(provider: SocialProvider, token: String) { if (failRevoke) throw OAuthRemoteUnavailable(); revoked += token }
    }

    private class Proofs : OAuthProofRepository {
        val rows = mutableMapOf<String, OAuthProof>()
        override fun insert(proof: OAuthProof) { rows[proof.hash] = proof }
        override fun find(hash: String) = rows[hash]
        override fun consume(hash: String, now: Instant): Boolean {
            val proof = rows[hash] ?: return false
            if (proof.consumedAt != null) return false
            rows[hash] = OAuthProof(proof.hash, proof.kind, proof.provider, proof.purpose, proof.bindingHash, proof.userId, proof.redirectUri,
                proof.subject, proof.email, proof.emailVerified, proof.expiresAt, now, proof.flowStartedAt, proof.socialAccountId, proof.providerToken)
            return true
        }
        override fun deleteExpired(now: Instant) = 0
    }

    private class Withdrawals : WithdrawalRepository {
        override fun <T : Any> inTransaction(action: () -> T) = action()
        override fun findActivePasswordHash(userId: Long): String? = null
        override fun findActivePasswordHashForUpdate(userId: Long): String? = null
        override fun markWithdrawalPending(userId: Long, requestedAt: Instant, purgeAt: Instant) {}
        override fun revokeAllRefreshTokens(userId: Long, now: Instant) {}
    }

    private inner class Members : SocialAuthRepository {
        val links = mutableListOf<SocialLink>()
        val tokens = mutableMapOf<Long, String?>()
        var passwordHash: String? = null
        private var nextId = 1L
        private fun owner() = SocialMember("owner@example.test", SignInMember(7, passwordHash, "owner", 1, "HANJA", MemberStatus.ACTIVE))
        override fun <T : Any> inTransaction(action: () -> T) = action()
        override fun memberByEmail(email: String): SocialMember? = null
        override fun member(userId: Long, lock: Boolean) = owner()
        override fun linked(provider: String, subject: String) = links.firstOrNull { it.provider == provider && it.subject == subject }
        override fun links(userId: Long) = links.toList()
        override fun link(userId: Long, provider: String, subject: String, now: Instant, flowStartedAt: Instant?, providerToken: String?) {
            val id = nextId++
            links += SocialLink(userId, provider, subject, now, id)
            tokens[id] = providerToken
        }
        override fun saveProviderToken(linkId: Long, sealed: String) { tokens[linkId] = sealed }
        override fun providerTokens(userId: Long) = links.mapNotNull { link -> tokens[link.id]?.let { StoredProviderToken(link.id, link.provider, it) } }
        override fun clearProviderToken(linkId: Long, sealed: String): Boolean {
            if (tokens[linkId] != sealed) return false
            tokens[linkId] = null
            return true
        }
        override fun lockKakaoSubject(subject: String): Instant? = null
        override fun unlink(userId: Long, provider: String): Boolean {
            val link = links.firstOrNull { it.provider == provider } ?: return false
            links.remove(link); tokens.remove(link.id)
            return true
        }
        override fun completeLogin(userId: Long, now: Instant) {}
    }
}
