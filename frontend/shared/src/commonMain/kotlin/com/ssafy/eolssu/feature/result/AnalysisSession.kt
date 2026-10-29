package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PendingRecording
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.practice.SendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 분석 하나의 요청·상태 조회·재시도와 화면의 백그라운드 전환(PRA-03 상태 조회와 시간 초과).
 * 다음 단계는 [AnalysisPolling]이 정하고, 여기서는 조회·대기·멈춤만 돌린다.
 *
 * 남은 일: 대기 화면을 떠났다(뒤로 가기) 돌아오면 새 분석을 맡긴다. 이미 맡긴 분석을 다시 묻는 것은 상태 조회 API가 정해진 뒤 다룬다.
 *
 * @param sleep 조회 사이의 기다림. 테스트는 가짜 시계를 넘기는 함수를 넣는다.
 * @param uploads 녹음 업로드. [start]에 녹음을 넘기면 먼저 보내고, 보내지 못하면 '녹음 다시 보내기'를 준다(PRA-03).
 *   실제 업로드가 붙기 전에는 화면이 녹음을 넘기지 않아 이 단계를 건너뛴다.
 */
internal class AnalysisSession(
    private val scope: CoroutineScope,
    private val repository: EvaluationRepository,
    clock: () -> Long,
    private val sleep: suspend (Long) -> Unit,
    private val uploads: RecordingUploads? = null,
) {
    private class Request(val songId: String, val bpm: Int, val recordedSeconds: Int)

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()
    private val polling = AnalysisPolling(clock)
    private var request: Request? = null
    private var analysisId: String? = null
    private var pollJob: Job? = null
    private var paused = false

    /** 곡을 보이고 녹음을 분석에 맡긴다. [recording]이 있으면 먼저 올리고, 올라간 뒤에 분석을 기다린다. */
    suspend fun start(song: Song?, bpm: Int, recording: PendingRecording? = null) {
        _state.update { it.copy(song = song) }
        if (song == null) return
        request = Request(song.id, bpm, song.durationSeconds(bpm))
        val uploads = uploads
        if (recording != null && uploads != null) {
            afterSend(uploads.send(recording))
        } else {
            requestAnalysis()
        }
    }

    /** 녹음 다시 보내기. 연결 실패로 보내지 못한 녹음이 기기에 남아 있을 때만 하며, 보내는 중에는 버튼이 돈다. */
    fun resend() {
        val uploads = uploads ?: return
        val state = _state.value
        if (state.upload != UploadProblem.CanResend || state.resending) return
        _state.update { it.copy(resending = true) }
        scope.launch { afterSend(uploads.resend()) }
    }

    private suspend fun afterSend(result: SendResult) {
        when (result) {
            SendResult.Sent -> {
                _state.update { it.copy(upload = null, resending = false) }
                requestAnalysis()
            }
            SendResult.CanResend -> _state.update { it.copy(upload = UploadProblem.CanResend, resending = false) }
            SendResult.Rejected -> _state.update { it.copy(upload = UploadProblem.Rejected, resending = false) }
            SendResult.Missing -> _state.update { it.copy(upload = UploadProblem.Missing, resending = false) }
            SendResult.Expired -> _state.update { it.copy(upload = null, resending = false, failure = AnalysisFailure.UploadTimeout) }
        }
    }

    /** 분석 다시 시도. 같은 녹음으로 다시 분석할 수 있는 실패에서만 하며, 시도 중에는 버튼이 돈다. */
    fun retry() {
        if (!_state.value.canRetry()) return
        _state.update { it.copy(retrying = true) }
        scope.launch {
            val id = analysisId
            if (id == null) {
                requestAnalysis()
            } else if (attempt { repository.retryAnalysis(id) }) {
                startPolling()
            }
        }
    }

    /** 화면이 백그라운드로 갔다. 서버 분석은 계속되고, 앱은 돌아올 때까지 묻지 않는다. */
    fun pause() {
        paused = true
        pollJob?.cancel()
    }

    /** 화면에 돌아왔다. 아직 분석 중이면 바로 한 번 묻고, `PENDING`이면 2초 조회를 다시 시작한다. */
    fun resume() {
        paused = false
        val state = _state.value
        if (analysisId == null || state.failure != null || state.evaluationId != null || pollJob?.isActive == true) return
        polling.resume()
        startPolling()
    }

    private suspend fun requestAnalysis() {
        val request = request ?: return
        val requested = attempt {
            analysisId = repository.requestAnalysis(request.songId, request.bpm, request.recordedSeconds)
        }
        if (requested) startPolling()
    }

    /** [block]을 부르고 성공하면 기다리는 상태로 돌린다. 실패하면 사유가 없는 실패라 [AnalysisFailure.ServerError]로 본다. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        polling.resume()
        _state.update { it.copy(failure = null, retrying = false) }
        true
    } catch (e: Exception) {
        if (e is CancellationException) currentCoroutineContext().ensureActive()
        _state.update { it.copy(failure = AnalysisFailure.ServerError, retrying = false) }
        false
    }

    /** 백그라운드에 있으면 시작하지 않는다. 돌아오면 [resume]이 시작한다. */
    private fun startPolling() {
        val id = analysisId ?: return
        if (paused) return
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val step = queryStatus(id)?.let(polling::onStatus) ?: polling.onNoResponse()
                when (step) {
                    is PollStep.Wait -> sleep(step.millis)
                    is PollStep.Done -> {
                        _state.update { it.copy(evaluationId = step.evaluationId) }
                        return@launch
                    }
                    is PollStep.Failed -> {
                        _state.update { it.copy(failure = step.reason) }
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * 남은 시간 안에 받은 상태. 연결 오류·서버 오류·저장소가 던진 취소와 남은 시간 안에 오지 않은 응답은 모두
     * 응답 없음(null)으로 보고, 70초 동안 이어지면 [AnalysisFailure.Timeout]이 된다. 상태 조회 API가 정해지면 오류별 처리를 나눈다.
     * 남은 시간은 [sleep]으로 재서 테스트의 가짜 시계로도 끝난다.
     */
    private suspend fun queryStatus(id: String): AnalysisStatus? = coroutineScope {
        val remaining = polling.remainingMillis()
        if (remaining == 0L) return@coroutineScope null
        val query = async {
            try {
                repository.analysisStatus(id)
            } catch (e: Exception) {
                if (e is CancellationException) currentCoroutineContext().ensureActive()
                null
            }
        }
        val limit = launch {
            sleep(remaining)
            query.cancel()
        }
        try {
            query.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            limit.cancel()
        }
    }
}
