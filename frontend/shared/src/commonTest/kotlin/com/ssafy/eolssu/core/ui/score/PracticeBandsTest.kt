package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Jangdan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 태블릿 세로 연습 악보의 두 단 나누기(와이어프레임 `Tab-Practice-Port`, 악보 영역 736×980). */
class PracticeBandsTest {

    private val semachi = Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3)
    private val gagok16 = Jangdan("가곡 16박", daegang = listOf(11, 5), beats = 16, subdivision = 1)
    private val yeominrak = Jangdan("여민락", daegang = listOf(6, 4, 4, 6), beats = 20, subdivision = 1)

    private fun bands(jangdan: Jangdan, columns: Int, big: Boolean = false, height: Int = 980) = twoBandColumnCount(
        columnCount = columns,
        stripsPerColumn = jangdan.strips.size,
        longestStrip = jangdan.strips.max(),
        availableWidth = 736.dp,
        availableHeight = height.dp,
        metrics = JeongganboMetrics.practice(jangdan, height.dp, big, tablet = true),
        minCellHeight = JeongganboMetrics.practiceMinCellHeight(big),
    )

    @Test
    fun nineCellGakFitsNineColumnsPerBand() {
        assertEquals(9, bands(semachi, columns = 16))
    }

    @Test
    fun splitGakSharesBandWidthBetweenStrips() {
        // 여민락은 한 각이 10 / 10 두 줄이라 한 단에 각 4개(줄 8개)
        assertEquals(4, bands(yeominrak, columns = 6))
    }

    @Test
    fun elevenCellStripStaysInOneBand() {
        assertNull(bands(gagok16, columns = 5))
    }

    @Test
    fun oneBandWhenEverythingFitsOrWindowIsShort() {
        assertNull(bands(semachi, columns = 8))
        assertNull(bands(semachi, columns = 16, height = 800))
        assertNull(bands(semachi, columns = 16, big = true))
    }

    @Test
    fun tabletPracticeMetricsUseWireframeSizes() {
        val normal = JeongganboMetrics.practice(semachi, 980.dp, big = false, tablet = true)
        assertEquals(72.dp, normal.columnWidth)
        assertEquals(44.dp, JeongganboMetrics.practice(semachi, 300.dp, big = false, tablet = true).cellHeight)
        assertEquals(92.dp, JeongganboMetrics.practice(semachi, 980.dp, big = true, tablet = true).columnWidth)
    }
}
