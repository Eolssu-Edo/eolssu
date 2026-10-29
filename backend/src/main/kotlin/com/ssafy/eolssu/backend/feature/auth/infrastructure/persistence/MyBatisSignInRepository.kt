package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.MemberStatus
import com.ssafy.eolssu.backend.feature.auth.domain.SignInMember
import com.ssafy.eolssu.backend.feature.auth.domain.SignInRepository
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisSignInRepository(private val users: UserMapper, transactionManager: PlatformTransactionManager) : SignInRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(action: () -> T): T = checkNotNull(transaction.execute { action() })

    override fun lockKey(email: String) = users.emailSortKey(email)
    override fun findMember(email: String) = users.findSignInMember(email)?.toMember()
    override fun findMemberForUpdate(email: String) = users.findSignInMemberForUpdate(email)?.toMember()

    override fun cancelWithdrawal(userId: Long, now: Instant) { check(users.cancelWithdrawal(userId, now.utc()) == 1) }
    override fun recordLogin(userId: Long, now: Instant) { check(users.recordLogin(userId, now.utc()) == 1) }

    private fun UserRow.toMember() =
        SignInMember(id, checkNotNull(passwordHash), checkNotNull(nickname), instrumentId, notationMode, MemberStatus.valueOf(status))

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
