package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test
    fun startsWithDansoAndClosedSheet() {
        val state = HomeViewModel().uiState.value

        assertEquals(Instrument.Danso, state.selectedInstrument)
        assertFalse(state.isInstrumentSheetOpen)
        assertEquals(
            listOf(Instrument.Daegeum, Instrument.Sogeum, Instrument.Danso),
            state.instruments,
        )
    }

    @Test
    fun selectingAnInstrumentChangesItAndClosesTheSheet() {
        val viewModel = HomeViewModel()

        viewModel.openInstrumentSheet()
        assertTrue(viewModel.uiState.value.isInstrumentSheetOpen)

        viewModel.selectInstrument(Instrument.Daegeum)

        assertEquals(Instrument.Daegeum, viewModel.uiState.value.selectedInstrument)
        assertFalse(viewModel.uiState.value.isInstrumentSheetOpen)
    }

    @Test
    fun dismissingTheSheetKeepsTheCurrentInstrument() {
        val viewModel = HomeViewModel()
        viewModel.selectInstrument(Instrument.Sogeum)

        viewModel.openInstrumentSheet()
        viewModel.dismissInstrumentSheet()

        assertEquals(Instrument.Sogeum, viewModel.uiState.value.selectedInstrument)
        assertFalse(viewModel.uiState.value.isInstrumentSheetOpen)
    }
}
