package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.feature.practice.toSection
import com.ssafy.eolssu.data.practice.WeakSectionPick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** RESULT-04 구간 재평가 결과 상태. 끝났는데 [evaluation]이 없으면 찾지 못한 것이다. */
data class SectionResultUiState(
    val loading: Boolean = true,
    val evaluation: SectionEvaluation? = null,
    /** 구간 정간보에 보일 틀린 종류(연주 결과와 같은 5칸 필터). */
    val filter: ErrorFilter = ErrorFilter.All,
) {
    /** 재평가한 구간(각 전체). 서버 비교(EVA-04)가 알려 준 이번 세션 구간이고, 비교를 받지 못하면 null. */
    val section: ScoreSection?
        get() {
            val evaluation = evaluation ?: return null
            val check = (evaluation.check as? CheckLoad.Loaded)?.check ?: return null
            val start = check.startColumn ?: return null
            val end = check.endColumn ?: return null
            return WeakSectionPick(start, end, 0).toSection(evaluation.song.score)
        }
}

/** 구간만 녹음한 재평가 세션 [sessionId]의 결과(EVA-01)와 바탕 세션과의 비교(EVA-04)를 받는다. */
class SectionResultViewModel(
    sessionId: String,
    repository: WeakSectionRepository,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionResultUiState())
    val uiState: StateFlow<SectionResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(perf.context()) {
            val evaluation = repository.sectionResult(sessionId)
            _uiState.update { it.copy(loading = false, evaluation = evaluation) }
        }
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }
}

/** 곡 전체 평가를 권하는 구간 점수. 이 점수를 넘기면 다음 단계 안내가 ‘곡 전체 다시 평가’를 권한다(시안 85점). */
internal const val WholeSongReadyScore = 85
