package com.ssafy.eolssu.feature.history

import kotlin.test.Test
import kotlin.test.assertEquals

class CompareDeltaLabelTest {
    @Test
    fun deltaLabelShowsSignAndExclamationOnlyWhenScoreRose() {
        assertEquals("+40점!", compareDeltaLabel(40))
        assertEquals("+1점!", compareDeltaLabel(1))
        assertEquals("−10점", compareDeltaLabel(-10))
        assertEquals("0점", compareDeltaLabel(0))
    }
}
