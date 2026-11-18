package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * COMPARE-01. 기록 [recordId]와 그보다 먼저 같은 곡을 처음부터 끝까지 연주한 기록을 비교한다(REC-02). 처음에는 [previousId]
 * (이전 연주 결과의 기록 고르기 시트에서 고른 기록), 없으면 바로 전 기록과 견준다. 이전 기록을 고르면 그 기록의 틀린 정간과
 * 서버 비교 결과(EVA-04)를 받는다.
 */
class CompareViewModel(
    private val recordId: String,
    private val repository: WeakSectionRepository,
    previousId: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState(previousId = previousId))
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()
    private var previousJob: Job? = null

    init {
        viewModelScope.launch {
            val comparison = repository.comparison(recordId)
            _uiState.update { it.copy(loading = false, comparison = comparison) }
            loadPrevious()
        }
    }

    fun openPicker() = _uiState.update { it.copy(sheetOpen = true) }

    fun closePicker() = _uiState.update { it.copy(sheetOpen = false) }

    fun toggleMetrics() = _uiState.update { it.copy(metricsOpen = !it.metricsOpen) }

    /** 시트에서 기록을 누르면 바로 그 기록과 비교한다. 이번 기록보다 늦은 기록은 고를 수 없다. */
    fun pickRecord(recordId: String) {
        if (_uiState.value.comparison?.previous?.none { it.id == recordId } != false) return
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
