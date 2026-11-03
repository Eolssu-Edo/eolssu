package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.GrowthSummary
import com.ssafy.eolssu.data.practice.PeriodTotals
import com.ssafy.eolssu.feature.history.component.perWeekLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 연습 기록지 보드(GROWTH-01)의 월 보기 ‘주 N회’와 ‘점수 변화 · 최근 5회’ 그래프 배치. */
class GrowthBoardTest {

    private val thursday = KoreanDateTime(2026, 10, 29, 21, 0)

    private fun monthStats(count: Long): GrowthStats = GrowthStats.of(
        GrowthPeriod.of(GrowthRange.Month, thursday),
        GrowthSummary(PeriodTotals(0, count, null), PeriodTotals(0, 0, null), 0, 0, emptyList(), null),
        emptyList(),
    )

    @Test
    fun monthPerWeekDividesCountByWeekBars() {
        // 2026년 10월은 막대(주)가 5개다.
        val stats = monthStats(14)
        assertEquals(5, stats.bars.size)
        assertEquals(2.8, stats.perWeek, 1e-9)
        assertEquals("2.8", perWeekLabel(stats.perWeek))
    }

    @Test
    fun perWeekLabelDropsTrailingZeroAndRounds() {
        assertEquals("3.5", perWeekLabel(3.5))
        assertEquals("3", perWeekLabel(3.0))
        assertEquals("0", perWeekLabel(0.0))
        assertEquals("2.7", perWeekLabel(2.666))
    }

    @Test
    fun boardSampleScoresLandOnBoardCoordinates() {
        // 보드 68 · 71 · 76 · 79 · 84 → 점 (14,72) (84,63) (154,48) (224,39) (294,24).
        val dots = scoreTrendDots(listOf(68, 71, 76, 79, 84))
        assertEquals(listOf(14f, 84f, 154f, 224f, 294f), dots.map { it.x })
        assertEquals(listOf(72f, 63f, 48f, 39f, 24f), dots.map { it.y })
    }

    @Test
    fun singleOrEvenScoresSitInTheMiddle() {
        assertEquals(listOf(TrendDot(TrendWidth / 2, 48f)), scoreTrendDots(listOf(80)))
        assertEquals(listOf(48f, 48f), scoreTrendDots(listOf(70, 70)).map { it.y })
        assertEquals(emptyList(), scoreTrendDots(emptyList()))
    }

    @Test
    fun scoreTrendIsNotConnectedYet() {
        // 점수 추이 API 연결(S15P31E102-372) 전에는 null이라 카드가 준비 중 안내를 보인다.
        assertNull(GrowthUiState(today = thursday).scoreTrend)
    }
}
