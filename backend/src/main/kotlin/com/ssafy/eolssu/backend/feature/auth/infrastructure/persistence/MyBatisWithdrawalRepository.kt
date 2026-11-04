package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.RevokeReason
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalRepository
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.AuthSessionCache

class MyBatisWithdrawalRepository(
    private val users: UserMapper,
    private val refreshTokens: RefreshTokenMapper,
    transactionManager: PlatformTransactionManager,
    private val sessions: AuthSessionCache? = null,
) : WithdrawalRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun findActivePasswordHash(userId: Long) = users.findActivePasswordHash(userId)
    override fun findActivePasswordHashForUpdate(userId: Long) = users.findActivePasswordHashForUpdate(userId)
    override fun markWithdrawalPending(userId: Long, requestedAt: Instant, purgeAt: Instant) {
        check(users.markWithdrawalPending(userId, requestedAt.utc(), purgeAt.utc()) == 1)
        sessions?.evictAfterCommit(userId)
    }
    override fun revokeAllRefreshTokens(userId: Long, now: Instant) {
        refreshTokens.revokeAllActive(userId, RevokeReason.WITHDRAWN.name, now.utc())
        sessions?.evictAfterCommit(userId)
    }

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
