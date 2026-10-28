package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.FullSongPlay
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.domain.SongBest
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
    fun `compares a whole month with the previous month and other ranges with the same number of days`() {
        assertEquals(DateRange(d("2026-10-19"), d("2026-10-25")), week.previous())
        assertEquals(DateRange(d("2027-02-01"), d("2027-02-28")), DateRange(d("2027-03-01"), d("2027-03-31")).previous())
        assertEquals(DateRange(d("2028-02-01"), d("2028-02-29")), DateRange(d("2028-03-01"), d("2028-03-31")).previous()) // 윤년
        assertEquals(DateRange(d("2026-12-01"), d("2026-12-31")), DateRange(d("2027-01-01"), d("2027-01-31")).previous()) // 해 넘김
        // 한 달 전체가 아니면 직전 같은 일수
        assertEquals(DateRange(d("2027-01-31"), d("2027-03-01")), DateRange(d("2027-03-02"), d("2027-03-31")).previous())
        assertEquals(DateRange(d("2027-01-30"), d("2027-02-28")), DateRange(d("2027-03-01"), d("2027-03-30")).previous())
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

    @Test
    fun `passes the best score of each song through`() {
        repository.best += SongBest(2, "도라지", 91)
        repository.best += SongBest(1, "아리랑", 85)

        assertEquals(listOf(SongBest(2, "도라지", 91), SongBest(1, "아리랑", 85)), stats.summary(USER, week).bestBySong)
    }

    @Test
    fun `compares the first and latest play under the latest play's conditions`() {
        val latest = play(sessionId = 9, pitch = 88, timing = 79, duration = 85)
        repository.plays += play(sessionId = 3, bpm = 120, pitch = 10, timing = 10, duration = 10) // 다른 BPM
        repository.plays += play(sessionId = 4, pitch = 70, timing = 68, duration = 71)
        repository.plays += play(sessionId = 6, pitch = 75, timing = 75, duration = 75)
        repository.plays += latest

        assertEquals(
            MetricGrowth(1, "아리랑", 90, ScoreChange(70, 88), ScoreChange(68, 79), ScoreChange(71, 85)),
            stats.summary(USER, week).metricGrowth,
        )
    }

    @Test
    fun `leaves the scores empty with fewer than two comparable plays`() {
        assertEquals(MetricGrowth.EMPTY, stats.summary(USER, week).metricGrowth)

        repository.plays += play(sessionId = 3, bpm = 120)
        repository.plays += play(sessionId = 9)
        assertEquals(
            MetricGrowth(1, "아리랑", 90, ScoreChange.NONE, ScoreChange.NONE, ScoreChange.NONE),
            stats.summary(USER, week).metricGrowth,
        )
    }

    private fun play(sessionId: Long, bpm: Int = 90, pitch: Int = 80, timing: Int = 80, duration: Int = 80) =
        FullSongPlay(sessionId, 1, "아리랑", 1, "pyin-1", bpm, "DOTTED_QUARTER", pitch, timing, duration)

    private fun d(text: String) = LocalDate.parse(text)

    private class InMemoryPracticeStatsRepository : PracticeStatsRepository {
        val seconds = mutableMapOf<LocalDate, Long>()
        val sessions = mutableMapOf<LocalDate, SessionTotals>()
        val best = mutableListOf<SongBest>()

        /** 오래된 것부터. */
        val plays = mutableListOf<FullSongPlay>()

        override fun dailyPracticeSeconds(userId: Long, range: DateRange) =
            seconds.filterKeys { it in range && userId == USER }

        override fun dailyDoneSessions(userId: Long, range: DateRange) =
            sessions.filterKeys { it in range && userId == USER }

        override fun practiceDaysUpTo(userId: Long, lastDay: LocalDate, minSeconds: Long) =
            seconds.filter { (day, total) -> day <= lastDay && total >= minSeconds }.keys.sortedDescending()

        override fun bestFullSongScores(userId: Long) = best

        override fun latestFullSongPlay(userId: Long) = plays.lastOrNull()

        override fun firstFullSongPlayLike(userId: Long, play: FullSongPlay) = plays.firstOrNull {
            it.songId == play.songId && it.scoreVersion == play.scoreVersion && it.modelVersion == play.modelVersion &&
                it.bpm == play.bpm && it.bpmUnit == play.bpmUnit
        }
    }

    private companion object {
        const val USER = 1L
    }
}
