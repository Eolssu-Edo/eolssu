package com.ssafy.eolssu.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun openInstrumentSheet() {
        _uiState.update { it.copy(isInstrumentSheetOpen = true) }
    }

    fun dismissInstrumentSheet() {
        _uiState.update { it.copy(isInstrumentSheetOpen = false) }
    }

    fun selectInstrument(instrument: Instrument) {
        _uiState.update { it.copy(selectedInstrument = instrument, isInstrumentSheetOpen = false) }
    }
}
