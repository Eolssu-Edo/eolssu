package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.AuthSessionCache

class MyBatisPasswordResetRepository(
    private val users: UserMapper,
    private val verifications: EmailVerificationMapper,
    private val refreshTokens: RefreshTokenMapper,
    manager: PlatformTransactionManager,
    private val sessions: AuthSessionCache? = null,
) : PasswordResetRepository {
    private val transaction = TransactionTemplate(manager)
    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })
    override fun findVerification(tokenHash: String) = verifications.findByTokenHash(tokenHash)?.proof()
    override fun findVerificationForUpdate(tokenHash: String) = verifications.findByTokenHashForUpdate(tokenHash)?.proof()
    override fun findMember(email: String) = users.findSignInMember(email)?.member()
    // purge와 같은 PK부터 잠근다. email 인덱스를 먼저 잡으면 익명화(email=NULL)와 교착한다.
    override fun findMemberForUpdate(userId: Long) = users.findByIdForUpdate(userId)?.member()
    override fun updatePasswordHash(userId: Long, passwordHash: String) { check(users.resetPasswordHash(userId, passwordHash) == 1) }
    override fun markConsumed(verificationId: Long, now: Instant) { check(verifications.markConsumed(verificationId, now.utc()) == 1) }
    override fun revokeAllRefreshTokens(userId: Long, now: Instant) {
        refreshTokens.revokeAllActive(userId, RevokeReason.PASSWORD_CHANGED.name, now.utc())
        sessions?.evictAfterCommit(userId)
    }
    private fun UserRow.member(): ResetMember? = if (status in setOf("ACTIVE", "WITHDRAWAL_PENDING") && email != null && passwordHash != null) ResetMember(id,email,passwordHash) else null
    private fun EmailVerificationRow.proof(): ConsumableVerification? = if (verifiedAt == null) null else
        ConsumableVerification(id,email,VerificationPurpose.valueOf(purpose),tokenExpiresAt?.toInstant(ZoneOffset.UTC),consumedAt?.toInstant(ZoneOffset.UTC),invalidatedAt?.toInstant(ZoneOffset.UTC))
    private fun Instant.utc() = LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
