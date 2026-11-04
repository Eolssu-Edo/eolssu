package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** RECORD-01. 화면을 열면 서버에서 목록을 받는다(REC-02). 받지 못하면 '다시 시도'로 다시 받는다. */
class RecordListViewModel(
    justSavedId: String?,
    private val recordRepository: PracticeRecordRepository,
    now: () -> Long,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordListUiState(
            today = KoreanDateTime.fromEpochMillis(now()),
            justSavedId = justSavedId,
            recordsAvailable = recordRepository.available,
        ),
    )
    val uiState: StateFlow<RecordListUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = RecordListUiState(today = _uiState.value.today, recordsAvailable = recordRepository.available)
    }

    init {
        accountScope.launch { recordRepository.records.collect { list -> _uiState.update { it.copy(records = list) } } }
        if (recordRepository.available) retry()
    }

    fun retry() {
        if (!accountScope.isActive) return
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        accountScope.launch {
            val ok = recordRepository.refresh()
            _uiState.update { it.copy(loading = false, loadFailed = !ok) }
        }
    }

    fun toggle(songId: String) = _uiState.update { it.toggle(songId) }

    fun changeSort(sort: RecordSort) = _uiState.update { it.copy(sort = sort) }

    /** 두 칸 배치에서 오른쪽에 볼 기록을 고른다. */
    fun select(recordId: String) = _uiState.update { it.select(recordId) }

    fun show(recordId: String) = _uiState.update { it.show(recordId) }
}
