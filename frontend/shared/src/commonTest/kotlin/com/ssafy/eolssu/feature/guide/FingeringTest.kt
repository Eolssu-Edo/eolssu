package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun blowTabsSelectShownBlowAndDisableMissingOnes() {
        assertEquals(
            listOf(BlowTab(Blow.Low, selected = false, enabled = false), BlowTab(Blow.Mid, selected = true, enabled = true), BlowTab(Blow.High, selected = false, enabled = true)),
            FingeringUiState(instrument = GuideInstrument.Danso, blow = Blow.Low).blowTabs,
        )
        assertEquals(
            listOf(true, false, false),
            FingeringUiState(instrument = GuideInstrument.DaegeumJeongak, blow = Blow.Low).blowTabs.map { it.selected },
        )
        assertTrue(FingeringUiState(instrument = GuideInstrument.DaegeumJeongak).blowTabs.all { it.enabled })
    }

    @Test
    fun remarksFollowBoardDescriptionRule() {
        assertEquals(emptyList(), GuideInstrument.Danso.noteOf("仲")!!.remarks())
        assertEquals(listOf(NoteRemark.Semitone), GuideInstrument.Danso.noteOf("夾")!!.remarks())
        assertEquals(listOf(NoteRemark.NeedsCheck), GuideInstrument.Danso.noteOf("㶃")!!.remarks())
        // 기초부터 배우기 카드는 반음 표시를 하지 않는다
        assertEquals(emptyList(), GuideInstrument.Danso.noteOf("夾")!!.remarks(inLearn = true))
    }

    @Test
    fun dropdownListsInstrumentsInBoardOrder() {
        assertEquals(listOf("danso", "sogeum", "daegeum-jeongak", "daegeum-sanjo"), GuideInstrument.entries.map { it.id })
    }

    @Test
    fun findsInstrumentById() {
        assertEquals(GuideInstrument.DaegeumSanjo, GuideInstrument.fromId("daegeum-sanjo"))
        assertNull(GuideInstrument.fromId("piri"))
        assertNull(GuideInstrument.fromId(null))
    }
}
