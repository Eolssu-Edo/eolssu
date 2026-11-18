package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import kotlin.test.Test
import kotlin.test.assertEquals

class CompareDeltaLabelTest {
    @Test
    fun metricDeltaLabelShowsSignOrZero() {
        assertEquals("+12", metricDeltaLabel(12))
        assertEquals("−4", metricDeltaLabel(-4))
        assertEquals("0", metricDeltaLabel(0))
    }

    @Test
    fun metricToneMarksOnlyLoweredMetricsAsDown() {
        // 음 길이 85 → 83처럼 1점이라도 내리면 붉은 막대·칩, 오르면 연두, 같으면 회색이다.
        assertEquals(MetricTone.Down, metricTone(MetricChange(EvaluationMetric.Length, 85, 83).delta))
        assertEquals(MetricTone.Down, metricTone(-1))
        assertEquals(MetricTone.Up, metricTone(MetricChange(EvaluationMetric.Pitch, 74, 85).delta))
        assertEquals(MetricTone.Same, metricTone(0))
    }
}
