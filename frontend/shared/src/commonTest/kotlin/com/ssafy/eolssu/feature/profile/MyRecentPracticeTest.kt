package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyRecentPracticeTest {
    // 2026-11-09은 월요일이다.
    private val today = KoreanDateTime(2026, 11, 11, 12, 0)

    private fun record(id: String, at: KoreanDateTime, score: Int) = PracticeRecord(
        id, "arirang", "아리랑", section = null, repeats = null, recordedAt = at,
        recordedSeconds = 30, bpm = 60, score = score, metrics = emptyList(), memo = "", errors = emptyList(),
    )

    @Test
    fun latestRecordAndThisWeekSummaryComeFromRecords() {
        val records = listOf(
            record("c", KoreanDateTime(2026, 11, 10, 20, 0), 71),
            record("b", KoreanDateTime(2026, 11, 9, 9, 0), 84),
            record("a", KoreanDateTime(2026, 11, 8, 22, 0), 95),
        )
        val recent = MyRecentPractice.from(records, today)
        assertEquals("c", recent.latest?.id)
        assertEquals(2, recent.weekCount, "일요일(11/8) 기록은 지난주다")
        assertEquals(84, recent.weekBest, "지난주 95점은 이번 주 최고에 넣지 않는다")
    }

    @Test
    fun latestUsesRecordedTimeEvenIfListIsOutOfOrder() {
        val records = listOf(
            record("old", KoreanDateTime(2026, 11, 1, 10, 0), 60),
            record("new", KoreanDateTime(2026, 11, 10, 10, 0), 70),
        )
        assertEquals("new", MyRecentPractice.from(records, today).latest?.id)
    }

    @Test
    fun noRecordsThisWeekLeavesBestEmpty() {
        val recent = MyRecentPractice.from(listOf(record("a", KoreanDateTime(2026, 11, 2, 10, 0), 90)), today)
        assertEquals("a", recent.latest?.id)
        assertEquals(0, recent.weekCount)
        assertNull(recent.weekBest)
    }

    @Test
    fun noRecordsAtAll() {
        val recent = MyRecentPractice.from(emptyList(), today)
        assertNull(recent.latest)
        assertEquals(0, recent.weekCount)
        assertNull(recent.weekBest)
    }

    @Test
    fun statusShowsKeptRecordsWhileLoadingOrFailed() {
        assertEquals(MyRecentPracticeStatus.Shown, myRecentPracticeStatus(available = true, hasRecords = true, loading = true, failed = false))
        assertEquals(MyRecentPracticeStatus.Shown, myRecentPracticeStatus(available = true, hasRecords = true, loading = false, failed = true))
        assertEquals(MyRecentPracticeStatus.Loading, myRecentPracticeStatus(available = true, hasRecords = false, loading = true, failed = false))
        assertEquals(MyRecentPracticeStatus.Failed, myRecentPracticeStatus(available = true, hasRecords = false, loading = false, failed = true))
        assertEquals(MyRecentPracticeStatus.Empty, myRecentPracticeStatus(available = true, hasRecords = false, loading = false, failed = false))
        assertEquals(MyRecentPracticeStatus.Unavailable, myRecentPracticeStatus(available = false, hasRecords = true, loading = false, failed = false))
    }
}
