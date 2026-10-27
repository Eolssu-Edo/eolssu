package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.AnalysisFailedException
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ANALYSIS-01 상태. 분석이 끝나면 [evaluationId]가 채워져 결과 화면으로 간다. 실패하면 [failure]에 사유가 있다. */
data class AnalysisUiState(
    val song: Song? = null,
    val failure: AnalysisFailure? = null,
    val retrying: Boolean = false,
    val evaluationId: String? = null,
)

/** 서버가 알린 실패 사유. 네트워크 오류처럼 사유가 없는 실패는 [AnalysisFailure.ServerError]로 본다. */
internal fun Exception.toAnalysisFailure(): AnalysisFailure =
    (this as? AnalysisFailedException)?.reason ?: AnalysisFailure.ServerError

/** 같은 녹음으로 다시 분석할 수 있는 실패이고, 다시 시도하는 중이 아닌지. */
internal fun AnalysisUiState.canRetry(): Boolean = !retrying && failure?.retryable == true

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

    /** 분석 다시 시도. 같은 녹음으로 다시 분석할 수 있는 실패에서만 하며, 시도 중에는 버튼이 돈다. */
    fun retry() {
        if (!_uiState.value.canRetry()) return
        _uiState.update { it.copy(retrying = true) }
        viewModelScope.launch { analyze() }
    }

    private suspend fun analyze() {
        val song = _uiState.value.song ?: return
        try {
            val result = evaluationRepository.evaluate(songId, bpm, song.durationSeconds(bpm))
            _uiState.update { it.copy(evaluationId = result.id, failure = null, retrying = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(failure = e.toAnalysisFailure(), retrying = false) }
        }
    }
}
