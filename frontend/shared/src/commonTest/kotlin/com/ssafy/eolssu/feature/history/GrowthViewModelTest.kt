package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.DailyPractice
import com.ssafy.eolssu.data.practice.GrowthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** 연습 기록지 ViewModel: GRO-02 날짜별 1년 단위 요청, 첫 기록 찾기, 기준 날짜 · 연습량 달력 고르기. */
@OptIn(ExperimentalCoroutinesApi::class)
class GrowthViewModelTest {

    private fun day(month: Int, day: Int, year: Int = 2026) = KoreanDateTime(year, month, day, 0, 0).epochDay

    // 2026-11-12 12:00 KST
    private val now = (day(11, 12) * 86_400_000L) + 3 * 3_600_000L
    private val today = day(11, 12)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstRequestIsOneYearEndingToday() {
        val repo = FakeGrowth { _, _ -> emptyList() }
        val vm = GrowthViewModel(repo, now = { now })

        assertEquals(listOf(today - 365 to today), repo.asked)
        val history = assertIs<GrowthLoad.Loaded>(vm.uiState.value.load).history
        assertFalse(history.searchingOlder)
        assertEquals(today - 365, history.loadedFrom)
    }

    @Test
    fun keepsAskingOlderYearsWhileRecordsAppear() {
        val records = mapOf(day(10, 1) to 600L, day(3, 1, 2025) to 120L)
        val repo = FakeGrowth { from, to -> records.filterKeys { it in from..to }.map { (d, s) -> DailyPractice(d, s, 1) } }
        val vm = GrowthViewModel(repo, now = { now })

        val first = today - 365
        val second = first - 366
        // 1년째 기록 있음 → 2년째 기록 있음 → 3년째 없음에서 멈춘다.
        assertEquals(listOf(first to today, second to first - 1, second - 366 to second - 1), repo.asked)
        val history = assertIs<GrowthLoad.Loaded>(vm.uiState.value.load).history
        assertEquals(day(3, 1, 2025), history.firstDay)
        assertFalse(history.searchingOlder)
        assertEquals(day(3, 1, 2025), vm.uiState.value.earliestDay)
    }

    @Test
    fun failedFirstYearCanBeRetried() {
        var fail = true
        val repo = FakeGrowth { _, _ -> if (fail) null else emptyList() }
        val vm = GrowthViewModel(repo, now = { now })
        assertEquals(GrowthLoad.Failed, vm.uiState.value.load)

        fail = false
        vm.retry()
        assertIs<GrowthLoad.Loaded>(vm.uiState.value.load)
        assertEquals(2, repo.asked.size)
    }

    @Test
    fun pickingAnchorMovesGraphAndHeatCalendar() {
        val repo = FakeGrowth { _, _ -> listOf(DailyPractice(day(9, 10), 600, 1)) }
        val vm = GrowthViewModel(repo, now = { now })

        vm.pickAnchor(day(10, 3))
        with(vm.uiState.value) {
            assertEquals(day(10, 3), anchorDay)
            assertEquals(day(10, 1), heatMonth)
            assertEquals(day(10, 3), heatDay)
            assertEquals(day(10, 4), chart!!.toDay)
        }

        // 첫 기록 전 · 오늘 뒤는 고르지 않는다.
        vm.pickAnchor(day(9, 9))
        vm.pickAnchor(day(11, 13))
        assertEquals(day(10, 3), vm.uiState.value.anchorDay)
    }

    @Test
    fun heatMonthStaysBetweenFirstRecordAndThisMonth() {
        val repo = FakeGrowth { _, _ -> listOf(DailyPractice(day(9, 10), 600, 1)) }
        val vm = GrowthViewModel(repo, now = { now })

        vm.moveHeatMonth(1)
        assertEquals(day(11, 1), vm.uiState.value.heatMonth)
        vm.moveHeatMonth(-1)
        vm.moveHeatMonth(-1)
        vm.moveHeatMonth(-1)
        assertEquals(day(9, 1), vm.uiState.value.heatMonth)

        // 달을 넘겨도 고른 날은 그대로다. 오늘 뒤 날은 고르지 않는다.
        assertEquals(today, vm.uiState.value.heatDay)
        vm.pickHeatDay(day(9, 10))
        vm.pickHeatDay(day(11, 20))
        assertEquals(day(9, 10), vm.uiState.value.heatDay)
    }

    @Test
    fun rangeMetricAndZoomChangeWithoutRequests() {
        val repo = FakeGrowth { _, _ -> emptyList() }
        val vm = GrowthViewModel(repo, now = { now })

        vm.changeRange(GrowthRange.Month)
        vm.changeMetric(GrowthMetric.Count)
        vm.toggleDense()
        with(vm.uiState.value) {
            assertEquals(GrowthRange.Month, range)
            assertEquals(GrowthMetric.Count, metric)
            assertEquals(6, chart!!.bars.size)
        }
        assertEquals(1, repo.asked.size)
    }

    private class FakeGrowth(private val answer: (Long, Long) -> List<DailyPractice>?) : GrowthRepository {
        val asked = mutableListOf<Pair<Long, Long>>()

        override suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>? {
            asked += fromDay to toDay
            return answer(fromDay, toDay)
        }
    }
}
