package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.ScoreTiming
import kotlin.test.*

class JangdanTimingTest {
    @Test fun distinctPatternsKeepTheirMusicalDurationInPractice() {
        data class Case(val name: String, val count: Int, val cell: Int, val ticks: Long)
        for (case in listOf(Case("단모리", 8, 1, 48), Case("세령산", 10, 3, 180), Case("4박 기본 반주", 4, 2, 48))) {
            val pattern = CatalogJangdanPattern(List(case.count) { "덩" }, listOf(case.count))
            val detail = CatalogJangdanDetail("12", case.name, 60, "QUARTER", pattern, patternCellEighths = case.cell)
            val timing = ScoreTiming(440.0, case.name, ScoreBpmUnit.QUARTER, 12, 12)
            assertEquals(case.ticks, assertNotNull(detail.playbackFor(timing)).cycleTicks)
            assertNull(detail.copy(patternCellEighths = null).playbackFor(timing))
            assertNull(detail.copy(bpmUnit = "DOTTED_QUARTER").playbackFor(timing))
        }
    }
}
