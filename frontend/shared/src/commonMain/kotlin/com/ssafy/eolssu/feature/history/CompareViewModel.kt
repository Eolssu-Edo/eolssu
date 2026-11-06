package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * COMPARE-01. 기록 [recordId]와 그 전 2주 안의 같은 곡 전체 연주 기록을 비교한다(REC-02). 이전 기록을 고르면 그 기록의 틀린 정간과
 * 서버 비교 결과(EVA-04)를 받는다. 녹음은 아직 재생하지 않는다.
 */
class CompareViewModel(
    private val recordId: String,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()
    private var previousJob: Job? = null

    init {
        viewModelScope.launch {
            val comparison = repository.comparison(recordId)
            _uiState.update { it.copy(loading = false, comparison = comparison) }
            loadPrevious()
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

    fun pickRecord(recordId: String) {
        _uiState.update { it.copy(previousId = recordId, sheetOpen = false) }
        loadPrevious()
    }

    /** 지금 고른 이전 기록의 틀린 정간과 비교 결과를 받는다. 다른 기록을 고르면 앞 요청은 버린다. */
    private fun loadPrevious() {
        val previous = _uiState.value.previous ?: return
        if (_uiState.value.previousDetail?.record?.id == previous.id) return
        previousJob?.cancel()
        previousJob = viewModelScope.launch {
            val detail = repository.previous(recordId, previous.id)
            _uiState.update { it.copy(previousDetail = detail) }
        }
    }
}
