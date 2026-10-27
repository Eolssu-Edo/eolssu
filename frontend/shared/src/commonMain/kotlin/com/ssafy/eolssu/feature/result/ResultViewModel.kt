package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-02 상태.
 *
 * @property savedRecord '연습 기록 저장'으로 저장한 기록. 있으면 저장 확인 시트를 띄운다.
 */
data class ResultUiState(
    val result: EvaluationResult? = null,
    val song: Song? = null,
    val savedRecord: PracticeRecord? = null,
    val sheetOpen: Boolean = false,
    val memo: String = "",
)

class ResultViewModel(
    evaluationId: String,
    evaluationRepository: EvaluationRepository,
    songRepository: SongRepository,
    private val recordRepository: PracticeRecordRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUiState(result = evaluationRepository.result(evaluationId)))
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val songId = _uiState.value.result?.songId
        if (songId != null) viewModelScope.launch { _uiState.update { it.copy(song = songRepository.getSong(songId)) } }
    }

    /** 연습 기록에 저장하고 확인 시트를 연다. 같은 결과는 다시 저장하지 않는다. */
    fun save() {
        val state = _uiState.value
        val result = state.result ?: return
        val title = state.song?.title ?: return
        viewModelScope.launch {
            val record = recordRepository.saveEvaluation(result, title)
            _uiState.update { it.copy(savedRecord = record, sheetOpen = true, memo = record.memo) }
        }
    }

    fun changeMemo(memo: String) = _uiState.update { it.copy(memo = memo) }

    /** 시트를 닫거나 기록으로 갈 때 적은 메모를 남긴다. */
    fun closeSheet() {
        commitMemo()
        _uiState.update { it.copy(sheetOpen = false) }
    }

    fun commitMemo() {
        val state = _uiState.value
        val record = state.savedRecord ?: return
        if (record.memo == state.memo) return
        viewModelScope.launch {
            recordRepository.updateMemo(record.id, state.memo)
            _uiState.update { it.copy(savedRecord = record.copy(memo = state.memo)) }
        }
    }
}
