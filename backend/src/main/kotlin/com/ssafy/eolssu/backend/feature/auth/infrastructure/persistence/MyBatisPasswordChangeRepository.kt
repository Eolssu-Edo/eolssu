package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.PasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.domain.RevokeReason
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.AuthSessionCache

class MyBatisPasswordChangeRepository(
    private val users: UserMapper,
    private val refreshTokens: RefreshTokenMapper,
    transactionManager: PlatformTransactionManager,
    private val sessions: AuthSessionCache? = null,
) : PasswordChangeRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun findActivePasswordHash(userId: Long) = users.findActivePasswordHash(userId)
    override fun findActivePasswordHashForUpdate(userId: Long) = users.findActivePasswordHashForUpdate(userId)
    override fun updatePasswordHash(userId: Long, passwordHash: String) { check(users.updatePasswordHash(userId, passwordHash) == 1) }
    override fun revokeAllRefreshTokens(userId: Long, now: Instant) {
        refreshTokens.revokeAllActive(userId, RevokeReason.PASSWORD_CHANGED.name, LocalDateTime.ofInstant(now, ZoneOffset.UTC))
        sessions?.evictAfterCommit(userId)
    }
}
