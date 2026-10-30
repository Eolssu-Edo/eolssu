package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * USR-05 규칙 3: 삭제 예정 시각이 지난 탈퇴 대기 회원을 매일 04:00(Asia/Seoul)에 지운다.
 * 파일 삭제 전에 회원 잠금으로 기존 취소의 완료를 확인한다. 이후의 취소는 로그인에서 기한으로 거절한다.
 * 녹음 파일은 잠금을 해제한 뒤 지우고, 실패한 회원은 DB를 건드리지 않고 다음 날로 미룬다.
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
        var failed = 0
        var afterUserId = 0L
        while (true) {
            val userIds = repository.dueMembers(now, afterUserId, batchSize)
            if (userIds.isEmpty()) break
            for (userId in userIds) {
                val keys = try {
                    repository.preparePurge(userId, now)
                } catch (e: Exception) {
                    log.error("Withdrawal purge preparation for user {} failed ({})", userId, e.javaClass.simpleName, e)
                    failed++
                    continue
                }
                if (keys == null) {
                    skipped++
                    continue
                }
                when {
                    !deleteRecordings(userId, keys) -> deferred++
                    else -> when (purge(userId, now)) {
                        true -> purged++
                        false -> skipped++
                        null -> failed++
                    }
                }
            }
            afterUserId = userIds.last()
        }
        return PurgeSummary(purged, deferred, skipped, failed)
    }

    // 한 회원의 DB 오류(잠금 대기 초과 등)로 그날 나머지 회원이 멈추지 않게 회원 단위로 격리한다. 그 회원은 다음 날 다시 대상이 된다.
    private fun purge(userId: Long, now: Instant): Boolean? = try {
        repository.purge(userId, now)
    } catch (e: Exception) {
        log.error("Withdrawal purge of user {} failed ({})", userId, e.javaClass.simpleName, e)
        null
    }

    private fun deleteRecordings(userId: Long, keys: List<String>): Boolean = try {
        keys.forEach(deleteRecording)
        true
    } catch (e: Exception) {
        // 저장소 키·서명 URL이 남지 않게 예외 종류만 남긴다.
        log.warn("Deferred withdrawal purge of user {}: recording deletion failed ({})", userId, e.javaClass.simpleName)
        false
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun runScheduled() {
        val started = System.nanoTime()
        var summary: PurgeSummary? = null
        try {
            summary = purgeDue()
        } finally {
            // 대상 조회처럼 회원 밖에서 실패해도 걸린 시간은 남긴다.
            val elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis()
            if (summary == null) {
                log.error("Withdrawal purge stopped after {} ms", elapsed)
            } else {
                log.info(
                    "Withdrawal purge finished in {} ms: purged={}, deferred={}, skipped={}, failed={}",
                    elapsed, summary.purged, summary.deferred, summary.skipped, summary.failed,
                )
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(WithdrawalPurgeService::class.java)
    }
}

/** purged: 지움, deferred: 녹음 삭제 실패, skipped: 재확인 시 삭제 대상 아님, failed: DB 오류. 실패는 다음 날 재시도한다. */
data class PurgeSummary(val purged: Int, val deferred: Int, val skipped: Int, val failed: Int)
