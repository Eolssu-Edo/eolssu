package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import kotlinx.coroutines.async
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PRACTICE-02. 세션 [sourceId]의 틀린 정간(EVA-01)과 서버 추천 구간(EVA-03)으로 처음 구간을 잡는다. */
class SectionSelectViewModel(
    private val sourceId: String,
    private val repository: WeakSectionRepository,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionSelectUiState())
    val uiState: StateFlow<SectionSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(perf.context()) {
            val picks = async { repository.weakSections(sourceId) }
            val source = repository.source(sourceId)
            val recommended = source?.let { picks.await()?.firstOrNull()?.toSection(it.song.score) }
            val current = source?.let { repository.isCurrent(it) }
            _uiState.update {
                it.copy(loading = false, song = source?.song, result = source?.evaluation, recommended = recommended, section = recommended, currentScore = current)
            }
        }
    }

    fun tap(position: ScorePosition) = _uiState.update { it.tap(position) }

    fun resetToRecommended() = _uiState.update { it.resetToRecommended() }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }


    fun selectSpeed(speed: TempoStep) = _uiState.update { it.copy(speed = speed) }
}
