package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.Blow

class FingeringTest {

    @Test
    fun everyNoteHasOneStatePerDrawnHole() {
        GuideInstrument.entries.forEach { instrument ->
            instrument.blows.forEach { blow ->
                instrument.notesByPitch(blow).forEach { note ->
                    assertEquals(instrument.holes.size, note.holes.size, "${instrument.id} ${note.hanja}")
                }
            }
        }
    }

    @Test
    fun tableSizesMatchWireframe() {
        fun sizes(instrument: GuideInstrument) = instrument.blows.map { instrument.notesByPitch(it).size }
        assertEquals(listOf(7, 5), sizes(GuideInstrument.Danso))
        assertEquals(listOf(9, 11), sizes(GuideInstrument.Sogeum))
        assertEquals(listOf(12, 12, 6), sizes(GuideInstrument.DaegeumJeongak))
        assertEquals(listOf(12, 12, 6), sizes(GuideInstrument.DaegeumSanjo))
    }

    @Test
    fun notesAreListedFromLowToHigh() {
        assertEquals(
            listOf("夾", "仲", "林", "南", "無", "潢", "汰"),
            GuideInstrument.Danso.notesByPitch(Blow.Mid).map { it.hanja },
        )
        val low = GuideInstrument.DaegeumJeongak.notesByPitch(Blow.Low).map { it.hanja }
        assertEquals("㑣", low.first())
        assertEquals("蕤", low.last())
    }

    @Test
    fun missingBlowFallsBackToMid() {
        assertEquals(Blow.Mid, GuideInstrument.Sogeum.resolveBlow(Blow.Low))
        assertEquals(GuideInstrument.Sogeum.notesByPitch(Blow.Mid), GuideInstrument.Sogeum.notesByPitch(Blow.Low))
        assertEquals(Blow.Low, GuideInstrument.DaegeumSanjo.resolveBlow(Blow.Low))
    }

    @Test
    fun parsesHoleStatesAndFlagsUncheckedFingering() {
        val half = GuideInstrument.Danso.noteOf("南")!!
        assertEquals(listOf(HoleState.Closed, HoleState.Closed, HoleState.Half, HoleState.Open, HoleState.Open), half.holes)
        assertFalse(half.basic)
        assertFalse(half.needsCheck)

        val unknown = GuideInstrument.Danso.noteOf("㶃")!!
        assertTrue(unknown.needsCheck)
        assertEquals("청태", unknown.reading)
    }

    @Test
    fun findsNotesAcrossBlowsAndInstruments() {
        assertEquals("탁임종", GuideInstrument.DaegeumJeongak.noteOf("㑣")?.name)
        assertEquals("황", GuideInstrument.Danso.noteOf("潢")?.reading)
        assertEquals("청황", GuideInstrument.Sogeum.noteOf("潢")?.reading)
        assertNull(GuideInstrument.Danso.noteOf("㑣"))
        assertEquals("파", GuideInstrument.DaegeumSanjo.solfegeOf("黃"))
    }

    @Test
    fun findsInstrumentById() {
        assertEquals(GuideInstrument.DaegeumSanjo, GuideInstrument.fromId("daegeum-sanjo"))
        assertNull(GuideInstrument.fromId("piri"))
        assertNull(GuideInstrument.fromId(null))
    }
}
