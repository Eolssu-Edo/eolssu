package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression

class WithdrawalPurgeServiceTests {
    private val now = Instant.parse("2026-11-21T19:00:00Z")
    private val repository = MemoryWithdrawalPurgeRepository()
    private val failingKeys = mutableSetOf<String>()
    private val service = WithdrawalPurgeService(
        repository,
        { key ->
            if (key in failingKeys) throw IllegalStateException("storage down")
            repository.events += "delete $key"
        },
        Clock.fixed(now, ZoneOffset.UTC),
        batchSize = 2,
    )

    @Test
    fun `due members are purged after their recordings are deleted`() {
        repository.due += listOf(3L, 5L)
        repository.keys[3] = listOf("recordings/1/a.m4a", "recordings/2/b.m4a")

        assertEquals(PurgeSummary(purged = 2, deferred = 0, skipped = 0, failed = 0), service.purgeDue())
        assertEquals(listOf("delete recordings/1/a.m4a", "delete recordings/2/b.m4a", "purge 3", "purge 5"), repository.events)
        assertEquals(listOf(now, now), repository.purgedAt)
    }

    @Test
    fun `a member whose recording cannot be deleted is left for the next day and the others continue`() {
        repository.due += listOf(3L, 5L)
        repository.keys[3] = listOf("recordings/1/a.m4a")
        failingKeys += "recordings/1/a.m4a"

        assertEquals(PurgeSummary(purged = 1, deferred = 1, skipped = 0, failed = 0), service.purgeDue())
        assertEquals(listOf("purge 5"), repository.events)
    }

    @Test
    fun `members are read in batches until none are left`() {
        repository.due += (1L..5L)
        assertEquals(PurgeSummary(purged = 5, deferred = 0, skipped = 0, failed = 0), service.purgeDue())
        assertEquals(listOf(0L, 2L, 4L, 5L), repository.readAfter)
    }

    @Test
    fun `a member that cancelled in the meantime is skipped`() {
        repository.due += listOf(3L)
        repository.cancelled += 3L
        assertEquals(PurgeSummary(purged = 0, deferred = 0, skipped = 1, failed = 0), service.purgeDue())
    }

    @Test
    fun `a member whose database purge fails is counted and the next members are still purged`() {
        repository.due += listOf(3L, 5L)
        repository.failing += 3L
        assertEquals(PurgeSummary(purged = 1, deferred = 0, skipped = 0, failed = 1), service.purgeDue())
        assertEquals(listOf("purge 5"), repository.events)
    }

    @Test
    fun `the scheduled run is every day at 04_00 in Seoul`() {
        val scheduled = WithdrawalPurgeService::class.java.getMethod("runScheduled").getAnnotation(Scheduled::class.java)
        val zone = ZoneId.of(scheduled.zone)
        val next = CronExpression.parse(scheduled.cron).next(Instant.parse("2026-11-21T03:00:00Z").atZone(zone))
        assertEquals(Instant.parse("2026-11-21T19:00:00Z"), next?.toInstant())
    }
}

internal class MemoryWithdrawalPurgeRepository : WithdrawalPurgeRepository {
    val due = mutableListOf<Long>()
    val keys = mutableMapOf<Long, List<String>>()
    val cancelled = mutableSetOf<Long>()
    val failing = mutableSetOf<Long>()
    val events = mutableListOf<String>()
    val purgedAt = mutableListOf<Instant>()
    val readAfter = mutableListOf<Long>()

    override fun dueMembers(now: Instant, afterUserId: Long, limit: Int): List<Long> {
        readAfter += afterUserId
        return due.filter { it > afterUserId }.sorted().take(limit)
    }
    override fun recordingKeys(userId: Long) = keys[userId].orEmpty()
    override fun purge(userId: Long, now: Instant): Boolean {
        if (userId in failing) throw IllegalStateException("lock wait timeout")
        if (userId in cancelled) return false
        events += "purge $userId"
        purgedAt += now
        return true
    }
}
