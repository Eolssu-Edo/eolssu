package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JangdanTest {

    @Test
    fun gakIsSumOfDaegang() {
        assertEquals(9, Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3).gak)
        assertEquals(20, Jangdan("상령산", daegang = listOf(6, 4, 4, 6), beats = 20, subdivision = 1).gak)
    }

    @Test
    fun daegangStartsAndRowIndex() {
        val jangdan = Jangdan("세령산", daegang = listOf(3, 2, 2, 3), beats = 10, subdivision = 1)
        assertEquals(listOf(0, 3, 5, 7), jangdan.daegangStarts)
        assertEquals(0, jangdan.daegangIndexOfRow(2))
        assertEquals(1, jangdan.daegangIndexOfRow(3))
        assertEquals(2, jangdan.daegangIndexOfRow(6))
        assertEquals(3, jangdan.daegangIndexOfRow(9))
    }

    @Test
    fun stripsKeepShortGakInOneLine() {
        assertEquals(listOf(6), stripsOf(listOf(6)))
        assertEquals(listOf(10), stripsOf(listOf(3, 2, 2, 3)))
        assertEquals(listOf(12), stripsOf(listOf(3, 3, 3, 3)))
    }

    @Test
    fun stripsSplitLongGakAtDaegangBoundary() {
        assertEquals(listOf(11, 5), stripsOf(listOf(11, 5)))
        assertEquals(listOf(10, 10), stripsOf(listOf(6, 4, 4, 6)))
    }

    @Test
    fun stripsDoNotSplitSingleLongDaegang() {
        assertEquals(listOf(20), stripsOf(listOf(20)))
    }

    @Test
    fun cellMillisUsesBeatAndSubdivision() {
        val semachi = Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3)
        assertEquals(1000.0 / 3, semachi.cellMillis(bpm = 60), absoluteTolerance = 1e-9)
        val jungmori = Jangdan("중모리", daegang = listOf(3, 3, 3, 3), beats = 12, subdivision = 1)
        assertEquals(1000.0, jungmori.cellMillis(bpm = 60), absoluteTolerance = 1e-9)
    }

    @Test
    fun rejectsEmptyDaegang() {
        assertFailsWith<IllegalArgumentException> { Jangdan("빈 장단", daegang = emptyList(), beats = 1, subdivision = 1) }
    }
}
