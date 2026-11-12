package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.DailyPractice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 기록지(GROWTH-01 A안 2차)의 기간 그래프 · 연습량 달력 계산. 날짜별 값은 GRO-02 응답 모양이다. */
class GrowthStateTest {

    private fun day(month: Int, day: Int, year: Int = 2026) = KoreanDateTime(year, month, day, 0, 0).epochDay

    private val today = KoreanDateTime(2026, 11, 12, 21, 0) // 목요일

    private fun history(vararg days: DailyPractice) = PracticeHistory.of(days.toList(), from = day(11, 12, 2025))

    @Test
    fun weekAndMonthBoundariesFollowMondayAndCalendarMonth() {
        assertEquals(day(11, 9), GrowthDays.weekStart(day(11, 12)))
        assertEquals(day(11, 9), GrowthDays.weekStart(day(11, 15)))
        assertEquals(day(11, 1), GrowthDays.monthStart(day(11, 12)))
        assertEquals(day(12, 1, 2025), GrowthDays.addMonths(day(11, 1), -11))
        assertEquals(day(1, 1, 2027), GrowthDays.addMonths(day(12, 1), 1))
        assertEquals(day(2, 28), GrowthDays.monthEnd(day(2, 1)))
        // 2026년 11월 1일은 일요일이라 10/26 주부터 11/30 주까지 6줄이다.
        assertEquals(listOf(day(10, 26), day(11, 2), day(11, 9), day(11, 16), day(11, 23), day(11, 30)), GrowthDays.monthWeeks(day(11, 1)))
        // 2027년 2월은 월요일에 시작하고 일요일에 끝나 4줄이다.
        assertEquals(4, GrowthDays.monthWeeks(day(2, 1, 2027)).size)
    }

    @Test
    fun weekChartEndsWithAnchorWeekAndLabelsMonthChanges() {
        val chart = PeriodChart.of(history(), GrowthRange.Week, dense = true, anchorDay = today.epochDay)

        assertEquals(8, chart.bars.size)
        assertEquals(day(9, 21), chart.fromDay)
        assertEquals(day(11, 15), chart.toDay)
        assertEquals(listOf("9/21", "28", "10/5", "12", "19", "26", "11/2", "9"), weekBarLabels(chart.bars))

        val zoomed = PeriodChart.of(history(), GrowthRange.Week, dense = false, anchorDay = today.epochDay)
        assertEquals(4, zoomed.bars.size)
        assertEquals(day(10, 19), zoomed.fromDay)
        assertEquals(listOf("10/19", "26", "11/2", "9"), weekBarLabels(zoomed.bars))
    }

    @Test
    fun monthChartCoversTwelveOrSixCalendarMonths() {
        val chart = PeriodChart.of(history(), GrowthRange.Month, dense = true, anchorDay = day(3, 5))
        assertEquals(12, chart.bars.size)
        assertEquals(day(4, 1, 2025), chart.fromDay)
        assertEquals(day(3, 31), chart.toDay)

        val zoomed = PeriodChart.of(history(), GrowthRange.Month, dense = false, anchorDay = day(3, 5))
        assertEquals(6, zoomed.bars.size)
        assertEquals(day(10, 1, 2025), zoomed.fromDay)
    }

    @Test
    fun barsSumDaysInsideEachPeriodAndTotalsFloorOnce() {
        val h = history(
            DailyPractice(day(11, 2), 89, 1), // 11/2 주(월요일)
            DailyPractice(day(11, 8), 89, 2), // 같은 주 일요일
            DailyPractice(day(11, 9), 30, 1), // 11/9 주
            DailyPractice(day(11, 1), 600, 1), // 10/26 주(11월 1일은 일요일)
        )
        val chart = PeriodChart.of(h, GrowthRange.Week, dense = false, anchorDay = day(11, 12))

        assertEquals(listOf(0L, 600L, 178L, 30L), chart.bars.map { it.seconds })
        assertEquals(listOf(0L, 1L, 3L, 1L), chart.bars.map { it.count })
        assertEquals(listOf(0L, 10L, 2L, 0L), chart.bars.map { it.minutes })
        // 합계는 초를 모두 더한 뒤 내림한다(808초 → 13분). 막대 합(12분)과 다를 수 있다.
        assertEquals(13, chart.totalMinutes)
        assertEquals(5, chart.totalCount)

        val month = PeriodChart.of(h, GrowthRange.Month, dense = false, anchorDay = day(11, 12))
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 808L), month.bars.map { it.seconds })
        assertEquals(day(6, 1), month.fromDay)
    }

    @Test
    fun axisMaxAndHalfFollowSelectedMetric() {
        val h = history(DailyPractice(day(11, 9), 45 * 60, 3), DailyPractice(day(11, 2), 20 * 60, 1))
        val chart = PeriodChart.of(h, GrowthRange.Week, dense = false, anchorDay = day(11, 12))

        assertEquals(45, chart.max(GrowthMetric.Time))
        assertEquals(23, chart.half(GrowthMetric.Time))
        assertEquals(3, chart.max(GrowthMetric.Count))
        assertEquals(2, chart.half(GrowthMetric.Count))

        val empty = PeriodChart.of(history(), GrowthRange.Week, dense = true, anchorDay = day(11, 12))
        assertEquals(1, empty.max(GrowthMetric.Time))
        assertEquals(1, empty.half(GrowthMetric.Time))
    }

    @Test
    fun heatLevelUsesFourMinuteBands() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), listOf(0L, 1, 14, 15, 29, 30, 44, 45, 180).map { heatLevel(it) })
    }

    @Test
    fun historyKeepsOnlyDaysWithRecordsAndFindsFirstDay() {
        val h = history(DailyPractice(day(11, 3), 0, 0), DailyPractice(day(11, 5), 30, 0), DailyPractice(day(11, 1), 0, 1))

        assertEquals(setOf(day(11, 5), day(11, 1)), h.days.keys)
        assertEquals(day(11, 1), h.firstDay)
        assertNull(history().firstDay)

        val merged = h.merged(listOf(DailyPractice(day(3, 1, 2025), 120, 1), DailyPractice(day(3, 2, 2025), 0, 0)), from = day(1, 1, 2025))
        assertEquals(day(3, 1, 2025), merged.firstDay)
        assertEquals(day(1, 1, 2025), merged.loadedFrom)
    }

    @Test
    fun picksAreLimitedFromFirstRecordToToday() {
        val loaded = GrowthUiState(today, load = GrowthLoad.Loaded(history(DailyPractice(day(9, 10), 600, 1))))

        assertTrue(loaded.canPickAnchor(day(9, 10)))
        assertTrue(loaded.canPickAnchor(today.epochDay))
        assertFalse(loaded.canPickAnchor(day(9, 9)))
        assertFalse(loaded.canPickAnchor(day(11, 13)))
        assertTrue(loaded.canShowMonth(day(9, 1)))
        assertFalse(loaded.canShowMonth(day(8, 1)))
        assertFalse(loaded.canShowMonth(day(12, 1)))

        // 기록이 없으면 오늘 · 이번 달만 고른다.
        val empty = GrowthUiState(today, load = GrowthLoad.Loaded(history()))
        assertTrue(empty.canPickAnchor(today.epochDay))
        assertFalse(empty.canPickAnchor(day(11, 11)))
        assertFalse(empty.canShowMonth(day(10, 1)))
    }

    @Test
    fun chartIsOnlyAvailableAfterLoading() {
        assertNull(GrowthUiState(today).chart)
        assertNull(GrowthUiState(today, load = GrowthLoad.Failed).chart)
        assertEquals(day(11, 15), GrowthUiState(today, load = GrowthLoad.Loaded(history())).chart?.toDay)
    }
}
