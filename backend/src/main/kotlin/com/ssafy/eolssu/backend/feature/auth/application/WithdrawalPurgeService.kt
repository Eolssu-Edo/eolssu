package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * USR-05 규칙 3: 삭제 예정 시각이 지난 탈퇴 대기 회원을 매일 04:00(Asia/Seoul)에 지운다.
 * 녹음 파일은 트랜잭션에 묶이지 않으므로 먼저 지우고, 실패한 회원은 DB를 건드리지 않고 다음 날로 미룬다.
 * 백업(매시 10분)과 겹치지 않게 회원 단위의 짧은 트랜잭션으로 처리한다.
 */
class WithdrawalPurgeService(
    private val repository: WithdrawalPurgeRepository,
    private val deleteRecording: (String) -> Unit,
    private val clock: Clock,
    private val batchSize: Int = 100,
) {
    fun purgeDue(): PurgeSummary {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        var purged = 0
        var deferred = 0
        var skipped = 0
        var afterUserId = 0L
        while (true) {
            val userIds = repository.dueMembers(now, afterUserId, batchSize)
            if (userIds.isEmpty()) break
            for (userId in userIds) {
                when {
                    !deleteRecordings(userId) -> deferred++
                    repository.purge(userId, now) -> purged++
                    else -> skipped++
                }
            }
            afterUserId = userIds.last()
        }
        return PurgeSummary(purged, deferred, skipped)
    }

    private fun deleteRecordings(userId: Long): Boolean = try {
        repository.recordingKeys(userId).forEach(deleteRecording)
        true
    } catch (e: Exception) {
        // 저장소 키·서명 URL이 남지 않게 예외 종류만 남긴다.
        log.warn("Deferred withdrawal purge of user {}: recording deletion failed ({})", userId, e.javaClass.simpleName)
        false
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun runScheduled() {
        val started = System.nanoTime()
        val summary = purgeDue()
        val elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis()
        log.info("Withdrawal purge finished in {} ms: purged={}, deferred={}, skipped={}", elapsed, summary.purged, summary.deferred, summary.skipped)
    }

    private companion object {
        val log = LoggerFactory.getLogger(WithdrawalPurgeService::class.java)
    }
}

/** purged: 지움, deferred: 녹음 삭제 실패로 다음 날로, skipped: 그 사이 탈퇴를 취소해 지우지 않음. */
data class PurgeSummary(val purged: Int, val deferred: Int, val skipped: Int)
