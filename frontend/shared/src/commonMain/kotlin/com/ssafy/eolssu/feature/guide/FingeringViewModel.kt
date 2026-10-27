package com.ssafy.eolssu.feature.guide

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** GUIDE-01. 기초부터 배우기의 '더 보기'로 들어오면 그 악기·취법으로 시작한다. */
class FingeringViewModel(
    instrument: GuideInstrument = GuideInstrument.Default,
    blow: Blow = Blow.Mid,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FingeringUiState(instrument = instrument, blow = blow))
    val uiState: StateFlow<FingeringUiState> = _uiState.asStateFlow()

    fun toggleDropdown() = _uiState.update { it.toggleDropdown() }

    fun dismissDropdown() = _uiState.update { it.dismissDropdown() }

    fun selectInstrument(instrument: GuideInstrument) = _uiState.update { it.selectInstrument(instrument) }

    fun selectBlow(blow: Blow) = _uiState.update { it.selectBlow(blow) }

    fun listen(hanja: String) = _uiState.update { it.listen(hanja) }

    fun finishListening(hanja: String) = _uiState.update { it.finishListening(hanja) }
}
