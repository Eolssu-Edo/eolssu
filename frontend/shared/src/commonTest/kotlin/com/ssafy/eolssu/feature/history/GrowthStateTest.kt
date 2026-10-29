package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.DailyPractice
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.GrowthSummary
import com.ssafy.eolssu.data.practice.MetricChange
import com.ssafy.eolssu.data.practice.MetricGrowth
import com.ssafy.eolssu.data.practice.PeriodTotals
import com.ssafy.eolssu.data.practice.SongBestScore
import com.ssafy.eolssu.feature.history.component.signed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 연습 기록지(GROWTH-01)의 주·월 기간과 서버 통계 → 화면 값(`docs/specs/practice-time.md` '성장 화면 통계'). */
class GrowthStateTest {

    private fun day(month: Int, day: Int, year: Int = 2026) = KoreanDateTime(year, month, day, 0, 0).epochDay

    private val thursday = KoreanDateTime(2026, 10, 29, 21, 0)

    private fun summary(
        seconds: Long = 0,
        count: Long = 0,
        average: Int? = null,
        previous: PeriodTotals = PeriodTotals(0, 0, null),
        best: List<SongBestScore> = emptyList(),
        growth: MetricGrowth? = null,
    ) = GrowthSummary(PeriodTotals(seconds, count, average), previous, 0, 0, null, null, best, growth)

    @Test
    fun weekIsMondayToSundayContainingToday() {
        val period = GrowthPeriod.of(GrowthRange.Week, thursday)
        assertEquals(day(10, 26), period.fromDay)
        assertEquals(day(11, 1), period.toDay)
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), period.barLabels)
        assertEquals(3, period.todayBar)
        // 10월 1일(목)이 든 9/28 주가 1주차.
        assertEquals(10 to 5, period.month to period.weekOfMonth)
    }

    @Test
    fun weekFollowsKstMidnightNotUtc() {
        val mondayMidnightKst = day(10, 26) * 86_400_000L - 9 * 3_600_000L // 2026-10-25T15:00:00Z
        val sundayNight = KoreanDateTime.fromEpochMillis(mondayMidnightKst - 1)
        val monday = KoreanDateTime.fromEpochMillis(mondayMidnightKst)

        assertEquals(day(10, 19), GrowthPeriod.of(GrowthRange.Week, sundayNight).fromDay)
        assertEquals(6, GrowthPeriod.of(GrowthRange.Week, sundayNight).todayBar)
        assertEquals(day(10, 26), GrowthPeriod.of(GrowthRange.Week, monday).fromDay)
        assertEquals(0, GrowthPeriod.of(GrowthRange.Week, monday).todayBar)
    }

    @Test
    fun octoberHasFiveWeekBarsStartingOnThursdayFirst() {
        val period = GrowthPeriod.of(GrowthRange.Month, thursday)
        assertEquals(day(10, 1)..day(10, 31), period.fromDay..period.toDay)
        assertEquals(
            listOf(day(10, 1)..day(10, 4), day(10, 5)..day(10, 11), day(10, 12)..day(10, 18), day(10, 19)..day(10, 25), day(10, 26)..day(10, 31)),
            period.barDays,
        )
        assertEquals(listOf("1주", "2주", "3주", "4주", "5주"), period.barLabels)
        assertEquals(4, period.todayBar)
    }

    @Test
    fun decemberEndsOnThirtyFirstAndFebruaryCanHaveFourBars() {
        val december = GrowthPeriod.of(GrowthRange.Month, KoreanDateTime(2026, 12, 15, 9, 0))
        assertEquals(day(12, 31), december.toDay)
        // 2027년 2월: 1일이 월요일, 28일이 일요일이라 막대 4개.
        assertEquals(4, GrowthPeriod.of(GrowthRange.Month, KoreanDateTime(2027, 2, 10, 9, 0)).barDays.size)
        assertEquals(10 to 1, GrowthPeriod.of(GrowthRange.Week, KoreanDateTime(2026, 10, 1, 9, 0)).let { it.month to it.weekOfMonth })
    }

    @Test
    fun minutesAreFlooredSeparatelyForTilesAndBars() {
        val period = GrowthPeriod.of(GrowthRange.Week, thursday)
        val days = (0..6).map { DailyPractice(period.fromDay + it, 0, 0) }.toMutableList()
        days[0] = DailyPractice(period.fromDay, 119, 1)
        days[3] = DailyPractice(period.fromDay + 3, 180, 1)

        val stats = GrowthStats.of(period, summary(seconds = 299, count = 2), days)

        assertEquals(4L, stats.practiceMinutes)
        assertEquals(listOf<Long>(1, 0, 0, 3, 0, 0, 0), stats.bars.map { it.minutes })
        assertEquals(3, stats.highlightBar)
    }

    @Test
    fun monthBarsSumDaysInEachWeek() {
        val period = GrowthPeriod.of(GrowthRange.Month, thursday)
        val days = listOf(DailyPractice(day(10, 1), 600, 1), DailyPractice(day(10, 4), 630, 1), DailyPractice(day(10, 5), 60, 1), DailyPractice(day(10, 31), 120, 1))

        val stats = GrowthStats.of(period, summary(seconds = 1410), days)

        assertEquals(listOf<Long>(20, 1, 0, 0, 2), stats.bars.map { it.minutes })
        assertEquals(GrowthRange.Month, stats.range)
    }

    @Test
    fun changesComparePreviousPeriodAndAverageNeedsBothSides() {
        val period = GrowthPeriod.of(GrowthRange.Week, thursday)

        val both = GrowthStats.of(period, summary(seconds = 98 * 60, average = 79, previous = PeriodTotals(75 * 60 + 59, 3, 73)), emptyList())
        assertEquals(23L, both.minutesChange)
        assertEquals(6, both.averageChange)

        val noPrevious = GrowthStats.of(period, summary(seconds = 60, average = 79), emptyList())
        assertEquals(1L, noPrevious.minutesChange)
        assertNull(noPrevious.averageChange)

        assertEquals(listOf("+23", "-5", "±0"), listOf(signed(23), signed(-5), signed(0)))
    }

    @Test
    fun emptyResultHasZeroBarsAndNoAchievements() {
        val period = GrowthPeriod.of(GrowthRange.Month, thursday)
        val stats = GrowthStats.of(period, summary(), (period.fromDay..period.toDay).map { DailyPractice(it, 0, 0) })

        assertEquals(0L, stats.practiceMinutes)
        assertEquals(0L, stats.practiceCount)
        assertNull(stats.averageScore)
        assertEquals(List(5) { 0L }, stats.bars.map { it.minutes })
        assertEquals(emptyList(), stats.bestBySong)
        assertNull(stats.metricGrowth)
        assertNull(stats.completedMissions)
    }

    @Test
    fun bestBySongKeepsServerOrderAndSameConditionGrowthShowsBasis() {
        val best = listOf(SongBestScore("2", "도라지타령", 81), SongBestScore("1", "아리랑", 79))
        val growth = MetricGrowth("2", "도라지타령", 60, listOf(MetricChange(EvaluationMetric.Pitch, 70, 82)))

        val stats = GrowthStats.of(GrowthPeriod.of(GrowthRange.Week, thursday), summary(best = best, growth = growth), emptyList())

        assertEquals(best, stats.bestBySong)
        assertEquals(growth, stats.metricGrowth)
        // 같은 조건 연주가 2회 미만이라 비교할 항목이 없으면 카드를 보이지 않는다.
        val single = GrowthStats.of(GrowthPeriod.of(GrowthRange.Week, thursday), summary(growth = growth.copy(changes = emptyList())), emptyList())
        assertNull(single.metricGrowth)
    }

    @Test
    fun loadAsksSamePeriodForSummaryAndCalendar() = runTest {
        val repository = FakeGrowthRepository(summary(seconds = 120), emptyList())
        val period = GrowthPeriod.of(GrowthRange.Month, thursday)

        val load = loadGrowth(repository, period)

        assertIs<GrowthLoad.Loaded>(load)
        assertEquals(listOf(period.fromDay to period.toDay, period.fromDay to period.toDay), repository.asked)
    }

    @Test
    fun loadFailsWhenEitherRequestFails() = runTest {
        val period = GrowthPeriod.of(GrowthRange.Week, thursday)
        assertEquals(GrowthLoad.Failed, loadGrowth(FakeGrowthRepository(null, emptyList()), period))
        assertEquals(GrowthLoad.Failed, loadGrowth(FakeGrowthRepository(summary(), null), period))
    }

    @Test
    fun stateKeepsEachRangeLoadSeparately() {
        val stats = GrowthStats.of(GrowthPeriod.of(GrowthRange.Week, thursday), summary(), emptyList())
        val state = GrowthUiState(today = thursday, loads = mapOf(GrowthRange.Week to GrowthLoad.Loaded(stats)))

        assertIs<GrowthLoad.Loaded>(state.load)
        assertEquals(GrowthLoad.Loading, state.copy(range = GrowthRange.Month).load)
        assertEquals(day(10, 1), state.copy(range = GrowthRange.Month).period.fromDay)
    }

    private class FakeGrowthRepository(private val summary: GrowthSummary?, private val days: List<DailyPractice>?) : GrowthRepository {
        val asked = mutableListOf<Pair<Long, Long>>()

        override suspend fun summary(fromDay: Long, toDay: Long): GrowthSummary? = summary.also { asked += fromDay to toDay }

        override suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>? = days.also { asked += fromDay to toDay }
    }
}
