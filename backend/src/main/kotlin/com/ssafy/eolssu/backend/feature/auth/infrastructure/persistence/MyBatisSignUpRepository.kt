package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.UserTermAgreementInsert
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.UserTermAgreementMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserInsert
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisSignUpRepository(
    private val verifications: EmailVerificationMapper,
    private val users: UserMapper,
    private val agreements: UserTermAgreementMapper,
    private val instruments: InstrumentMapper,
    transactionManager: PlatformTransactionManager,
) : SignUpRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun findVerificationForUpdate(tokenHash: String) = verifications.findByTokenHashForUpdate(tokenHash)?.let {
        ConsumableVerification(
            it.id, it.email, VerificationPurpose.valueOf(it.purpose), it.tokenExpiresAt?.toInstant(ZoneOffset.UTC),
            it.consumedAt?.toInstant(ZoneOffset.UTC), it.invalidatedAt?.toInstant(ZoneOffset.UTC),
        )
    }

    override fun markConsumed(verificationId: Long, now: Instant) { check(verifications.markConsumed(verificationId, now.utc()) == 1) }
    override fun memberExists(email: String) = verifications.memberExists(email)
    override fun activeInstrumentExists(id: Long) = instruments.activeExists(id)
    override fun activeInstrumentIdByCode(code: String) = instruments.findActiveIdByCode(code)

    override fun insertUser(user: NewUser, now: Instant): Long {
        val row = UserInsert(user.email, user.passwordHash, user.nickname, user.instrumentId, user.notationMode, now.utc())
        try {
            check(users.insert(row) == 1)
        } catch (_: DuplicateKeyException) {
            throw EmailAlreadyRegistered()
        }
        return row.id
    }

    override fun insertAgreements(userId: Long, agreements: List<TermAgreement>, now: Instant) {
        if (agreements.isEmpty()) return
        check(this.agreements.insertAll(userId, agreements.map { UserTermAgreementInsert(it.termId, it.agreed) }, now.utc()) == agreements.size)
    }

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
