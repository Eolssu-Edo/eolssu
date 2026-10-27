package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** ANALYSIS-01 상태. 분석이 끝나면 [evaluationId]가 채워져 결과 화면으로 간다. 실패하면 [failure]에 사유가 있다. */
data class AnalysisUiState(
    val song: Song? = null,
    val failure: AnalysisFailure? = null,
    val retrying: Boolean = false,
    val evaluationId: String? = null,
)

/** 같은 녹음으로 다시 분석할 수 있는 실패이고, 다시 시도하는 중이 아닌지. */
internal fun AnalysisUiState.canRetry(): Boolean = !retrying && failure?.retryable == true

/**
 * 녹음을 분석에 맡기고 2초마다 상태를 묻는다. 결정은 [AnalysisPolling]이 하고, 여기서는 조회·대기만 돌린다.
 * 화면이 백그라운드로 가면 [pause]로 조회를 멈추고, 돌아오면 [resume]으로 한 번 물은 뒤 `PENDING`이면 다시 돈다.
 */
class AnalysisViewModel(
    private val songId: String,
    private val bpm: Int,
    private val songRepository: SongRepository,
    private val evaluationRepository: EvaluationRepository,
    clock: () -> Long = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } },
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    private val polling = AnalysisPolling(clock)
    private var analysisId: String? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(song = songRepository.getSong(songId)) }
            requestAnalysis()
        }
    }

    /** 분석 다시 시도. 같은 녹음으로 다시 분석할 수 있는 실패에서만 하며, 시도 중에는 버튼이 돈다. */
    fun retry() {
        if (!_uiState.value.canRetry()) return
        _uiState.update { it.copy(retrying = true) }
        viewModelScope.launch {
            val id = analysisId ?: return@launch requestAnalysis()
            if (attempt { evaluationRepository.retryAnalysis(id) }) startPolling()
        }
    }

    /** 화면이 백그라운드로 갔다. 서버 분석은 계속된다. */
    fun pause() {
        pollJob?.cancel()
    }

    /** 화면에 돌아왔다. 아직 분석 중이면 바로 한 번 묻고, `PENDING`이면 2초 조회를 다시 시작한다. */
    fun resume() {
        val state = _uiState.value
        if (analysisId == null || state.failure != null || state.evaluationId != null || pollJob?.isActive == true) return
        polling.resume()
        startPolling()
    }

    private suspend fun requestAnalysis() {
        val song = _uiState.value.song ?: return
        if (attempt { analysisId = evaluationRepository.requestAnalysis(songId, bpm, song.durationSeconds(bpm)) }) startPolling()
    }

    /** [block]을 부르고 성공하면 기다리는 상태로 돌린다. 실패하면 사유가 없는 실패라 [AnalysisFailure.ServerError]로 본다. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        polling.resume()
        _uiState.update { it.copy(failure = null, retrying = false) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _uiState.update { it.copy(failure = AnalysisFailure.ServerError, retrying = false) }
        false
    }

    private fun startPolling() {
        val id = analysisId ?: return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val status = try {
                    withTimeoutOrNull(polling.remainingMillis()) { evaluationRepository.analysisStatus(id) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                when (val step = if (status == null) polling.onNoResponse() else polling.onStatus(status)) {
                    is PollStep.Wait -> delay(step.millis)
                    is PollStep.Done -> return@launch _uiState.update { it.copy(evaluationId = step.evaluationId) }
                    is PollStep.Failed -> return@launch _uiState.update { it.copy(failure = step.reason) }
                }
            }
        }
    }
}
