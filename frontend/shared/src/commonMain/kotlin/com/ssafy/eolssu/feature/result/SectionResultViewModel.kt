package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** RESULT-04 구간 재평가 결과 상태. 끝났는데 [evaluation]이 없으면 찾지 못한 것이다. */
data class SectionResultUiState(val loading: Boolean = true, val evaluation: SectionEvaluation? = null) {
    /** 이전 구간 점수와의 차이. 이전 점수가 없으면 null. */
    val delta: Int? get() = evaluation?.let { e -> e.previousTotal?.let { e.total - it } }
}

class SectionResultViewModel(
    sourceId: String,
    section: ScoreSection,
    bpm: Int,
    repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionResultUiState())
    val uiState: StateFlow<SectionResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val evaluation = repository.evaluateSection(sourceId, section, bpm)
            _uiState.update { it.copy(loading = false, evaluation = evaluation) }
        }
    }
}

/** 곡 전체 평가를 권하는 구간 점수. 이 점수를 넘기면 다음 단계 안내가 ‘곡 전체 다시 평가’를 권한다(시안 85점). */
internal const val WholeSongReadyScore = 85
