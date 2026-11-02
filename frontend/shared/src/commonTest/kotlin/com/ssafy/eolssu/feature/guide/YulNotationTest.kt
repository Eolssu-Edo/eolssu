package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YulNotationTest {

    @Test
    fun reducesClearAndLowGlyphsToBaseYul() {
        assertEquals("黃", baseYulOf("潢"))
        assertEquals("黃", baseYulOf("㶂"))
        assertEquals("林", baseYulOf("㑣"))
        assertEquals("林", baseYulOf("林"))
        assertNull(baseYulOf("ㅡ"))
    }

    @Test
    fun solfegeCountsSemitonesFromHwangjong() {
        // 정악대금·소금·단소는 황종 = E♭(3)
        assertEquals(listOf("미♭", "파", "라♭", "시♭", "도"), listOf("黃", "太", "仲", "林", "南").map { solfegeOf(it, 3) })
        // 청성·탁성은 옥타브를 적지 않고 같은 서양 음이름
        assertEquals("미♭", solfegeOf("㶂", 3))
        assertEquals("시♭", solfegeOf("㑣", 3))
        // 산조대금은 황종 = F(5)라 임종이 C
        assertEquals("파", solfegeOf("黃", 5))
        assertEquals("도", solfegeOf("林", 5))
        assertEquals("", solfegeOf("ㅡ", 3))
    }

    @Test
    fun westernNameIsShownInParenthesesBesideYulName() {
        assertEquals("(미♭)", westernNameLabel(solfegeOf("黃", 3)))
        assertEquals("", westernNameLabel(solfegeOf("ㅡ", 3)))
    }

    @Test
    fun pitchIndexPutsLowGlyphsBelowAndDoubleClearAbove() {
        assertEquals(-5, pitchIndexOf("㑣"))
        assertEquals(0, pitchIndexOf("黃"))
        assertEquals(12, pitchIndexOf("潢"))
        assertEquals(24, pitchIndexOf("㶂"))
        assertEquals(UNKNOWN_PITCH, pitchIndexOf("?"))
    }

    @Test
    fun glyphsMissingFromSerifFontAreSplitIntoRadicalAndBase() {
        assertEquals(HanjaGlyphParts("", "潢"), glyphPartsOf("潢"))
        assertEquals(HanjaGlyphParts("亻", "林"), glyphPartsOf("㑣"))
        assertEquals(HanjaGlyphParts("氵", "仲"), glyphPartsOf("㳞"))
        assertEquals(HanjaGlyphParts("氵", "大"), glyphPartsOf("汏"))
        // 중청: 글꼴에 있는 청성 글자가 있으면 그 앞에 氵 하나, 없으면 氵 둘
        assertEquals(HanjaGlyphParts("氵", "潢"), glyphPartsOf("㶂"))
        assertEquals(HanjaGlyphParts("氵", "淋"), glyphPartsOf("㶍"))
        assertEquals(HanjaGlyphParts("氵氵", "姑"), glyphPartsOf("㴩"))
    }

    @Test
    fun everyFingeringGlyphCanBeDrawn() {
        GuideInstrument.entries.forEach { instrument ->
            Blow.entries.forEach { blow ->
                instrument.notesByPitch(blow).forEach { note ->
                    val parts = glyphPartsOf(note.hanja)
                    assertEquals(true, parts.base in SerifYulGlyphs, "${instrument.id} ${note.hanja} → $parts")
                }
            }
        }
    }
}
