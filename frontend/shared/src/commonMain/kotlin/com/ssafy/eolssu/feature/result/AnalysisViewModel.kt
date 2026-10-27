package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ANALYSIS-01 상태. 분석이 끝나면 [evaluationId]가 채워져 결과 화면으로 간다. */
data class AnalysisUiState(
    val song: Song? = null,
    val failed: Boolean = false,
    val retrying: Boolean = false,
    val evaluationId: String? = null,
)

class AnalysisViewModel(
    private val songId: String,
    private val bpm: Int,
    private val songRepository: SongRepository,
    private val evaluationRepository: EvaluationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(song = songRepository.getSong(songId)) }
            analyze()
        }
    }

    /** 분석 다시 시도. 시도 중에는 버튼이 돈다. */
    fun retry() {
        if (_uiState.value.retrying) return
        _uiState.update { it.copy(retrying = true) }
        viewModelScope.launch { analyze() }
    }

    private suspend fun analyze() {
        val song = _uiState.value.song ?: return
        try {
            val result = evaluationRepository.evaluate(songId, bpm, song.durationSeconds(bpm))
            _uiState.update { it.copy(evaluationId = result.id, failed = false, retrying = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(failed = true, retrying = false) }
        }
    }
}
