package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * ANALYSIS-01 상태. 분석이 끝나면 [evaluationId]가 채워져 결과 화면으로 간다. 실패하면 [failure]에 사유가 있다.
 * 녹음을 보내지 못하면 분석 전이라 [failure] 대신 [upload]에 사유가 있다.
 */
data class AnalysisUiState(
    val song: Song? = null,
    val failure: AnalysisFailure? = null,
    val retrying: Boolean = false,
    val evaluationId: String? = null,
    val upload: UploadProblem? = null,
    val resending: Boolean = false,
)

/** 녹음을 보내지 못한 사유. 세션 업로드 시간이 지난 410은 [AnalysisFailure.UploadTimeout]으로 보인다. */
enum class UploadProblem {
    /** 연결 실패나 503. 녹음이 기기에 남아 있어 '녹음 다시 보내기'를 준다. */
    CanResend,

    /** 413·415·422. 같은 파일은 다시 보내도 거부되므로 '다시 연주하기'만 준다. */
    Rejected,
}

/** 같은 녹음으로 다시 분석할 수 있는 실패이고, 다시 시도하는 중이 아닌지. */
internal fun AnalysisUiState.canRetry(): Boolean = !retrying && failure?.retryable == true

/** 녹음을 분석에 맡기고 2초마다 상태를 묻는다. 동작은 [AnalysisSession]에 있다. */
class AnalysisViewModel(
    songId: String,
    bpm: Int,
    songRepository: SongRepository,
    evaluationRepository: EvaluationRepository,
) : ViewModel() {
    private val session = AnalysisSession(
        viewModelScope,
        evaluationRepository,
        clock = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } },
        sleep = { delay(it) },
    )
    val uiState: StateFlow<AnalysisUiState> = session.state

    init {
        viewModelScope.launch { session.start(songRepository.getSong(songId), bpm) }
    }

    /** 분석 다시 시도. */
    fun retry() = session.retry()

    /** 녹음 다시 보내기. */
    fun resend() = session.resend()

    /** 화면이 백그라운드로 갔다. */
    fun pause() = session.pause()

    /** 화면에 돌아왔다. */
    fun resume() = session.resume()
}
