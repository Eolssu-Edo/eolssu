package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultSummaryTest {

    private fun result(total: Int, pitch: Int, rhythm: Int, length: Int) = EvaluationResult(
        id = "e", songId = "s", total = total, feedback = "",
        metrics = listOf(
            MetricScore(EvaluationMetric.Pitch, pitch),
            MetricScore(EvaluationMetric.Rhythm, rhythm),
            MetricScore(EvaluationMetric.Length, length),
        ),
        errors = emptyList(), bpm = 60, recordedSeconds = 48, recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), sample = false,
    )

    @Test
    fun boardSampleMarksRhythmWeakAndGood() {
        // 보드 RESULT-02: 84점 · 음정 88 · 박자 80 · 음 길이 84 → 박자에 ‘더 연습’, ‘좋아요! 박자를…’.
        val summary = result(84, 88, 80, 84).summary()
        assertEquals(setOf(EvaluationMetric.Rhythm), summary.weak)
        assertEquals(EvaluationMetric.Rhythm, summary.focus)
        assertTrue(summary.good)
    }

    @Test
    fun tiedLowestMetricsAreAllWeakAndFocusFollowsMetricOrder() {
        val summary = result(70, 90, 60, 60).summary()
        assertEquals(setOf(EvaluationMetric.Rhythm, EvaluationMetric.Length), summary.weak)
        assertEquals(EvaluationMetric.Rhythm, summary.focus)
        assertFalse(summary.good)
    }

    @Test
    fun evenScoresHaveNoWeakMetric() {
        val summary = result(80, 80, 80, 80).summary()
        assertTrue(summary.weak.isEmpty())
        assertNull(summary.focus)
        assertTrue(summary.good)
    }

    @Test
    fun goodThresholdIsEighty() {
        assertFalse(result(79, 90, 70, 80).summary().good)
        assertTrue(result(80, 90, 70, 80).summary().good)
    }

    @Test
    fun noMetricsMeansNoWeakMetric() {
        val summary = result(50, 0, 0, 0).copy(metrics = emptyList()).summary()
        assertTrue(summary.weak.isEmpty())
        assertNull(summary.focus)
    }
}
