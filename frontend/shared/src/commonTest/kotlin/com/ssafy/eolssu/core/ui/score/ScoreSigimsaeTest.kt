package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.feature.guide.BasicSigimsae
import com.ssafy.eolssu.feature.guide.MoreSigimsae
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ScoreSigimsaeTest {
    @Test fun sourceDotUsesItsOwnGlyphWithoutBecomingALengthOrArticulationMark() {
        val raw = "원문점@100,-34,-9.5" // CAT-04 song 19 v2, c0-j5-r0-u0.
        val dot = assertNotNull(scoreSigimsaeImage(raw))
        assertEquals(dot, scoreSigimsaeImage("원문점"))
        assertEquals("원문점", scoreSigimsaeName(raw))
        assertNotEquals(scoreSigimsaeImage("반길이표"), dot)
        assertNotEquals(scoreSigimsaeImage("끊는표"), dot)
        assertTrue("원문점" in RegisteredSigimsaeNames)
    }

    @Test fun everySanjoJindoArirangMarkUsesAGlyphInsteadOfItsKoreanName() {
        // CAT-04 song 85 (SANJO_DAEGEUM v1), 2026-11-13: 49 marks, 31 previously fell back to text.
        val sourceMarks = listOf("농음표@100,10,10", "혀치기@100,10,0", "반음올림표@100,10,0",
            "풀어내림표@100,10,10", "꺾는음", "꺾는음@100,10,0", "끊는표@100,10,0", "니레@100,10,0", "반음올림표")
        sourceMarks.forEach { raw -> assertNotNull(scoreSigimsaeImage(raw), raw) }
        for (name in listOf("혀치기", "반음올림표", "꺾는음")) {
            assertEquals(name, scoreSigimsaeName("$name@100,10,0"))
            assertTrue(name in RegisteredSigimsaeNames) // 표시되는 원본 기호를 편집기에서도 다시 넣을 수 있다.
        }
    }

    @Test fun jeonseongShowsItsProvisionalGlyphOnceInTheEditorList() {
        assertNotNull(scoreSigimsaeImage("전성"))
        assertEquals(1, com.ssafy.eolssu.feature.song.edit.Sigimsae.entries.count { it.name == "전성" })
    }

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
