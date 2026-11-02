package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.UnusedPracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.DeletableRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DeletePracticeRecordTests {
    // 저장소 삭제와 DB 갱신의 순서를 한 줄로 남긴다.
    private val events = mutableListOf<String>()
    private var storageFails = false
    private var deletedMeanwhile = false
    private val records = object : UnusedPracticeRecords() {
        override fun findDeletable(sessionId: Long, userId: Long) = when {
            userId != 1L -> null
            sessionId == 501L -> DeletableRecord("recordings/501/a.m4a")
            sessionId == 502L -> DeletableRecord(null)
            else -> null
        }

        override fun markDeleted(sessionId: Long, userId: Long, deletedAt: LocalDateTime): Boolean {
            events += "mark $sessionId $deletedAt"
            return !deletedMeanwhile
        }
    }
    private val delete = DeletePracticeRecord(
        records,
        { key ->
            if (storageFails) throw IllegalStateException("storage down")
            events += "delete $key"
        },
        Clock.fixed(Instant.parse("2026-11-02T04:00:00.700Z"), ZoneOffset.UTC),
    )

    @Test
    fun `deletes the recording before marking the record deleted`() {
        assertEquals(DeleteRecordOutcome.Deleted(recordingDeleted = true, deletedAt = Instant.parse("2026-11-02T04:00:00Z")), delete.execute(1, 501))
        assertEquals(listOf("delete recordings/501/a.m4a", "mark 501 2026-11-02T04:00"), events)
    }

    @Test
    fun `deletes a record without a recording without calling the storage`() {
        assertEquals(DeleteRecordOutcome.Deleted(recordingDeleted = false, deletedAt = Instant.parse("2026-11-02T04:00:00Z")), delete.execute(1, 502))
        assertEquals(listOf("mark 502 2026-11-02T04:00"), events)
    }

    @Test
    fun `touches nothing for sessions that are not the member's records`() {
        assertEquals(DeleteRecordOutcome.NotFound, delete.execute(2, 501))
        assertEquals(DeleteRecordOutcome.NotFound, delete.execute(1, 999))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `keeps the record when the recording cannot be deleted`() {
        storageFails = true
        assertEquals(DeleteRecordOutcome.Unavailable, delete.execute(1, 501))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `answers not found when another request deleted the record first`() {
        deletedMeanwhile = true
        assertEquals(DeleteRecordOutcome.NotFound, delete.execute(1, 501))
        assertEquals(listOf("delete recordings/501/a.m4a", "mark 501 2026-11-02T04:00"), events)
    }
}
