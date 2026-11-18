package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.AuthSessionCache

class MyBatisRefreshTokenRepository(
    private val mapper: RefreshTokenMapper,
    private val users: UserMapper,
    transactionManager: PlatformTransactionManager,
    private val sessions: AuthSessionCache? = null,
) : RefreshTokenRepository, TokenRefreshRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long =
        error("refresh 토큰은 로그인 세션 ID와 함께 저장한다")

    override fun insert(userId: Long, tokenHash: String, sessionId: String, expiresAt: Instant?, now: Instant): Long {
        val row = RefreshTokenInsert(userId, tokenHash, sessionId, expiresAt?.utc(), now.utc())
        check(mapper.insert(row) == 1)
        check(row.id > 0)
        return row.id
    }

    override fun find(tokenHash: String) = mapper.findByTokenHash(tokenHash)?.toStored()
    override fun findForUpdate(tokenHash: String) = mapper.findByTokenHashForUpdate(tokenHash)?.toStored()

    private fun RefreshTokenRow.toStored() =
        StoredRefreshToken(id, userId, expiresAt?.toInstant(ZoneOffset.UTC), revokeReason?.let(RevokeReason::valueOf), sessionId)

    override fun revoke(id: Long, reason: RevokeReason, now: Instant) {
        check(mapper.revoke(id, reason.name, now.utc()) == 1)
        // 회전은 같은 세션의 새 행이 이어받으므로 세션이 끊기지 않는다.
        if (reason != RevokeReason.ROTATED) mapper.findById(id)?.let { sessions?.evictAfterCommit(it.userId) }
    }
    override fun revokeAll(userId: Long, reason: RevokeReason, now: Instant) {
        mapper.revokeAllActive(userId, reason.name, now.utc())
        sessions?.evictAfterCommit(userId)
    }
    override fun memberStatusForUpdate(userId: Long) = users.findStatusForUpdate(userId)?.let(MemberStatus::valueOf)

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
