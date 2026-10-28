package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GetGrowthStatsTests {
    private val repository = InMemoryPracticeStatsRepository()

    // 2026-10-28(수) 00:30 KST. UTC로는 아직 27일이라 KST 날짜 계산을 함께 확인한다.
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneOffset.UTC)
    private val stats = GetGrowthStats(repository, clock)
    private val week = DateRange(d("2026-10-26"), d("2026-11-01"))

    @Test
    fun `compares with the same number of days right before the range`() {
        assertEquals(DateRange(d("2026-10-19"), d("2026-10-25")), week.previous())
        // 10월(31일)의 지난 기간은 9월이 아니라 직전 31일이다.
        assertEquals(DateRange(d("2026-08-31"), d("2026-09-30")), DateRange(d("2026-10-01"), d("2026-10-31")).previous())
        assertEquals(DateRange(d("2026-10-27"), d("2026-10-27")), DateRange(d("2026-10-28"), d("2026-10-28")).previous())
    }

    @Test
    fun `sums practice time in the range and in the previous period`() {
        repository.seconds[d("2026-10-26")] = 600
        repository.seconds[d("2026-11-01")] = 1200
        repository.seconds[d("2026-10-19")] = 300 // 지난 기간 첫날
        repository.seconds[d("2026-10-18")] = 7777 // 지난 기간보다 앞
        repository.seconds[d("2026-11-02")] = 9999 // 범위 뒤

        val summary = stats.summary(USER, week)

        assertEquals(1800, summary.totals.practiceSeconds)
        assertEquals(300, summary.previous.practiceSeconds)
    }

    @Test
    fun `counts done sessions and rounds the average score half up`() {
        repository.sessions[d("2026-10-26")] = SessionTotals(doneCount = 1, scoredCount = 1, scoreSum = 85)
        repository.sessions[d("2026-10-27")] = SessionTotals(doneCount = 2, scoredCount = 1, scoreSum = 86) // 1건은 평가 없음
        repository.sessions[d("2026-10-19")] = SessionTotals(doneCount = 3, scoredCount = 3, scoreSum = 211) // 70.33 → 70

        val summary = stats.summary(USER, week)

        assertEquals(PeriodTotals(0, 3, 86), summary.totals) // 85.5 → 86
        assertEquals(PeriodTotals(0, 3, 70), summary.previous)
    }

    @Test
    fun `leaves the average empty when a period has no scored session`() {
        repository.sessions[d("2026-10-26")] = SessionTotals(doneCount = 1, scoredCount = 0, scoreSum = 0)

        val summary = stats.summary(USER, week)

        assertEquals(1, summary.totals.practiceCount)
        assertNull(summary.totals.averageTotalScore)
        assertEquals(PeriodTotals(0, 0, null), summary.previous)
    }

    @Test
    fun `counts practice days with at least one minute in the range`() {
        repository.seconds[d("2026-10-26")] = 60
        repository.seconds[d("2026-10-27")] = 59
        repository.seconds[d("2026-10-31")] = 3600
        repository.seconds[d("2026-10-25")] = 600 // 범위 밖

        assertEquals(2, stats.summary(USER, week).practiceDays)
    }

    @Test
    fun `counts the streak from today or yesterday regardless of the requested range`() {
        listOf("2026-10-25", "2026-10-26", "2026-10-27").forEach { repository.seconds[d(it)] = 60 }
        repository.seconds[d("2026-10-24")] = 59
        repository.seconds[d("2026-10-23")] = 600
        val lastMonth = DateRange(d("2026-09-01"), d("2026-09-30"))
        assertEquals(3, stats.summary(USER, lastMonth).currentStreakDays) // 오늘(28일)은 아직, 어제부터

        repository.seconds[d("2026-10-28")] = 120
        assertEquals(4, stats.summary(USER, lastMonth).currentStreakDays)

        repository.seconds.remove(d("2026-10-28"))
        repository.seconds.remove(d("2026-10-27"))
        assertEquals(0, stats.summary(USER, lastMonth).currentStreakDays)
    }

    @Test
    fun `fills every day of the calendar including days without practice`() {
        repository.seconds[d("2026-10-27")] = 96
        repository.sessions[d("2026-10-27")] = SessionTotals(doneCount = 2, scoredCount = 2, scoreSum = 150)
        repository.sessions[d("2026-10-29")] = SessionTotals(doneCount = 1, scoredCount = 0, scoreSum = 0)

        val days = stats.calendar(USER, DateRange(d("2026-10-26"), d("2026-10-29")))

        assertEquals(
            listOf(
                DailyPractice(d("2026-10-26"), 0, 0),
                DailyPractice(d("2026-10-27"), 96, 2),
                DailyPractice(d("2026-10-28"), 0, 0),
                DailyPractice(d("2026-10-29"), 0, 1),
            ),
            days,
        )
    }

    private fun d(text: String) = LocalDate.parse(text)

    private class InMemoryPracticeStatsRepository : PracticeStatsRepository {
        val seconds = mutableMapOf<LocalDate, Long>()
        val sessions = mutableMapOf<LocalDate, SessionTotals>()

        override fun dailyPracticeSeconds(userId: Long, range: DateRange) =
            seconds.filterKeys { it in range && userId == USER }

        override fun dailyDoneSessions(userId: Long, range: DateRange) =
            sessions.filterKeys { it in range && userId == USER }

        override fun practiceDaysUpTo(userId: Long, lastDay: LocalDate, minSeconds: Long) =
            seconds.filter { (day, total) -> day <= lastDay && total >= minSeconds }.keys.sortedDescending()
    }

    private companion object {
        const val USER = 1L
    }
}
