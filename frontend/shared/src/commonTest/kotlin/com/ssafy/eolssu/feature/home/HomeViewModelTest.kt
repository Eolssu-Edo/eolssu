package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test
    fun startsWithDaegeumAndClosedSheet() {
        val state = HomeViewModel().uiState.value

        assertEquals(Instrument.Daegeum, state.selectedInstrument)
        assertFalse(state.isInstrumentSheetOpen)
        assertEquals(
            listOf(Instrument.Daegeum, Instrument.Sogeum, Instrument.Danso, Instrument.Piri),
            state.instruments,
        )
    }

    @Test
    fun selectingAnInstrumentChangesItAndClosesTheSheet() {
        val viewModel = HomeViewModel()

        viewModel.openInstrumentSheet()
        assertTrue(viewModel.uiState.value.isInstrumentSheetOpen)

        viewModel.selectInstrument(Instrument.Danso)

        assertEquals(Instrument.Danso, viewModel.uiState.value.selectedInstrument)
        assertFalse(viewModel.uiState.value.isInstrumentSheetOpen)
    }

    @Test
    fun dismissingTheSheetKeepsTheCurrentInstrument() {
        val viewModel = HomeViewModel()
        viewModel.selectInstrument(Instrument.Piri)

        viewModel.openInstrumentSheet()
        viewModel.dismissInstrumentSheet()

        assertEquals(Instrument.Piri, viewModel.uiState.value.selectedInstrument)
        assertFalse(viewModel.uiState.value.isInstrumentSheetOpen)
    }
}
