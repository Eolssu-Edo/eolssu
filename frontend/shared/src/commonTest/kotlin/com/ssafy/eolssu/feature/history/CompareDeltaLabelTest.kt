package com.ssafy.eolssu.feature.history

import kotlin.test.Test
import kotlin.test.assertEquals

class CompareDeltaLabelTest {
    @Test
    fun metricDeltaLabelShowsSignOrZero() {
        assertEquals("+12", metricDeltaLabel(12))
        assertEquals("−4", metricDeltaLabel(-4))
        assertEquals("0", metricDeltaLabel(0))
    }
}
