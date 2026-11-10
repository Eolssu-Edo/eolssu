package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfKind
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PendingRecording
import com.ssafy.eolssu.data.practice.PracticeSessionRepository
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.practice.RetryOutcome
import com.ssafy.eolssu.data.practice.SendResult
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.practice.SessionStart
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
 *   녹음이 올라가면 서버가 분석을 시작하므로, 그 세션의 상태를 바로 묻는다. 녹음이 없으면(녹음기가 없는 플랫폼) 예시 분석을 맡긴다.
 * @param sessions 연습 세션 생성(PRA-01). 마친 녹음([RecordedTake])을 넘기면 세션을 만든 뒤 올린다.
 */
internal class AnalysisSession(
    private val scope: CoroutineScope,
    private val repository: EvaluationRepository,
    clock: () -> Long,
    private val sleep: suspend (Long) -> Unit,
    private val uploads: RecordingUploads? = null,
    private val sessions: PracticeSessionRepository? = null,
) {
    private class Request(val songId: String, val bpm: Int, val recordedSeconds: Int, val section: SessionSection?)

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()
    private val polling = AnalysisPolling(clock)
    private var request: Request? = null
    private var analysisId: String? = null
    private var pollJob: Job? = null
    private var paused = false
    private var recording: PendingRecording? = null
    private var take: RecordedTake? = null

    fun clearAccount() {
        pollJob?.cancel()
        request = null
        analysisId = null
        recording = null
        take = null
        _state.value = AnalysisUiState(exit = AnalysisExit.NeedsLogin)
    }

    /** 곡 조회 실패는 분석 요청 전의 실패이며, 분석 상태나 결과로 바꾸지 않는다. */
    suspend fun loadSong(
        songId: String,
        bpm: Int,
        songs: SongRepository,
        take: RecordedTake? = null,
        pendingSessionId: String? = null,
        section: SessionSection? = null,
        initialSong: Song? = null,
    ) {
        _state.update { it.copy(songLookup = SongLookup.Loading) }
        val song = try {
            val carried = initialSong?.takeIf { it.id == songId }
            if (carried != null) currentCoroutineContext()[PerformanceVisit]?.reuse(
                PerfTarget.SONG_BUNDLE, carried.id, PerfSource.NAVIGATION, carried.scoreVersion,
            )
            carried ?: songs.getSong(songId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            _state.update { it.copy(songLookup = SongLookup.Failed) }
            return
        }
        currentCoroutineContext().ensureActive()
        if (pendingSessionId != null) {
            _state.update { it.copy(song = song, songLookup = if (song == null) SongLookup.Missing else SongLookup.Ready) }
            startPending(song, bpm, pendingSessionId)
        } else start(song, bpm, take = take, section = section)
    }

    /**
     * 곡을 보이고 녹음을 분석에 맡긴다. [recording]이 있으면 먼저 올리고, 올라간 뒤에 분석을 기다린다.
     * [section]이 있으면 그 구간만 연주한 재평가 세션을 만든다(구간 반복의 ‘이 구간 재평가’).
     */
    suspend fun start(song: Song?, bpm: Int, recording: PendingRecording? = null, take: RecordedTake? = null, section: SessionSection? = null) {
        _state.update { it.copy(song = song, songLookup = if (song == null) SongLookup.Missing else SongLookup.Ready) }
        if (song == null) return
        request = Request(song.id, bpm, song.durationSeconds(bpm), section)
        val uploads = uploads
        when {
            recording != null && uploads != null -> {
                this.recording = recording
                afterSend(uploads.send(recording))
            }
            take != null && uploads != null && sessions != null -> {
                this.take = take
                createAndSend(take)
            }
            else -> requestAnalysis()
        }
    }

    /**
     * 앱을 다시 켜고 기기에 남은 세션 [sessionId]의 녹음을 보낸다. 그 녹음이 없거나(이미 보냄·지움·다른 녹음으로 바뀜)
     * 업로드 시간이 지났으면 보내지 않고 다시 연주를 안내한다.
     */
    suspend fun startPending(song: Song?, bpm: Int, sessionId: String) {
        val pending = uploads?.pending()?.takeIf { it.sessionId == sessionId }
        if (pending == null) {
            _state.update { it.copy(song = song, upload = UploadProblem.Missing) }
            return
        }
        start(song, bpm, recording = pending)
    }

    /** 세션을 만들고 녹음을 올린다. 세션을 만들지 못하면 녹음은 녹음기 자리에 남고, 다시 보내기가 같은 키로 다시 만든다. */
    private suspend fun createAndSend(take: RecordedTake) {
        val uploads = uploads ?: return
        val sessions = sessions ?: return
        val request = request ?: return
        when (val created = sessions.create(request.songId, request.bpm, take.key, request.section)) {
            is SessionStart.Created -> {
                val pending = PendingRecording(created.sessionId, take.path, created.startedAtMillis, request.songId, request.bpm, created.memberId)
                recording = pending
                afterSend(uploads.send(pending))
            }
            SessionStart.Unreachable -> _state.update { it.copy(upload = UploadProblem.CanResend, resending = false) }
            SessionStart.Rejected -> _state.update { it.copy(upload = UploadProblem.Rejected, resending = false) }
        }
    }

    /**
     * 녹음 다시 보내기. 연결 실패로 보내지 못한 이 연습 세션의 녹음이 기기에 남아 있을 때만 하며, 보내는 중에는 버튼이 돈다.
     * 그 사이 다른 녹음이 기다리게 됐으면 그 녹음을 보내지 않고 다시 연주를 안내한다.
     */
    fun resend() {
        val uploads = uploads ?: return
        val recording = recording
        val take = take
        val state = _state.value
        if (state.upload?.retryable != true || state.resending) return
        if (recording == null && take == null) return
        _state.update { it.copy(resending = true) }
        scope.launch {
            if (recording != null) afterSend(uploads.resend(recording)) else if (take != null) createAndSend(take)
        }
    }

    private suspend fun afterSend(result: SendResult) {
        when (result) {
            SendResult.Sent -> {
                _state.update { it.copy(upload = null, resending = false) }
                analysisId = recording?.sessionId
                polling.start()
                startPolling()
            }
            SendResult.CanResend -> _state.update { it.copy(upload = UploadProblem.CanResend, resending = false) }
            SendResult.TimedOut -> _state.update { it.copy(upload = UploadProblem.TimedOut, resending = false) }
            SendResult.ConnectionFailed -> _state.update { it.copy(upload = UploadProblem.ConnectionFailed, resending = false) }
            SendResult.ServerError -> _state.update { it.copy(upload = UploadProblem.ServerError, resending = false) }
            SendResult.Rejected -> _state.update { it.copy(upload = UploadProblem.Rejected, resending = false) }
            SendResult.Missing -> _state.update { it.copy(upload = UploadProblem.Missing, resending = false) }
            SendResult.Expired -> _state.update { it.copy(upload = null, resending = false, failure = AnalysisFailure.UploadTimeout) }
            SendResult.NotOwner -> _state.update { it.copy(resending = false, exit = AnalysisExit.NeedsLogin) }
        }
    }

    /**
     * 분석 다시 시도. 같은 녹음으로 다시 분석할 수 있는 실패에서만 하며, 시도 중에는 버튼이 돈다.
     * 시작했으면(이미 분석 중 포함) 상태 조회를 다시 시작하고, 다시 시도할 수 없는 상태(409)면 상태를 다시 물어 그 화면을 보인다.
     * 서버가 바쁘면(503) 실패 화면에 '잠시 후' 안내를 더하고 버튼을 둔다. 세션이 없거나(404) 로그인이 풀리면 화면을 떠난다.
     * 응답을 받지 못하면 사유가 없는 실패라 [AnalysisFailure.ServerError]로 보인다.
     */
    fun retry() {
        if (!_state.value.canRetry()) return
        _state.update { it.copy(retrying = true, retryBusy = false) }
        scope.launch {
            val id = analysisId
            if (id == null) {
                requestAnalysis()
                return@launch
            }
            val outcome = try {
                repository.retryAnalysis(id)
            } catch (e: Exception) {
                if (e is CancellationException) currentCoroutineContext().ensureActive()
                null
            }
            when (outcome) {
                RetryOutcome.Started, RetryOutcome.Conflict -> {
                    if (outcome == RetryOutcome.Started) polling.start() else polling.resume()
                    _state.update { it.copy(failure = null, retrying = false) }
                    startPolling()
                }
                RetryOutcome.Busy -> _state.update { it.copy(retrying = false, retryBusy = true) }
                RetryOutcome.NotFound -> _state.update { it.copy(retrying = false, exit = AnalysisExit.SessionGone) }
                RetryOutcome.Unauthorized -> _state.update { it.copy(retrying = false, exit = AnalysisExit.NeedsLogin) }
                null -> _state.update { it.copy(failure = AnalysisFailure.ServerError, retrying = false) }
            }
        }
    }

    /** 화면이 백그라운드로 갔다. 서버 분석은 계속되고, 앱은 돌아올 때까지 묻지 않는다. */
    fun pause() {
        paused = true
        pollJob?.cancel()
    }

    /** 화면에 돌아왔다. 아직 분석 중이면 즉시 조회하고 기존 조회 주기를 이어 간다. */
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
        polling.start()
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
        val perf = scope.coroutineContext[PerformanceVisit]
        pollJob = scope.launch(perf.context(PerfReason.POLL)) {
            val started = PerformanceLog.now()
            var count = 0
            var failed = 0
            var outcome = PerfReason.CANCELLED
            try {
                while (true) {
                    val status = queryStatus(id)
                    count++
                    if (status == null) failed++
                    val step = status?.let(polling::onStatus) ?: polling.onNoResponse()
                    when (step) {
                        is PollStep.Wait -> sleep(step.millis)
                        is PollStep.Done -> {
                            outcome = PerfReason.READY
                            _state.update { it.copy(evaluationId = step.evaluationId) }
                            return@launch
                        }
                        is PollStep.Failed -> {
                            outcome = PerfReason.FAILED
                            _state.update { it.copy(failure = step.reason) }
                            return@launch
                        }
                    }
                }
            } finally {
                perf?.event(PerfKind.POLL_SUMMARY, PerfTarget.PRACTICE_STATUS,
                    durationMs = PerformanceLog.now() - started, count = count, failed = failed, outcome = outcome)
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
