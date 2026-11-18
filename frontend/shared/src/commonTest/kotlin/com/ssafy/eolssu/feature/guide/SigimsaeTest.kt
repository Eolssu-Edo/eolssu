package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SigimsaeTest {

    @Test
    fun basicListHasElevenAndMoreListHasThirtyFive() {
        assertEquals(11, BasicSigimsaeCount)
        assertEquals(listOf(18, 17), MoreSigimsae.map { it.symbols.size })
        assertEquals(35, MoreSigimsaeCount)
    }

    @Test
    fun everyMoreSymbolHasItsOwnOneLineDescription() {
        val symbols = MoreSigimsae.flatMap { it.symbols }
        assertTrue(symbols.all { it.description.isNotBlank() && '\n' !in it.description })
        assertEquals(symbols.size, symbols.map { it.name }.toSet().size)
    }

    @Test
    fun onlyGraceGroupCarriesTheOneStepHint() {
        assertEquals(listOf("앞에서 스쳐요"), BasicSigimsae.filter { it.hint != null }.map { it.title })
        assertTrue(BasicSigimsae.first { it.hint != null }.items.all { it.figure is SigimsaeFigure.Grace })
    }

    @Test
    fun everyBasicSigimsaeHasItsOwnSoundFile() {
        val items = BasicSigimsae.flatMap { it.items }
        assertEquals(items.size, items.map { it.sound }.toSet().size)
        assertTrue(items.all { it.sound.matches(Regex("[a-z]+")) })
        assertEquals("files/sigimsae/chuseong.mp3", items.first().soundPath)
    }

    @Test
    fun graceSlidesFromTheDotDownToTheWrittenNote() {
        val (d, dots) = gracePath(listOf(1))
        assertEquals("M9 31C16 31 16 42 23 42H68", d)
        assertEquals(listOf(9f to 31f), dots)
    }

    @Test
    fun noniroRollsFromTheNoteToOneAboveAndBack() {
        val (d, dots) = gracePath(listOf(0, 1))
        assertEquals("M9 42C16 42 15 31 22 31C29 31 29 42 36 42H68", d)
        assertEquals(listOf(9f to 42f, 22f to 31f), dots)
    }
}
