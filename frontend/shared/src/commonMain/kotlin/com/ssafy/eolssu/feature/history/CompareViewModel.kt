package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** COMPARE-01. 기록 [recordId]와 같은 곡의 이전 기록을 비교한다. 지금은 예시 데이터이고 소리는 내지 않는다. */
class CompareViewModel(
    private val recordId: String,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val comparison = repository.comparison(recordId)
            _uiState.update { it.copy(loading = false, comparison = comparison) }
        }
    }

    fun select(position: ScorePosition) = _uiState.update { it.copy(selected = position, playing = false) }

    fun togglePlay() = _uiState.update { it.copy(playing = !it.playing) }

    fun openPicker() = _uiState.update { it.copy(sheetOpen = true, pickDay = null, playing = false) }

    fun closePicker() = _uiState.update { it.copy(sheetOpen = false) }

    /** 녹음한 날만 고를 수 있다. */
    fun pickDay(epochDay: Long) = _uiState.update { state ->
        if (state.calendar.any { it.date.epochDay == epochDay && it.records.isNotEmpty() }) state.copy(pickDay = epochDay) else state
    }

    fun pickRecord(recordId: String) = _uiState.update { it.copy(previousId = recordId, sheetOpen = false) }
}
