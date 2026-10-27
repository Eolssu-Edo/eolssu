package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.domain.StatsPeriod
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GetPracticeStatsTests {
    private val repository = InMemoryPracticeStatsRepository()

    // 2026-10-28(수) 00:30 KST. UTC로는 아직 27일이라 KST 날짜 계산을 함께 확인한다.
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneOffset.UTC)
    private val stats = GetPracticeStats(repository, clock)

    @Test
    fun `uses the KST Monday to Sunday week with one bar per day`() {
        val week = stats.stats(USER, StatsPeriod.WEEK)

        assertEquals(DateRange(d("2026-10-26"), d("2026-11-01")), week.range)
        assertEquals((26..31).map { d("2026-10-%02d".format(it)) } + d("2026-11-01"), week.bars.map { it.range.start })
        assertEquals(week.bars.map { it.range.start }, week.bars.map { it.range.endInclusive })
    }

    @Test
    fun `splits a month into Monday to Sunday weeks clipped to the month`() {
        fun barsOf(today: String) = GetPracticeStats(repository, kstNoon(today)).stats(USER, StatsPeriod.MONTH).bars.map { it.range }

        // 2026-10-01은 목요일: 1~4, 5~11, 12~18, 19~25, 26~31
        assertEquals(
            listOf("01" to "04", "05" to "11", "12" to "18", "19" to "25", "26" to "31").map { DateRange(d("2026-10-${it.first}"), d("2026-10-${it.second}")) },
            barsOf("2026-10-28"),
        )
        // 2026-08-01은 토요일이라 6주, 2027-02-01은 월요일이고 28일이라 4주
        assertEquals(6, barsOf("2026-08-15").size)
        assertEquals(DateRange(d("2026-08-31"), d("2026-08-31")), barsOf("2026-08-15").last())
        assertEquals(4, barsOf("2027-02-10").size)
    }

    @Test
    fun `sums practice time per bar and compares with the whole previous period`() {
        repository.seconds[d("2026-10-26")] = 600
        repository.seconds[d("2026-10-28")] = 1200
        repository.seconds[d("2026-10-20")] = 300 // 지난주
        repository.seconds[d("2026-11-02")] = 9999 // 다음 주는 빠진다

        val week = stats.stats(USER, StatsPeriod.WEEK)

        assertEquals(1800, week.practiceSeconds)
        assertEquals(1500, week.practiceSecondsChange)
        assertEquals(listOf(600L, 0, 1200, 0, 0, 0, 0), week.bars.map { it.practiceSeconds })
    }

    @Test
    fun `counts done sessions and rounds the average score half up`() {
        repository.sessions[d("2026-10-26")] = SessionTotals(doneCount = 1, scoredCount = 1, scoreSum = 85)
        repository.sessions[d("2026-10-27")] = SessionTotals(doneCount = 1, scoredCount = 1, scoreSum = 86)
        repository.sessions[d("2026-10-19")] = SessionTotals(doneCount = 2, scoredCount = 2, scoreSum = 141) // 70.5 → 71

        val week = stats.stats(USER, StatsPeriod.WEEK)

        assertEquals(2, week.sessionCount)
        assertEquals(86, week.averageScore) // 85.5 → 86
        assertEquals(15, week.averageScoreChange)
    }

    @Test
    fun `omits the average and its change when a period has no scored session`() {
        repository.sessions[d("2026-10-26")] = SessionTotals(doneCount = 1, scoredCount = 1, scoreSum = 80)
        val withoutPrevious = stats.stats(USER, StatsPeriod.WEEK)
        assertEquals(80, withoutPrevious.averageScore)
        assertNull(withoutPrevious.averageScoreChange)

        repository.sessions.clear()
        repository.sessions[d("2026-10-19")] = SessionTotals(doneCount = 1, scoredCount = 1, scoreSum = 80)
        val withoutCurrent = stats.stats(USER, StatsPeriod.WEEK)
        assertEquals(0, withoutCurrent.sessionCount)
        assertNull(withoutCurrent.averageScore)
        assertNull(withoutCurrent.averageScoreChange)
    }

    @Test
    fun `counts the streak from today or yesterday using days with at least one minute`() {
        listOf("2026-10-25", "2026-10-26", "2026-10-27").forEach { repository.seconds[d(it)] = 60 }
        repository.seconds[d("2026-10-24")] = 59
        repository.seconds[d("2026-10-23")] = 600
        assertEquals(3, stats.stats(USER, StatsPeriod.WEEK).streakDays) // 오늘(28일)은 아직, 어제부터

        repository.seconds[d("2026-10-28")] = 120
        assertEquals(4, stats.stats(USER, StatsPeriod.WEEK).streakDays)

        repository.seconds.remove(d("2026-10-28"))
        repository.seconds.remove(d("2026-10-27"))
        assertEquals(0, stats.stats(USER, StatsPeriod.WEEK).streakDays)
    }

    private fun d(text: String) = LocalDate.parse(text)

    private fun kstNoon(date: String) = Clock.fixed(Instant.parse("${date}T03:00:00Z"), ZoneOffset.UTC)

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
