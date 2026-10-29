package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import com.ssafy.eolssu.backend.feature.term.domain.Term
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class SignUpServiceTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val secrets = EmailVerificationSecrets(ByteArray(32) { it.toByte() })
    private val repository = MemorySignUpRepository()
    private val terms = listOf(
        Term(1, "AGE_OVER_14", "만 14세 이상", true, "2026-10", "https://example.test/age"),
        Term(2, "SERVICE_TERMS", "서비스 이용약관", true, "2026-10", "https://example.test/service"),
        Term(5, "MARKETING_NOTICE", "소식 알림", false, "2026-10", "https://example.test/news"),
    )
    private val termRepository = object : TermRepository {
        override fun findCurrent(asOf: Instant) = terms
    }
    private val encoder = object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?) = "hashed:$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?) = encodedPassword == "hashed:$rawPassword"
    }
    private var signingAvailable = true
    private val issuer = AuthTokenIssuer(
        object : AccessTokenSigner {
            override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant) = if (signingAvailable) "access-$userId" else throw TokenIssuingUnavailable()
        },
        repository, secrets, Clock.fixed(now, ZoneOffset.UTC),
    )
    private val service = SignUpService(repository, termRepository, encoder, secrets, issuer, Clock.fixed(now, ZoneOffset.UTC))
    private val token = "verified-token"

    init {
        repository.instruments["DANSO"] = 3L
        repository.verifications[secrets.hashToken(token)] = ConsumableVerification(11, "learner@example.com", VerificationPurpose.SIGN_UP, now.plusSeconds(60), null, null)
    }

    private fun command(
        email: String = "Learner@Example.com",
        password: String = "Example!234",
        agreements: List<TermAgreement> = listOf(TermAgreement(1, "2026-10", true), TermAgreement(2, "2026-10", true), TermAgreement(5, "2026-10", false)),
        instrumentId: Long? = null,
    ) = SignUpCommand(email, password, "연습자", token, agreements, instrumentId, null)

    @Test
    fun `creates an active member with hashed password agreements default instrument and login tokens`() {
        val created = assertIs<SignUpResult.Created>(service.signUp(command()))
        val user = repository.users.single()
        assertEquals("learner@example.com", user.email)
        assertEquals("hashed:Example!234", user.passwordHash)
        assertEquals(3L, user.instrumentId)
        assertEquals("HANJA", user.notationMode)
        assertEquals(listOf(1L to true, 2L to true, 5L to false), repository.agreements.map { it.termId to it.agreed })
        assertNotNull(repository.consumed[11])
        assertEquals("access-${created.userId}", created.tokens.accessToken)
        assertEquals(secrets.hashToken(created.tokens.refreshToken), repository.refreshTokens.single())
    }

    @Test
    fun `accepts a chosen active instrument and rejects unknown instruments`() {
        repository.activeInstrumentIds.add(9)
        assertEquals(9L, assertIs<SignUpResult.Created>(service.signUp(command(instrumentId = 9))).instrumentId)
        repository.reset()
        assertIs<SignUpResult.InvalidRequest>(service.signUp(command(instrumentId = 404)))
    }

    @Test
    fun `rejects a weak password before touching the verification`() {
        assertIs<SignUpResult.InvalidRequest>(service.signUp(command(password = "password")))
        assertNull(repository.consumed[11])
    }

    @Test
    fun `rejects missing expired consumed invalidated or mismatched verification tokens`() {
        val base = repository.verifications.getValue(secrets.hashToken(token))
        for (broken in listOf(
            null,
            ConsumableVerification(base.id, base.email, base.purpose, now, null, null),
            ConsumableVerification(base.id, base.email, base.purpose, base.tokenExpiresAt, now, null),
            ConsumableVerification(base.id, base.email, base.purpose, base.tokenExpiresAt, null, now),
            ConsumableVerification(base.id, "other@example.com", base.purpose, base.tokenExpiresAt, null, null),
            ConsumableVerification(base.id, base.email, VerificationPurpose.PASSWORD_RESET, base.tokenExpiresAt, null, null),
        )) {
            if (broken == null) repository.verifications.clear() else repository.verifications[secrets.hashToken(token)] = broken
            assertEquals(SignUpResult.EmailNotVerified, service.signUp(command()))
        }
        assertTrue(repository.users.isEmpty())
    }

    @Test
    fun `rejects an already registered email`() {
        repository.members.add("learner@example.com")
        assertEquals(SignUpResult.EmailAlreadyExists, service.signUp(command()))
        assertTrue(repository.users.isEmpty())
    }

    @Test
    fun `requires every current required term at its current version`() {
        for (agreements in listOf(
            listOf(TermAgreement(2, "2026-10", true)),
            listOf(TermAgreement(1, "2026-10", false), TermAgreement(2, "2026-10", true)),
            listOf(TermAgreement(1, "2026-09", true), TermAgreement(2, "2026-10", true)),
            listOf(TermAgreement(1, "2026-10", true), TermAgreement(2, "2026-10", true), TermAgreement(99, "2026-10", true)),
        )) {
            assertEquals(SignUpResult.TermsAgreementRequired, service.signUp(command(agreements = agreements)))
        }
        assertTrue(repository.users.isEmpty())
    }

    @Test
    fun `missing default instrument or signing key is unavailable`() {
        repository.instruments.clear()
        assertEquals(SignUpResult.Unavailable, service.signUp(command()))
        repository.instruments["DANSO"] = 3L
        signingAvailable = false
        assertEquals(SignUpResult.Unavailable, service.signUp(command()))
    }
}

internal class MemorySignUpRepository : SignUpRepository, RefreshTokenRepository {
    val verifications = mutableMapOf<String, ConsumableVerification>()
    val consumed = mutableMapOf<Long, Instant>()
    val members = mutableSetOf<String>()
    val instruments = mutableMapOf<String, Long>()
    val activeInstrumentIds = mutableSetOf<Long>()
    val users = mutableListOf<NewUser>()
    val agreements = mutableListOf<TermAgreement>()
    val refreshTokens = mutableListOf<String>()

    fun reset() { users.clear(); agreements.clear(); consumed.clear(); refreshTokens.clear() }

    override fun <T : Any> inTransaction(action: () -> T): T = action()
    override fun findVerificationForUpdate(tokenHash: String) = verifications[tokenHash]
    override fun markConsumed(verificationId: Long, now: Instant) { consumed[verificationId] = now }
    override fun memberExists(email: String) = email in members
    override fun activeInstrumentExists(id: Long) = id in activeInstrumentIds
    override fun activeInstrumentIdByCode(code: String) = instruments[code]
    override fun insertUser(user: NewUser, now: Instant): Long { users.add(user); return users.size.toLong() }
    override fun insertAgreements(userId: Long, agreements: List<TermAgreement>, now: Instant) { this.agreements.addAll(agreements) }
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long { refreshTokens.add(tokenHash); return refreshTokens.size.toLong() }
}
