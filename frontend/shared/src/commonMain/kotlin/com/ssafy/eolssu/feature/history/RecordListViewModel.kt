package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordListViewModel(
    justSavedId: String?,
    recordRepository: PracticeRecordRepository,
    now: () -> Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordListUiState(today = KoreanDateTime.fromEpochMillis(now()), justSavedId = justSavedId),
    )
    val uiState: StateFlow<RecordListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { recordRepository.records.collect { list -> _uiState.update { it.copy(records = list) } } }
    }

    fun toggle(songId: String) = _uiState.update { it.toggle(songId) }

    fun changeSort(sort: RecordSort) = _uiState.update { it.copy(sort = sort) }
}
