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

class SignInServiceTests {
    private var now = Instant.parse("2026-10-28T03:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val repository = MemorySignInRepository()
    private val encoder = object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?) = "hashed:$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?) = encodedPassword == "hashed:$rawPassword"
    }
    private var signingAvailable = true
    private val issuer = AuthTokenIssuer(
        object : AccessTokenSigner {
            override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = if (signingAvailable) "access-$userId" else throw TokenIssuingUnavailable()
        },
        repository, EmailVerificationSecrets(ByteArray(32)), clock,
    )
    private val service = SignInService(repository, encoder, issuer, LoginAttemptLimiter(), clock)

    init {
        repository.members["learner@example.com"] = SignInMember(7, "hashed:Example!234", "연습자", 3, "HANJA", MemberStatus.ACTIVE)
        repository.members["pending@example.com"] = SignInMember(8, "hashed:Example!234", "대기", 3, "HANGUL", MemberStatus.WITHDRAWAL_PENDING)
    }

    @Test
    fun `active member signs in with normalized email records the login and receives tokens`() {
        val signedIn = assertIs<SignInResult.SignedIn>(service.signIn("Learner@Example.com", "Example!234"))
        assertEquals(7, signedIn.member.id)
        assertFalse(signedIn.withdrawalCancelled)
        assertEquals(now, repository.lastLogins[7])
        assertEquals("access-7", signedIn.tokens.accessToken)
    }

    @Test
    fun `withdrawal pending member signs in by cancelling the withdrawal`() {
        val signedIn = assertIs<SignInResult.SignedIn>(service.signIn("pending@example.com", "Example!234"))
        assertTrue(signedIn.withdrawalCancelled)
        assertEquals(setOf(8L), repository.cancelled)
    }

    @Test
    fun `unknown email and wrong password are the same invalid credentials`() {
        assertEquals(SignInResult.InvalidCredentials, service.signIn("absent@example.com", "Example!234"))
        assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Wrong!234"))
        assertTrue(repository.lastLogins.isEmpty())
    }

    @Test
    fun `fifth failure within 15 minutes locks the email for 15 minutes even for the right password`() {
        repeat(4) { assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Wrong!234")) }
        now = now.plusSeconds(60)
        assertEquals(SignInResult.TooManyAttempts(now.plusSeconds(900)), service.signIn("learner@example.com", "Wrong!234"))
        val lockedUntil = now.plusSeconds(900)
        now = now.plusSeconds(899)
        assertEquals(SignInResult.TooManyAttempts(lockedUntil), service.signIn("learner@example.com", "Example!234"))
        now = now.plusSeconds(1)
        assertIs<SignInResult.SignedIn>(service.signIn("learner@example.com", "Example!234"))
    }

    @Test
    fun `accent and case variants that the database treats as the same member share one lock`() {
        repository.aliases["léarner@example.com"] = "learner@example.com"
        repeat(5) { service.signIn("learner@example.com", "Wrong!234") }
        assertIs<SignInResult.TooManyAttempts>(service.signIn("Léarner@example.com", "Example!234"))
    }

    @Test
    fun `failures older than 15 minutes do not count and success resets the count`() {
        repeat(4) { service.signIn("learner@example.com", "Wrong!234") }
        now = now.plusSeconds(901)
        assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Wrong!234"))
        assertIs<SignInResult.SignedIn>(service.signIn("learner@example.com", "Example!234"))
        repeat(4) { assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Wrong!234")) }
    }

    @Test
    fun `missing signing key is unavailable and keeps the login unrecorded`() {
        signingAvailable = false
        assertEquals(SignInResult.Unavailable, service.signIn("learner@example.com", "Example!234"))
    }
}

internal class MemorySignInRepository : SignInRepository, RefreshTokenRepository {
    val members = mutableMapOf<String, SignInMember>()
    // MySQL 기본 콜레이션처럼 악센트·대소문자가 다른 이메일을 같은 행으로 찾는다.
    val aliases = mutableMapOf<String, String>()
    val lastLogins = mutableMapOf<Long, Instant>()
    val cancelled = mutableSetOf<Long>()

    override fun <T : Any> inTransaction(action: () -> T): T = action()
    override fun findMember(email: String) = members[aliases[email] ?: email]
    override fun findMemberForUpdate(email: String) = members[aliases[email] ?: email]
    override fun cancelWithdrawal(userId: Long, now: Instant) { cancelled.add(userId) }
    override fun recordLogin(userId: Long, now: Instant) { lastLogins[userId] = now }
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant, now: Instant) = 1L
}
