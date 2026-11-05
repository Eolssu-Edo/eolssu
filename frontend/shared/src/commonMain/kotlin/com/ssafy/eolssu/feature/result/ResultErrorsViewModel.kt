package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.feature.practice.toSection
import kotlinx.coroutines.async
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-03 오류 위치(2차) 상태. 틀린 정간은 평가 결과(EVA-01), 취약 구간은 서버 추천(EVA-03)이다.
 *
 * @property loading 데이터를 받는 중. 끝났는데 [song]·[result]가 없으면 찾지 못한 것이다.
 * @property section 서버가 추천한 취약 구간(각 전체). 추천할 구간이 없거나 받지 못하면 null.
 * @property selectedCell 눌러 고른 정간. null이면 [shownCell]이 취약 구간의 첫 오류를 고른다.
 */
data class ResultErrorsUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val result: EvaluationResult? = null,
    val filter: ErrorFilter = ErrorFilter.All,
    val selectedCell: ScoreCellPosition? = null,
    val section: ScoreSection? = null,
) {
    /** 취약 구간 안의 틀린 정간 수. */
    val sectionErrorCount: Int get() = section?.let { sec -> result?.errors?.count { sec.contains(it.column, it.row) } } ?: 0

    /** 고른 정간. 고르지 않았으면 추천 취약 구간의 첫 오류, 추천이 없으면 틀린 정간이 가장 몰린 곳의 첫 오류다. */
    val shownCell: ScoreCellPosition?
        get() = selectedCell
            ?: section?.let { sec -> result?.errors?.filter { sec.contains(it.column, it.row) }?.minWithOrNull(compareBy({ it.column }, { it.row })) }
                ?.let { ScoreCellPosition(it.column, it.row) }
            ?: result?.defaultSelectedCell()
}

class ResultErrorsViewModel(
    private val sourceId: String,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultErrorsUiState())
    val uiState: StateFlow<ResultErrorsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val picks = async { repository.weakSections(sourceId) }
            val source = repository.source(sourceId)
            val section = source?.let { picks.await()?.firstOrNull()?.toSection(it.song.score) }
            _uiState.update { it.copy(loading = false, song = source?.song, result = source?.evaluation, section = section) }
        }
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    fun selectCell(cell: ScoreCellPosition) = _uiState.update { it.copy(selectedCell = cell) }
}
