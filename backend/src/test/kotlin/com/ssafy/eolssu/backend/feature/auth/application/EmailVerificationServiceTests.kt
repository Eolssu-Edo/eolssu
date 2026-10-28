package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*
import org.junit.jupiter.api.Test

class EmailVerificationServiceTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val repository = MemoryVerificationRepository()
    private val deliveries = mutableListOf<String>()
    private val secrets = EmailVerificationSecrets(ByteArray(32) { it.toByte() })
    private var clock = Clock.fixed(now, ZoneOffset.UTC)
    private val mail = object : VerificationMailSender {
        override fun send(email: String, purpose: VerificationPurpose, code: String) { deliveries.add(code) }
    }
    private fun service(sender: VerificationMailSender = mail) = EmailVerificationService(repository, sender, secrets, clock)

    @Test
    fun `sends a six digit code to the normalized email and persists only the hash`() {
        val sent = assertIs<SendVerificationResult.Accepted>(service().send("Learner@Example.COM", VerificationPurpose.SIGN_UP))
        assertEquals(now.plusSeconds(180), sent.expiresAt)
        assertEquals(now.plusSeconds(60), sent.resendAvailableAt)
        val stored = assertNotNull(repository.records[sent.verificationId])
        assertEquals("learner@example.com", stored.email)
        assertEquals(1, deliveries.size)
        assertTrue(deliveries.single().matches(Regex("[0-9]{6}")))
        assertTrue(secrets.matchesCode(stored.email, stored.purpose, deliveries.single(), stored.codeHash))
        assertNotEquals(deliveries.single(), stored.codeHash)
    }

    @Test
    fun `resends only after 60 seconds across purposes and invalidates the previous same purpose code`() {
        val first = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        clock = Clock.fixed(now.plusSeconds(59), ZoneOffset.UTC)
        assertEquals(SendVerificationResult.RateLimited(now.plusSeconds(60)), service().send("LEARNER@example.com", VerificationPurpose.PASSWORD_RESET))
        assertEquals(1, repository.records.size)
        clock = Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC)
        assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        assertEquals(now.plusSeconds(60), repository.records.getValue(first.verificationId).invalidatedAt)
    }

    @Test
    fun `daily quota shares all purposes and resets at midnight in Seoul`() {
        val start = Instant.parse("2026-10-28T14:48:00Z")
        repeat(10) { index ->
            clock = Clock.fixed(start.plusSeconds(index * 60L), ZoneOffset.UTC)
            assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.entries[index % 3]))
        }
        clock = Clock.fixed(Instant.parse("2026-10-28T14:58:00Z"), ZoneOffset.UTC)
        assertEquals(SendVerificationResult.RateLimited(Instant.parse("2026-10-28T15:00:00Z")), service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        clock = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC)
        assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        assertEquals(11, repository.records.size)
    }

    @Test
    fun `sign up rejects registered emails before sending`() {
        repository.members.add("learner@example.com")
        assertEquals(SendVerificationResult.AlreadyRegistered, service().send("LEARNER@example.com", VerificationPurpose.SIGN_UP))
        assertTrue(deliveries.isEmpty())
        assertTrue(repository.records.isEmpty())
    }

    @Test
    fun `password reset hides account existence while only sending to members`() {
        assertIs<SendVerificationResult.Accepted>(service().send("absent@example.com", VerificationPurpose.PASSWORD_RESET))
        assertTrue(deliveries.isEmpty())
        repository.members.add("member@example.com")
        assertIs<SendVerificationResult.Accepted>(service().send("member@example.com", VerificationPurpose.PASSWORD_RESET))
        assertEquals(1, deliveries.size)
    }

    @Test
    fun `mail failure is reported as unavailable`() {
        val failing = object : VerificationMailSender {
            override fun send(email: String, purpose: VerificationPurpose, code: String): Unit = throw VerificationMailUnavailable()
        }
        assertEquals(SendVerificationResult.Unavailable, service(failing).send("learner@example.com", VerificationPurpose.SIGN_UP))
    }

    @Test
    fun `password reset delivery failure stays accepted but cannot verify the undelivered code`() {
        repository.members.add("member@example.com")
        var undelivered = ""
        val failing = object : VerificationMailSender {
            override fun send(email: String, purpose: VerificationPurpose, code: String) { undelivered = code; throw VerificationMailUnavailable() }
        }
        val accepted = assertIs<SendVerificationResult.Accepted>(service(failing).send("member@example.com", VerificationPurpose.PASSWORD_RESET))
        assertEquals(1, repository.records.size)
        assertNotEquals(secrets.hashCode("member@example.com", VerificationPurpose.PASSWORD_RESET, undelivered), repository.records.getValue(accepted.verificationId).codeHash)
        assertEquals(ConfirmVerificationResult.Mismatch(4), service().confirm(accepted.verificationId, undelivered))
    }

    @Test
    fun `account merge hides account existence even when mail delivery fails`() {
        val failing = object : VerificationMailSender {
            override fun send(email: String, purpose: VerificationPurpose, code: String): Unit = throw VerificationMailUnavailable()
        }
        repository.members.add("member@example.com")
        assertIs<SendVerificationResult.Accepted>(service(failing).send("member@example.com", VerificationPurpose.ACCOUNT_MERGE))
        assertIs<SendVerificationResult.Accepted>(service(failing).send("absent@example.com", VerificationPurpose.ACCOUNT_MERGE))
    }

    @Test
    fun `successful confirmation issues one 30 minute token and stores only its hash`() {
        val sent = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        clock = Clock.fixed(now.plusSeconds(20), ZoneOffset.UTC)
        val verified = assertIs<ConfirmVerificationResult.Verified>(service().confirm(sent.verificationId, deliveries.single()))
        assertEquals("learner@example.com", verified.email)
        assertEquals(now.plusSeconds(1820), verified.expiresAt)
        assertEquals(secrets.hashToken(verified.verificationToken), repository.tokens.getValue(sent.verificationId).first)
        assertNotEquals(verified.verificationToken, repository.tokens.getValue(sent.verificationId).first)
        assertEquals(ConfirmVerificationResult.Expired, service().confirm(sent.verificationId, deliveries.single()))
    }

    @Test
    fun `fifth wrong code is persisted and blocks even a later correct code`() {
        val sent = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        val correct = deliveries.single()
        val wrong = if (correct == "000000") "111111" else "000000"
        repeat(4) { assertEquals(ConfirmVerificationResult.Mismatch(4 - it), service().confirm(sent.verificationId, wrong)) }
        assertEquals(ConfirmVerificationResult.AttemptsExceeded, service().confirm(sent.verificationId, wrong))
        assertEquals(5, repository.records.getValue(sent.verificationId).failedAttempts)
        assertEquals(ConfirmVerificationResult.AttemptsExceeded, service().confirm(sent.verificationId, correct))
        assertTrue(repository.tokens.isEmpty())
    }

    @Test
    fun `a correct fifth attempt succeeds after four wrong codes`() {
        val sent = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        val correct = deliveries.single()
        repeat(4) { service().confirm(sent.verificationId, if (correct == "000000") "111111" else "000000") }
        assertIs<ConfirmVerificationResult.Verified>(service().confirm(sent.verificationId, correct))
    }

    @Test
    fun `code expires exactly at three minutes and resend invalidates it immediately`() {
        val sent = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        val firstCode = deliveries.single()
        clock = Clock.fixed(now.plusSeconds(180), ZoneOffset.UTC)
        assertEquals(ConfirmVerificationResult.Expired, service().confirm(sent.verificationId, firstCode))
        val newer = assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        val newCode = deliveries.last()
        clock = Clock.fixed(now.plusSeconds(240), ZoneOffset.UTC)
        assertIs<SendVerificationResult.Accepted>(service().send("learner@example.com", VerificationPurpose.SIGN_UP))
        assertEquals(ConfirmVerificationResult.Expired, service().confirm(newer.verificationId, newCode))
        assertTrue(repository.tokens.isEmpty())
    }

    @Test
    fun `unknown verification ID is missing`() {
        assertEquals(ConfirmVerificationResult.Missing, service().confirm(999, "123456"))
    }
}

internal class MemoryVerificationRepository : EmailVerificationRepository {
    val records = linkedMapOf<Long, EmailVerification>()
    val createdAt = mutableMapOf<Long, Instant>()
    val members = mutableSetOf<String>()
    val tokens = mutableMapOf<Long, Pair<String, Instant>>()
    override fun <T : Any> withVerificationTransaction(action: () -> T): T = action()
    override fun memberExists(email: String) = email in members
    override fun sendHistory(email: String, dayStart: Instant): VerificationSendHistory {
        val dates = records.values.filter { it.email == email }.map { createdAt.getValue(it.id) }
        return VerificationSendHistory(dates.maxOrNull(), dates.count { !it.isBefore(dayStart) })
    }
    override fun invalidatePrevious(email: String, purpose: VerificationPurpose, now: Instant) {
        records.replaceAll { _, row -> if (row.email == email && row.purpose == purpose) row.copy(invalidatedAt = now) else row }
    }
    override fun insert(verification: NewEmailVerification): Long {
        val id = (records.keys.maxOrNull() ?: 0) + 1
        records[id] = EmailVerification(id, verification.email, verification.purpose, verification.codeHash, 0, verification.expiresAt, null, null)
        createdAt[id] = verification.createdAt
        return id
    }
    override fun findById(id: Long) = records[id]
    override fun findByIdForUpdate(id: Long) = records[id]
    override fun recordMismatch(id: Long) { records.computeIfPresent(id) { _, row -> row.copy(failedAttempts = row.failedAttempts + 1) } }
    override fun replaceUndeliveredCodeHash(id: Long, codeHash: String) { records.computeIfPresent(id) { _, row -> row.copy(codeHash = codeHash) } }
    override fun markVerified(id: Long, tokenHash: String, verifiedAt: Instant, tokenExpiresAt: Instant) {
        records.computeIfPresent(id) { _, row -> row.copy(verifiedAt = verifiedAt) }
        tokens[id] = tokenHash to tokenExpiresAt
    }
}
