package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.recommendedSection
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-03 오류 위치(2차) 상태. 지금은 예시 평가 결과를 쓴다.
 *
 * @property loading 데이터를 받는 중. 끝났는데 [song]·[result]가 없으면 찾지 못한 것이다.
 * @property selectedCell 눌러 고른 정간. null이면 [shownCell]이 취약 구간의 첫 오류를 고른다.
 */
data class ResultErrorsUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val result: EvaluationResult? = null,
    val filter: ErrorFilter = ErrorFilter.All,
    val selectedCell: ScoreCellPosition? = null,
) {
    /** 추천 취약 구간(틀린 정간이 가장 많이 몰린 두 각). */
    val section: ScoreSection? get() = song?.let { s -> result?.recommendedSection(s.score) }

    /** 취약 구간 안의 틀린 정간 수. */
    val sectionErrorCount: Int get() = section?.let { sec -> result?.errors?.count { sec.contains(it.column, it.row) } } ?: 0

    val shownCell: ScoreCellPosition? get() = selectedCell ?: result?.defaultSelectedCell()
}

class ResultErrorsViewModel(
    private val sourceId: String,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultErrorsUiState())
    val uiState: StateFlow<ResultErrorsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val source = repository.source(sourceId)
            _uiState.update { it.copy(loading = false, song = source?.song, result = source?.evaluation) }
        }
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    fun selectCell(cell: ScoreCellPosition) = _uiState.update { it.copy(selectedCell = cell) }
}
