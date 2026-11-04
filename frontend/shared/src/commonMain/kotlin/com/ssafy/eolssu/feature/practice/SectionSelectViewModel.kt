package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.recommendedSection
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PRACTICE-02. 평가·기록 [sourceId]의 틀린 정간으로 추천 구간을 잡는다. 지금은 예시 데이터다. */
class SectionSelectViewModel(
    private val sourceId: String,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionSelectUiState())
    val uiState: StateFlow<SectionSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val source = repository.source(sourceId)
            _uiState.update {
                it.copy(
                    loading = false,
                    song = source?.song,
                    result = source?.evaluation,
                    section = source?.let { s -> s.evaluation.recommendedSection(s.song.score) },
                )
            }
        }
    }

    fun tap(position: ScorePosition) = _uiState.update { it.tap(position) }

    fun pickRecommended() = _uiState.update { it.pickRecommended() }

    fun toggleLoop() = _uiState.update { it.copy(loop = !it.loop) }

    fun selectRepeat(repeat: SectionRepeat) = _uiState.update { it.copy(repeat = repeat) }

    fun selectSpeed(speed: TempoStep) = _uiState.update { it.copy(speed = speed) }
}
