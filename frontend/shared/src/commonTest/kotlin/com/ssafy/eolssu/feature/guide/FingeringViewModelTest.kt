package com.ssafy.eolssu.feature.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FingeringViewModelTest {

    @Test
    fun startsWithDansoMidOrWithGivenInstrumentAndBlow() {
        val home = FingeringViewModel().uiState.value
        assertEquals(GuideInstrument.Danso, home.instrument)
        assertEquals(Blow.Mid, home.shownBlow)
        assertFalse(home.isDropdownOpen)

        val fromLearn = FingeringViewModel(GuideInstrument.DaegeumJeongak, Blow.Low).uiState.value
        assertEquals(Blow.Low, fromLearn.shownBlow)
        assertEquals("㑣", fromLearn.notes.first().hanja)
    }

    @Test
    fun selectingAnInstrumentClosesTheDropdown() {
        val viewModel = FingeringViewModel()
        viewModel.toggleDropdown()
        assertTrue(viewModel.uiState.value.isDropdownOpen)

        viewModel.selectInstrument(GuideInstrument.Sogeum)

        assertEquals(GuideInstrument.Sogeum, viewModel.uiState.value.instrument)
        assertFalse(viewModel.uiState.value.isDropdownOpen)
    }

    @Test
    fun keepsChosenBlowWhileShowingMidOnInstrumentsWithoutIt() {
        val viewModel = FingeringViewModel(GuideInstrument.DaegeumJeongak)
        viewModel.selectBlow(Blow.Low)

        viewModel.selectInstrument(GuideInstrument.Danso)
        assertEquals(Blow.Mid, viewModel.uiState.value.shownBlow)

        viewModel.selectInstrument(GuideInstrument.DaegeumSanjo)
        assertEquals(Blow.Low, viewModel.uiState.value.shownBlow)
    }

    @Test
    fun ignoresBlowTheInstrumentDoesNotHave() {
        val viewModel = FingeringViewModel()
        viewModel.selectBlow(Blow.High)
        viewModel.selectBlow(Blow.Low)
        assertEquals(Blow.High, viewModel.uiState.value.blow)
    }

    @Test
    fun listeningHighlightEndsOnlyForTheSameNote() {
        val viewModel = FingeringViewModel()
        viewModel.listen("林")
        viewModel.listen("南")
        viewModel.finishListening("林")
        assertEquals("南", viewModel.uiState.value.playing)

        viewModel.finishListening("南")
        assertNull(viewModel.uiState.value.playing)
    }
}
