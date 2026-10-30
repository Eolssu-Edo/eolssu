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
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            if (repository.inTransaction) matchedInsideTransaction = true
            return encodedPassword == "hashed:$rawPassword"
        }
    }
    private var matchedInsideTransaction = false
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
        repository.members["pending@example.com"] = pendingMember(Instant.parse("2026-10-01T03:00:00Z"))
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
    fun `withdrawal can be cancelled just before thirty days`() {
        now = Instant.parse("2026-10-31T02:59:59.999999Z")
        assertIs<SignInResult.SignedIn>(service.signIn("pending@example.com", "Example!234"))
        assertEquals(setOf(8L), repository.cancelled)
    }

    @Test
    fun `withdrawal cannot be cancelled at or after thirty days`() {
        for (time in listOf("2026-10-31T03:00:00Z", "2026-10-31T03:00:00.000001Z")) {
            now = Instant.parse(time)
            assertEquals(SignInResult.InvalidCredentials, service.signIn("pending@example.com", "Example!234"))
        }
        assertTrue(repository.cancelled.isEmpty())
        assertTrue(repository.lastLogins.isEmpty())
        assertEquals(0, repository.issuedTokens)
    }

    @Test
    fun `expiry while waiting for member lock uses the time after the lock`() {
        now = Instant.parse("2026-10-31T02:59:59.999999Z")
        repository.beforeLockedRead = { now = Instant.parse("2026-10-31T03:00:00Z") }
        assertEquals(SignInResult.InvalidCredentials, service.signIn("pending@example.com", "Example!234"))
        assertTrue(repository.cancelled.isEmpty())
        assertTrue(repository.lastLogins.isEmpty())
        assertEquals(0, repository.issuedTokens)
    }

    @Test
    fun `pending member with missing withdrawal time fails closed`() {
        repository.members["pending@example.com"] = pendingMember(null)
        assertEquals(SignInResult.InvalidCredentials, service.signIn("pending@example.com", "Example!234"))
        assertTrue(repository.cancelled.isEmpty())
        assertEquals(0, repository.issuedTokens)
    }

    private fun pendingMember(requestedAt: Instant?) =
        SignInMember(8, "hashed:Example!234", "대기", 3, "HANGUL", MemberStatus.WITHDRAWAL_PENDING, requestedAt)

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
    fun `variants of an unknown email share one lock so the lock does not reveal membership`() {
        repository.aliases["kím@example.com"] = "kim@example.com"
        repeat(5) { service.signIn("kim@example.com", "Wrong!234") }
        assertIs<SignInResult.TooManyAttempts>(service.signIn("kím@example.com", "Wrong!234"))
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
    fun `password hashing runs outside the database transaction`() {
        assertIs<SignInResult.SignedIn>(service.signIn("learner@example.com", "Example!234"))
        assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Wrong!234"))
        assertEquals(SignInResult.InvalidCredentials, service.signIn("absent@example.com", "Example!234"))
        assertFalse(matchedInsideTransaction)
    }

    @Test
    fun `a password changed after verification does not sign in`() {
        repository.beforeLockedRead = {
            repository.members["learner@example.com"] = SignInMember(7, "hashed:Changed!234", "연습자", 3, "HANJA", MemberStatus.ACTIVE)
        }
        assertEquals(SignInResult.InvalidCredentials, service.signIn("learner@example.com", "Example!234"))
        assertTrue(repository.lastLogins.isEmpty())
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
    var issuedTokens = 0

    var inTransaction = false
    var beforeLockedRead: () -> Unit = {}

    override fun <T : Any> inTransaction(action: () -> T): T {
        inTransaction = true
        try { return action() } finally { inTransaction = false }
    }
    override fun lockKey(email: String) = aliases[email] ?: email
    override fun findMember(email: String) = members[aliases[email] ?: email]
    override fun findMemberForUpdate(email: String): SignInMember? {
        beforeLockedRead()
        return members[aliases[email] ?: email]
    }
    override fun cancelWithdrawal(userId: Long, now: Instant) { cancelled.add(userId) }
    override fun recordLogin(userId: Long, now: Instant) { lastLogins[userId] = now }
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long {
        issuedTokens++
        return 1L
    }
}
