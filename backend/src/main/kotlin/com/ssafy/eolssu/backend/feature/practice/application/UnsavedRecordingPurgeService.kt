package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.ExpiredRecording
import com.ssafy.eolssu.backend.feature.practice.domain.UNSAVED_RECORDING_RETENTION
import com.ssafy.eolssu.backend.feature.practice.domain.UnsavedRecordingRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * 저장하지 않은 녹음을 업로드(`ended_at`) 30일 뒤 매일 04:30(Asia/Seoul)에 지운다(S15P31E102-375).
 * 탈퇴 일괄 삭제(04:00)·백업(매시 10분)과 겹치지 않게 세션 단위의 짧은 갱신으로 처리한다.
 * DB에서 먼저 비워 저장과 겹친 세션을 갱신 조건으로 걸러 내고, 파일을 지우지 못하면 키를 되돌려 다음 날 다시 지운다.
 */
class UnsavedRecordingPurgeService(
    private val repository: UnsavedRecordingRepository,
    private val deleteRecording: (String) -> Unit,
    private val clock: Clock,
    private val batchSize: Int = 100,
) {
    fun purgeExpired(): RecordingPurgeSummary {
        val cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minus(UNSAVED_RECORDING_RETENTION)
        val counts = IntArray(Outcome.entries.size)
        var afterSessionId = 0L
        while (true) {
            val recordings = repository.findExpired(cutoff, afterSessionId, batchSize)
            if (recordings.isEmpty()) break
            recordings.forEach { counts[purge(it, cutoff).ordinal]++ }
            afterSessionId = recordings.last().sessionId
        }
        return RecordingPurgeSummary(
            deleted = counts[Outcome.DELETED.ordinal],
            skipped = counts[Outcome.SKIPPED.ordinal],
            restored = counts[Outcome.RESTORED.ordinal],
            failed = counts[Outcome.FAILED.ordinal],
        )
    }

    // 저장소 키·서명 URL이 남지 않게 로그에는 세션 ID와 예외 종류만 남긴다.
    private fun purge(recording: ExpiredRecording, cutoff: LocalDateTime): Outcome {
        val cleared = try {
            repository.clear(recording, cutoff)
        } catch (e: Exception) {
            log.error("Clearing the recording of session {} failed ({})", recording.sessionId, e.javaClass.simpleName, e)
            return Outcome.FAILED
        }
        if (!cleared) return Outcome.SKIPPED
        try {
            deleteRecording(recording.key)
            return Outcome.DELETED
        } catch (e: Exception) {
            log.warn("Deleting the recording of session {} failed ({}); restoring it for the next run", recording.sessionId, e.javaClass.simpleName)
        }
        val restored = try {
            repository.restore(recording)
        } catch (e: Exception) {
            log.error("Restoring the recording of session {} failed ({})", recording.sessionId, e.javaClass.simpleName, e)
            false
        }
        return if (restored) Outcome.RESTORED else Outcome.FAILED
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun runScheduled() {
        val started = System.nanoTime()
        var summary: RecordingPurgeSummary? = null
        try {
            summary = purgeExpired()
        } finally {
            // 대상 조회처럼 세션 밖에서 실패해도 걸린 시간은 남긴다.
            val elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis()
            if (summary == null) {
                log.error("Unsaved recording purge stopped after {} ms", elapsed)
            } else {
                log.info(
                    "Unsaved recording purge finished in {} ms: deleted={}, skipped={}, restored={}, failed={}",
                    elapsed, summary.deleted, summary.skipped, summary.restored, summary.failed,
                )
            }
        }
    }

    private enum class Outcome { DELETED, SKIPPED, RESTORED, FAILED }

    private companion object {
        val log = LoggerFactory.getLogger(UnsavedRecordingPurgeService::class.java)
    }
}

/** deleted: 지움, skipped: 그사이 저장·변경돼 그대로 둠, restored: 파일 삭제 실패로 키를 되돌림, failed: DB 오류. 남은 녹음은 다음 날 다시 대상이 된다. */
data class RecordingPurgeSummary(val deleted: Int, val skipped: Int, val restored: Int, val failed: Int)
