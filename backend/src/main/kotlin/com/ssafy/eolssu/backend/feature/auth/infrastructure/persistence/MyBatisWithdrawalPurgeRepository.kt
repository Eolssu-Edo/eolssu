package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisWithdrawalPurgeRepository(
    private val mapper: WithdrawalPurgeMapper,
    transactionManager: PlatformTransactionManager,
) : WithdrawalPurgeRepository {
    private val transaction = TransactionTemplate(transactionManager)

    override fun dueMembers(now: Instant, afterUserId: Long, limit: Int) = mapper.findDueMembers(now.utc(), afterUserId, limit)
    override fun preparePurge(userId: Long, now: Instant): List<String>? = transaction.execute {
        mapper.lockDueMemberEmail(userId, now.utc()) ?: return@execute null
        mapper.findRecordingKeys(userId)
    }

    override fun purge(userId: Long, now: Instant): Boolean = transaction.execute {
        val email = mapper.lockDueMemberEmail(userId, now.utc()) ?: return@execute false
        mapper.lockSessions(userId)
        mapper.deleteNoteResults(userId)
        mapper.deleteEvaluations(userId)
        mapper.deleteSessions(userId)
        mapper.deleteScoreViews(userId)
        mapper.deleteTermAgreements(userId)
        mapper.deleteSongSettings(userId)
        mapper.deleteLessonProgress(userId)
        mapper.deleteRefreshTokens(userId)
        mapper.deleteSocialAccounts(userId)
        mapper.deleteEmailVerifications(email)
        check(mapper.anonymize(userId, now.utc()) == 1)
        true
    } == true

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
