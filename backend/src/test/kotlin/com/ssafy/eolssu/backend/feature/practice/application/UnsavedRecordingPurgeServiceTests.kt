package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.ExpiredRecording
import com.ssafy.eolssu.backend.feature.practice.domain.UnsavedRecordingRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression

@ExtendWith(OutputCaptureExtension::class)
class UnsavedRecordingPurgeServiceTests {
    private val now = Instant.parse("2026-12-01T19:30:00Z")
    private val repository = MemoryUnsavedRecordingRepository()
    private val failingKeys = mutableSetOf<String>()
    private val service = UnsavedRecordingPurgeService(
        repository,
        { key ->
            // 실제 저장소 예외처럼 메시지에 키를 넣어, 서비스가 예외 메시지를 로그에 남기면 아래 로그 검사가 잡게 한다.
            if (key in failingKeys) throw IllegalStateException("storage down for $key")
            repository.events += "delete $key"
        },
        Clock.fixed(now, ZoneOffset.UTC),
        batchSize = 2,
    )

    @Test
    fun `expired unsaved recordings are cleared in the database before their files are deleted`() {
        repository.expired += listOf(recording(3), recording(5))

        assertEquals(RecordingPurgeSummary(deleted = 2, skipped = 0, restored = 0, failed = 0), service.purgeExpired())
        assertEquals(listOf("clear 3", "delete recordings/3/a.m4a", "clear 5", "delete recordings/5/a.m4a"), repository.events)
    }

    @Test
    fun `the cutoff is exactly thirty days before now in UTC`() {
        service.purgeExpired()
        assertEquals(listOf(LocalDateTime.of(2026, 11, 1, 19, 30)), repository.cutoffs.distinct())
    }

    @Test
    fun `a recording saved after it was listed is skipped and its file is kept`() {
        repository.expired += listOf(recording(3), recording(5))
        repository.savedMeanwhile += 3L

        assertEquals(RecordingPurgeSummary(deleted = 1, skipped = 1, restored = 0, failed = 0), service.purgeExpired())
        assertEquals(listOf("clear 5", "delete recordings/5/a.m4a"), repository.events)
    }

    @Test
    fun `a file that cannot be deleted gets its key back for the next run and the others continue`() {
        repository.expired += listOf(recording(3), recording(5))
        failingKeys += "recordings/3/a.m4a"

        assertEquals(RecordingPurgeSummary(deleted = 1, skipped = 0, restored = 1, failed = 0), service.purgeExpired())
        assertEquals(listOf("clear 3", "restore 3", "clear 5", "delete recordings/5/a.m4a"), repository.events)
    }

    @Test
    fun `a key that cannot be restored is counted as failed`() {
        repository.expired += listOf(recording(3))
        failingKeys += "recordings/3/a.m4a"
        repository.gone += 3L

        assertEquals(RecordingPurgeSummary(deleted = 0, skipped = 0, restored = 0, failed = 1), service.purgeExpired())
    }

    @Test
    fun `a database error on one session does not stop the others`() {
        repository.expired += listOf(recording(3), recording(5))
        repository.clearFailing += 3L

        assertEquals(RecordingPurgeSummary(deleted = 1, skipped = 0, restored = 0, failed = 1), service.purgeExpired())
        assertEquals(listOf("clear 5", "delete recordings/5/a.m4a"), repository.events)
    }

    @Test
    fun `recordings are read in batches after the last session id until none are left`() {
        repository.expired += (1L..5L).map(::recording)
        assertEquals(RecordingPurgeSummary(deleted = 5, skipped = 0, restored = 0, failed = 0), service.purgeExpired())
        assertEquals(listOf(0L, 2L, 4L, 5L), repository.readAfter)
    }

    @Test
    fun `logs carry counts and session ids but never storage keys`(output: CapturedOutput) {
        repository.expired += listOf(recording(3), recording(5), recording(7))
        failingKeys += listOf("recordings/3/a.m4a", "recordings/7/a.m4a")
        repository.gone += 7L

        service.runScheduled()

        assertTrue(output.out.contains("deleted=1, skipped=0, restored=1, failed=1"))
        assertTrue(output.out.contains("Recording of session 7 was left without a reference"))
        assertFalse(output.out.contains("recordings/"))
    }

    @Test
    fun `the scheduled run is every day at 04_30 in Seoul`() {
        val scheduled = UnsavedRecordingPurgeService::class.java.getMethod("runScheduled").getAnnotation(Scheduled::class.java)
        val zone = ZoneId.of(scheduled.zone)
        val next = CronExpression.parse(scheduled.cron).next(Instant.parse("2026-11-21T03:00:00Z").atZone(zone))
        assertEquals(Instant.parse("2026-11-21T19:30:00Z"), next?.toInstant())
    }

    private fun recording(sessionId: Long) = ExpiredRecording(sessionId, "recordings/$sessionId/a.m4a")
}

internal class MemoryUnsavedRecordingRepository : UnsavedRecordingRepository {
    val expired = mutableListOf<ExpiredRecording>()
    val savedMeanwhile = mutableSetOf<Long>()
    val clearFailing = mutableSetOf<Long>()
    val gone = mutableSetOf<Long>()
    val events = mutableListOf<String>()
    val cutoffs = mutableListOf<LocalDateTime>()
    val readAfter = mutableListOf<Long>()

    override fun findExpired(cutoff: LocalDateTime, afterSessionId: Long, limit: Int): List<ExpiredRecording> {
        cutoffs += cutoff
        readAfter += afterSessionId
        return expired.filter { it.sessionId > afterSessionId }.sortedBy { it.sessionId }.take(limit)
    }

    override fun clear(recording: ExpiredRecording, cutoff: LocalDateTime): Boolean {
        cutoffs += cutoff
        if (recording.sessionId in clearFailing) throw IllegalStateException("lock wait timeout")
        if (recording.sessionId in savedMeanwhile) return false
        events += "clear ${recording.sessionId}"
        return true
    }

    override fun restore(recording: ExpiredRecording): Boolean {
        if (recording.sessionId in gone) return false
        events += "restore ${recording.sessionId}"
        return true
    }
}
