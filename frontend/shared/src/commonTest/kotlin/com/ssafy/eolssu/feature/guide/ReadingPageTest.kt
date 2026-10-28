package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.feature.guide.ReadingPage.Intro
import com.ssafy.eolssu.feature.guide.ReadingPage.Length
import com.ssafy.eolssu.feature.guide.ReadingPage.Order
import com.ssafy.eolssu.feature.guide.ReadingPage.Pitch
import com.ssafy.eolssu.feature.guide.ReadingPage.Sigimsae
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingPageTest {

    @Test
    fun routeIdOpensThatPageAndFallsBackToIntro() {
        assertEquals(Length, ReadingPage.fromId("length"))
        assertEquals(Order, ReadingPage.fromId("order"))
        assertEquals(Sigimsae, ReadingPage.fromId("sig"))
        assertEquals(Intro, ReadingPage.fromId(""))
        assertEquals(Intro, ReadingPage.fromId(null))
        assertEquals(Intro, ReadingPage.fromId("symbols"))
    }

    @Test
    fun marksAreJeongThenNumbers() {
        assertEquals(listOf("井", "1", "2", "3", "4"), ReadingPage.entries.map { it.mark })
    }

    @Test
    fun portraitTabletPairsPagesAndGivesSigimsaeAFullRow() {
        assertEquals(listOf(listOf(Intro, Order), listOf(Length, Pitch), listOf(Sigimsae)), readingTabletRows(2))
    }

    @Test
    fun landscapeTabletGivesIntroAFullRowAndPutsOneToThreeTogether() {
        assertEquals(listOf(listOf(Intro), listOf(Order, Length, Pitch), listOf(Sigimsae)), readingTabletRows(3))
    }
}
