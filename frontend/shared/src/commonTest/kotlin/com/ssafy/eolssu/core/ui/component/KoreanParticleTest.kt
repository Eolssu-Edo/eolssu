package com.ssafy.eolssu.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class KoreanParticleTest {
    @Test
    fun euroRoFollowsFinalConsonant() {
        assertEquals("기본음으로", "기본음".withParticle(KoreanParticle.EuroRo))
        assertEquals("첫 소리로", "첫 소리".withParticle(KoreanParticle.EuroRo))
        assertEquals("대금으로", "대금".withParticle(KoreanParticle.EuroRo))
        assertEquals("단소로", "단소".withParticle(KoreanParticle.EuroRo))
    }

    @Test
    fun euroRoUsesRoAfterRieul() {
        assertEquals("가락 놀이 한 줄로", "가락 놀이 한 줄".withParticle(KoreanParticle.EuroRo))
        assertEquals("1로", "1".withParticle(KoreanParticle.EuroRo))
    }

    @Test
    fun otherParticlesPickByBatchim() {
        assertEquals("장단을", "장단".withParticle(KoreanParticle.EulReul))
        assertEquals("반주를", "반주".withParticle(KoreanParticle.EulReul))
        assertEquals("소리가", "소리".withParticle(KoreanParticle.IGa))
        assertEquals("음이", "음".withParticle(KoreanParticle.IGa))
        assertEquals("음은", "음".withParticle(KoreanParticle.EunNeun))
        assertEquals("소리와", "소리".withParticle(KoreanParticle.GwaWa))
        assertEquals("장단 미지정으로", "장단 미지정".withParticle(KoreanParticle.EuroRo))
        assertEquals("기본 박 반주로", "기본 박 반주".withParticle(KoreanParticle.EuroRo))
    }

    @Test
    fun digitsReadAsSinoKorean() {
        assertEquals("4/4를", "4/4".withParticle(KoreanParticle.EulReul))
        assertEquals("12/8을", "12/8".withParticle(KoreanParticle.EulReul))
        assertEquals("6/8을", "6/8".withParticle(KoreanParticle.EulReul))
        assertEquals("3/3을", "3/3".withParticle(KoreanParticle.EulReul))
    }

    @Test
    fun skipsClosingMarksAndFallsBackForOtherText() {
        assertEquals("소리(청)으로", "소리(청)".withParticle(KoreanParticle.EuroRo))
        assertEquals("Apple로", "Apple".withParticle(KoreanParticle.EuroRo))
        assertEquals("로", "".withParticle(KoreanParticle.EuroRo))
    }
}
