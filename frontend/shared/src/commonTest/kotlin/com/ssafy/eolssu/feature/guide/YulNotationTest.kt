package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.solfegeOf
import com.ssafy.eolssu.core.model.fingering.pitchIndexOf
import com.ssafy.eolssu.core.model.fingering.baseYulOf
import com.ssafy.eolssu.core.model.fingering.glyphPartsOf
import com.ssafy.eolssu.core.model.fingering.yulGlyphParts
import com.ssafy.eolssu.core.model.fingering.HanjaGlyphParts
import com.ssafy.eolssu.core.model.fingering.SerifYulGlyphs
import com.ssafy.eolssu.core.model.fingering.UNKNOWN_PITCH
import com.ssafy.eolssu.core.model.fingering.westernNameLabel

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
    fun clearAndLowGlyphsInSerifFontAreDrawnAsIs() {
        // 탁성·청성·중청 모두 세리프 글꼴에 실제 글자가 있어 변으로 조합하지 않는다.
        listOf("潢", "㑣", "㒣", "㳞", "汏", "㶂", "㶍", "㴩").forEach { hanja ->
            assertEquals(HanjaGlyphParts("", hanja), glyphPartsOf(hanja))
        }
    }

    @Test
    fun glyphsMissingFromFontGetTheOctaveRadicalBeforeTheBaseYul() {
        // 글꼴에 없는 글자(여기서는 "?")만 옥타브의 변 + 기본 율명으로 나눈다.
        assertEquals(HanjaGlyphParts("彳", "林"), yulGlyphParts("?", "林", -2))
        assertEquals(HanjaGlyphParts("亻", "林"), yulGlyphParts("?", "林", -1))
        assertEquals(HanjaGlyphParts("", "林"), yulGlyphParts("?", "林", 0))
        assertEquals(HanjaGlyphParts("氵", "林"), yulGlyphParts("?", "林", 1))
        assertEquals(HanjaGlyphParts("氵氵", "林"), yulGlyphParts("?", "林", 2))
        assertEquals(HanjaGlyphParts("", "林"), yulGlyphParts("?", "林", 3))
        assertEquals(HanjaGlyphParts("", "㑣"), yulGlyphParts("㑣", "林", -1))
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
