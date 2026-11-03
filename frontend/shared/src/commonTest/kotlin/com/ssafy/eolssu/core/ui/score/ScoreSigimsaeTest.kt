package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.feature.guide.BasicSigimsae
import com.ssafy.eolssu.feature.guide.MoreSigimsae
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScoreSigimsaeTest {
    @Test fun reusesEveryExistingGuideGlyphWithoutReinterpretingItsShape() {
        val basic = BasicSigimsae.flatMap { it.items }.flatMap { it.images }
        val more = MoreSigimsae.flatMap { it.symbols }
        more.forEach { assertEquals(it.image, scoreSigimsaeImage(it.name), it.name) }
        val names = listOf("추성", "미는표", "퇴성", "흘림표", "요성표", "농음표", "니레", "니라", "니로", "노니로", "끊는표", "늘임표")
        assertEquals(basic, names.map { assertNotNull(scoreSigimsaeImage(it)) })
        assertEquals(47, (basic + more.map { it.image }).distinct().size)
        assertEquals(scoreSigimsaeImage("니레"), scoreSigimsaeImage("니레@50,0,0"))
        assertEquals("니레", scoreSigimsaeName("니레@50,0,0"))
        assertNull(scoreSigimsaeImage("깊은요성")) // A missing verified glyph remains its original text.
        assertNull(scoreSigimsaeImage("새로운기호"))
    }
}
