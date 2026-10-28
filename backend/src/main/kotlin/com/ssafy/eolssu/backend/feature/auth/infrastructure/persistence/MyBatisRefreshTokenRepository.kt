package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisRefreshTokenRepository(
    private val mapper: RefreshTokenMapper,
    private val users: UserMapper,
    transactionManager: PlatformTransactionManager,
) : RefreshTokenRepository, TokenRefreshRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant, now: Instant): Long {
        val row = RefreshTokenInsert(userId, tokenHash, expiresAt.utc(), now.utc())
        check(mapper.insert(row) == 1)
        check(row.id > 0)
        return row.id
    }

    override fun findForUpdate(tokenHash: String) = mapper.findByTokenHashForUpdate(tokenHash)?.let {
        StoredRefreshToken(it.id, it.userId, it.expiresAt.toInstant(ZoneOffset.UTC), it.revokedAt != null)
    }

    override fun revoke(id: Long, reason: RevokeReason, now: Instant) { check(mapper.revoke(id, reason.name, now.utc()) == 1) }
    override fun revokeAll(userId: Long, reason: RevokeReason, now: Instant) { mapper.revokeAllActive(userId, reason.name, now.utc()) }
    override fun memberStatus(userId: Long) = users.findById(userId)?.let { MemberStatus.valueOf(it.status) }

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
