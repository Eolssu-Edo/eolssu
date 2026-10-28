package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisEmailVerificationRepository(
    private val mapper: EmailVerificationMapper,
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : EmailVerificationRepository {
    private val transaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    override fun <T : Any> withVerificationTransaction(action: () -> T): T = checkNotNull(transaction.execute {
        // 하루 100통 안쪽의 1차 규모: DB별 한 잠금으로 빈 이메일의 첫 발송도 직렬화한다.
        // 이메일 collation과 잠금 키가 달라 같은 DB 식별자의 제한을 우회하는 경우도 막는다.
        val lock = checkNotNull(jdbc.queryForObject("SELECT SHA2(CONCAT(DATABASE(), ':auth-email-verifications'), 256)", String::class.java))
        if (jdbc.queryForObject("SELECT GET_LOCK(?, 5)", Int::class.java, lock) != 1) throw VerificationStorageBusy()
        val connection = DataSourceUtils.getConnection(checkNotNull(jdbc.dataSource))
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                // GET_LOCK은 commit/rollback으로 풀리지 않는다. 커밋 완료 뒤 같은 연결에서 해제한다.
                connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                    statement.setString(1, lock)
                    statement.executeQuery().use { result -> check(result.next() && result.getInt(1) == 1) }
                }
            }
        })
        action()
    })

    override fun memberExists(email: String) = mapper.memberExists(email)
    override fun sendHistory(email: String, dayStart: Instant): VerificationSendHistory = mapper.sendHistory(email, dayStart.utc()).let {
        VerificationSendHistory(it.lastSentAt?.toInstant(ZoneOffset.UTC), it.dailyCount.toInt())
    }
    override fun invalidatePrevious(email: String, purpose: VerificationPurpose, now: Instant) {
        mapper.invalidatePrevious(email, purpose.name, now.utc())
    }
    override fun insert(verification: NewEmailVerification): Long {
        val row = EmailVerificationInsert(verification.email, verification.purpose.name, verification.codeHash, verification.expiresAt.utc(), verification.createdAt.utc())
        check(mapper.insert(row) == 1)
        check(row.id > 0)
        return row.id
    }
    override fun findById(id: Long) = mapper.findById(id)?.toVerification()
    override fun findByIdForUpdate(id: Long) = mapper.findByIdForUpdate(id)?.toVerification()
    override fun recordMismatch(id: Long) { check(mapper.recordMismatch(id) == 1) }
    override fun replaceUndeliveredCodeHash(id: Long, codeHash: String) { check(mapper.replaceUndeliveredCodeHash(id, codeHash) == 1) }
    override fun markVerified(id: Long, tokenHash: String, verifiedAt: Instant, tokenExpiresAt: Instant) {
        check(mapper.markVerified(id, tokenHash, verifiedAt.utc(), tokenExpiresAt.utc()) == 1)
    }
    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun EmailVerificationRow.toVerification() = EmailVerification(
        id, email, VerificationPurpose.valueOf(purpose), codeHash, attemptCount.toInt(), expiresAt.toInstant(ZoneOffset.UTC),
        verifiedAt?.toInstant(ZoneOffset.UTC), invalidatedAt?.toInstant(ZoneOffset.UTC),
    )
}
