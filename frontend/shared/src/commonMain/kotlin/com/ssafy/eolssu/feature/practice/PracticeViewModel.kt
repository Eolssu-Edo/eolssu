package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * PRACTICE-01. 정간 길이만큼 기다리며 위치를 옮긴다(와이어프레임 타이머와 같은 방식).
 * 상태를 바꾸는 규칙은 `PracticeUiState`의 전환 함수에 두고, 여기서는 타이머와 녹음을 켜고 끈다.
 *
 * 평가 연주는 [recorder]로 녹음한다. 연주 중단 확인 창·이어 연주 카운트다운 동안은 일시정지하고,
 * 끊기거나 버린 연주의 녹음은 지운다. [recorder]가 없는 플랫폼(iOS·Web)은 녹음 없이 진행만 한다.
 * 입력 없음 안내·팝업은 [AudioRecorder.measuresLevel]인 녹음기가 있을 때만 띄우고, 나머지는 서버 판정에 맡긴다.
 * 마이크 권한은 앱 안의 '허용'과 [micPermission](OS 권한)이 모두 있어야 있는 것으로 본다.
 *
 * 화면이 보이는 동안 [timeMeter]로 연습 시간을 잰다. 재생·녹음·장단 미리듣기 상태는 화면 상태에서 읽어 넘기고,
 * 입력·화면 보임은 Route가 알린다. 열린 구간은 30초마다, 닫힌 구간은 닫을 때 [timeQueue]에 남긴다.
 */
class PracticeViewModel(
    private val songId: String,
    solo: Boolean,
    private val songRepository: SongRepository,
    private val settingsStore: PracticeSettingsStore,
    private val timeMeter: PracticeTimeMeter,
    private val timeQueue: PracticeTimeQueue,
    private val recorder: AudioRecorder? = null,
    private val micPermission: MicPermission? = null,
    private val playbackTimeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PracticeUiState(solo = solo, settings = settingsStore.settings.value, inputMeasured = recorder?.measuresLevel == true, isSongLoading = true),
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var stepJob: Job? = null
    private var stepClock: PracticeTimelineClock? = null
    private var countdownJob: Job? = null
    private var previewJob: Job? = null
    private var listenJob: Job? = null
    private var levelJob: Job? = null
    private var idleJob: Job? = null
    private var snapshotJob: Job? = null
    private var songJob: Job? = null
    private var screenShown = false
    private val recordingClock = RecordingClock()

    init {
        retryLoadSong()
        viewModelScope.launch { settingsStore.settings.collect { s -> _uiState.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            _uiState.map { it.runningActivities }.distinctUntilChanged().collect { running ->
                ContinuousActivity.entries.forEach { timeMeter.setRunning(it, it in running) }
            }
        }
    }

    /** 화면 재구성은 요청을 반복하지 않는다. 실패 후 재시도만 가능하며 취소는 오류로 바꾸지 않는다. */
    fun retryLoadSong() {
        if (songJob?.isActive == true || _uiState.value.song != null) return
        _uiState.update { it.copy(isSongLoading = true, songError = null) }
        songJob = viewModelScope.launch {
            try {
                val song = songRepository.getSong(songId)
                when {
                    song == null -> _uiState.update { it.copy(songError = PracticeSongError.NotFound) }
                    song.timeline.isEmpty() -> _uiState.update { it.copy(songError = PracticeSongError.Failed) }
                    else -> {
                        _uiState.update { it.copy(song = song) }
                        if (screenShown) startTimeTracking()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(songError = PracticeSongError.Failed) }
            } finally {
                _uiState.update { it.copy(isSongLoading = false) }
            }
        }
    }

    // 연습 시간

    /** 화면이 앞에 보임(들어옴·앱으로 돌아옴·탭이 다시 보임). 새 구간을 열고 알림 때를 기다린다. */
    fun onScreenShown() {
        screenShown = true
        if (_uiState.value.song == null) return
        startTimeTracking()
    }

    private fun startTimeTracking() {
        timeMeter.show(songId)
        watchIdle()
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            while (true) {
                delay(PracticeTimeMeter.SnapshotInterval)
                timeMeter.snapshot()?.let(timeQueue::saveOpen)
            }
        }
    }

    /** 화면이 가려짐(떠남·백그라운드·화면 잠금·탭 숨김). 구간을 닫아 보내고 떠 있던 알림을 내린다. */
    fun onScreenHidden() {
        screenShown = false
        idleJob?.cancel()
        snapshotJob?.cancel()
        timeQueue.close(timeMeter.hide())
        _uiState.update { it.copy(idleReminder = false) }
    }

    /** 터치·스크롤·키 입력. */
    fun onUserActivity() = timeMeter.onActivity()

    /** "계속 연습 중인가요?"에 응답. 활동으로 보고 자리 비움 시간을 다시 센다. */
    fun keepPracticing() {
        timeMeter.onActivity()
        _uiState.update { it.copy(idleReminder = false) }
    }

    /** 알림 때까지 기다렸다가 띄운다. 그사이 활동이 있었으면 남은 시간이 늘어 있으므로 다시 기다린다. */
    private fun watchIdle() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            while (true) {
                delay(timeMeter.untilReminder() ?: IDLE_RECHECK)
                if (timeMeter.takeReminder()) _uiState.update { it.copy(idleReminder = true) }
            }
        }
    }

    // 악보·패널

    fun selectEvent(index: Int) = _uiState.update { it.selectEvent(index) }

    fun openPanel(panel: PracticePanel) {
        stopPreview()
        _uiState.update { it.openPanel(panel) }
    }

    fun closePanel() {
        stopPreview()
        listenJob?.cancel()
        _uiState.update { it.closePanel() }
    }

    fun updateDraft(transform: (PracticeDraft) -> PracticeDraft) = _uiState.update { it.copy(draft = transform(it.draft)) }

    fun moveFingering(delta: Int) = _uiState.update { it.moveFingering(delta) }

    /** 음 듣기: 소리는 아직 없고 1.2초 동안 재생 중으로 보인다. */
    fun listen(yul: Yul) {
        listenJob?.cancel()
        _uiState.update { it.copy(listening = yul) }
        listenJob = viewModelScope.launch {
            delay(1200)
            _uiState.update { it.copy(listening = null) }
        }
    }

    fun applyNotation() {
        val state = _uiState.value
        val song = state.song ?: return
        settingsStore.update { it.applyNotation(state.draft, song) }
        closePanel()
    }

    fun applyJangdan() {
        settingsStore.update { it.applyJangdan(_uiState.value.draft) }
        closePanel()
    }

    /** 모범 연주 중에 속도를 바꾸면 현재 음의 남은 몫부터 새 속도로 이어 간다. */
    fun applyTempo() {
        captureStepProgress()
        val state = _uiState.value
        val song = state.song ?: return
        val running = stepJob?.isActive == true
        val carry = state.currentEventFraction
        settingsStore.update { it.applyTempo(state.draft, song) }
        _uiState.update {
            val updated = it.copy(settings = settingsStore.settings.value)
            updated.copy(eventElapsedMs = updated.eventDurationMs() * carry, pausedFraction = carry.toFloat())
        }
        closePanel()
        if (running) startStepping(_uiState.value.eventElapsedMs)
    }

    fun toggleBigScore() = settingsStore.update { it.copy(bigScore = !it.bigScore) }

    /** 장단 미리듣기: 구음 패턴 한 칸마다 옮긴다. 악보의 정간 길이와는 다르다. */
    fun togglePreview() {
        val state = _uiState.value
        if (state.draft.previewing) {
            stopPreview()
            return
        }
        val song = state.song ?: return
        _uiState.update { it.copy(draft = it.draft.copy(previewing = true, previewBeat = 0)) }
        previewJob = viewModelScope.launch {
            val gak = song.jangdan.gak
            while (true) {
                delay(song.jangdan.cellMillis(_uiState.value.bpm).toLong())
                _uiState.update { it.copy(draft = it.draft.copy(previewBeat = (it.draft.previewBeat + 1) % gak)) }
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        _uiState.update { it.copy(draft = it.draft.copy(previewing = false, previewBeat = 0)) }
    }

    fun stopFollowing() = _uiState.update { it.copy(follow = false) }

    fun followNow() = _uiState.update { it.copy(follow = true) }

    // 모범 연주

    fun startModel() {
        _uiState.update { it.startModel() }
        startStepping(_uiState.value.eventElapsedMs)
    }

    fun toggleModel() {
        val wasPlaying = _uiState.value.playing
        if (wasPlaying) {
            captureStepProgress()
            stepJob?.cancel()
        }
        _uiState.update { it.toggleModel() }
        if (!wasPlaying) startStepping(_uiState.value.eventElapsedMs)
    }

    fun restartModel() {
        _uiState.update { it.restartModel() }
        if (_uiState.value.playing) startStepping()
    }

    fun stopModel() {
        captureStepProgress()
        stepJob?.cancel()
        _uiState.update { it.stopModel() }
    }

    // 평가 연주

    fun requestEvaluation() {
        stepJob?.cancel()
        val granted = settingsStore.micGranted.value && micPermission?.isGranted() != false
        _uiState.update { it.requestEvaluation(granted) }
        if (_uiState.value.mode == PracticeMode.Countdown) runCountdown()
    }

    /** 마이크 안내에서 '계속'을 누르면 OS 권한을 묻는다. 허용하면 true. */
    suspend fun requestMicPermission(): Boolean = micPermission?.request() ?: true

    /** OS 권한까지 허용된 뒤 연주를 시작한다. */
    fun grantMicAndStart() {
        settingsStore.grantMic()
        _uiState.update { it.beginCountdown() }
        runCountdown()
    }

    fun closePopup() = _uiState.update { it.copy(popup = null) }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.cancelCountdown() }
    }

    fun askAbort() {
        captureStepProgress()
        val state = _uiState.value
        stepJob?.cancel()
        pauseRecorder()
        _uiState.update { it.askAbort(state.currentEventFraction.toFloat(), state.eventElapsedMs) }
    }

    /** '계속 연주': 3·2·1 뒤 멈춘 자리부터. */
    fun resumeRecording() {
        _uiState.update { it.beginCountdown(resume = true) }
        runCountdown()
    }

    fun askRestart() = _uiState.update { it.copy(popup = PracticePopup.Restart) }

    fun restartNow() {
        stepJob?.cancel()
        cancelRecorder()
        _uiState.update { it.beginCountdown() }
        runCountdown()
    }

    fun toReady() {
        stopAll()
        _uiState.update { it.toReady() }
    }

    /** 뒤로 가기. 연주·카운트다운 중이면 나갈지 묻고 false를 돌려준다. */
    fun onBack(): Boolean {
        val mode = _uiState.value.mode
        if (mode == PracticeMode.Recording || mode == PracticeMode.Countdown) {
            _uiState.update { it.copy(popup = PracticePopup.Exit) }
            return false
        }
        stopAll()
        return true
    }

    /** 화면을 벗어남(앱 전환·전화 등). 연주 중이면 끊긴 것으로 처리하고, 모범 연주는 일시정지한다. */
    fun onLeaveScreen() {
        val state = _uiState.value
        when (state.mode) {
            PracticeMode.Recording, PracticeMode.Countdown -> {
                stopAll()
                _uiState.update { it.interrupt() }
            }
            PracticeMode.Model -> if (state.playing) toggleModel()
            else -> Unit
        }
    }

    /** 타이머를 모두 멈추고 진행 중인 녹음을 버린다. 마친 녹음 파일은 남긴다. */
    fun stopAll() {
        captureStepProgress()
        stepJob?.cancel()
        stepClock = null
        _uiState.update { it.copy(eventStartedAt = null, pausedFraction = it.currentEventFraction.toFloat()) }
        countdownJob?.cancel()
        previewJob?.cancel()
        listenJob?.cancel()
        cancelRecorder()
    }

    private fun runCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.count > 1) {
                delay(1000)
                _uiState.update { it.copy(count = it.count - 1) }
            }
            delay(1000)
            val resume = _uiState.value.resume
            val carry = _uiState.value.eventElapsedMs
            if (!(if (resume) resumeRecorder() else startRecorder())) {
                _uiState.update { it.failRecording() }
                return@launch
            }
            _uiState.update { if (resume) it.resumeRecording() else it.beginRecording() }
            startStepping(if (resume) carry else 0.0)
        }
    }

    // 녹음

    private fun startRecorder(): Boolean {
        val recorder = recorder ?: return true
        if (runCatching { recorder.start() }.isFailure) return false
        recordingClock.start()
        watchRecording()
        return true
    }

    private fun resumeRecorder(): Boolean {
        val recorder = recorder ?: return true
        if (runCatching { recorder.resume() }.isFailure) {
            cancelRecorder()
            return false
        }
        recordingClock.resume()
        watchRecording()
        return true
    }

    private fun pauseRecorder() {
        val recorder = recorder ?: return
        levelJob?.cancel()
        recordingClock.pause()
        runCatching { recorder.pause() }
        _uiState.update { it.copy(micLevel = 0f) }
    }

    private fun cancelRecorder() {
        levelJob?.cancel()
        recordingClock.reset()
        recorder?.cancel()
    }

    /** 곡 끝이나 최대 길이에서 녹음을 마치고 저장된 파일을 확인한다. */
    private fun finishRecorder() {
        val recorder = recorder ?: return
        levelJob?.cancel()
        recordingClock.reset()
        val audio = recorder.stop()
        _uiState.update { it.checkRecording(saved = audio != null, tooLarge = audio?.tooLarge == true) }
    }

    /** 녹음하는 동안 입력 크기와 무입력 시간을 반영하고, 최대 길이(10분)에 닿으면 연주를 마친다. */
    private fun watchRecording() {
        val recorder = recorder ?: return
        levelJob?.cancel()
        levelJob = viewModelScope.launch {
            var measuredAt = recordingClock.elapsed()
            while (isActive) {
                val elapsed = recordingClock.elapsed()
                if (elapsed >= RecordingSpec.MaxDuration) {
                    captureStepProgress()
                    stepJob?.cancel()
                    _uiState.update { it.endRecording() }
                    finishRecorder()
                    break
                }
                val level = recorder.level()
                val sinceMs = (elapsed - measuredAt).inWholeMilliseconds.toDouble()
                measuredAt = elapsed
                _uiState.update { it.recordLevel(level, sinceMs) }
                delay(LEVEL_INTERVAL_MS)
            }
        }
    }

    /** 일시정지/중단/속도 변경 직전 마지막 UI 갱신 이후의 경과분도 보존한다. */
    private fun captureStepProgress() {
        val clock = stepClock ?: return
        if (_uiState.value.eventStartedAt == null) return
        val sample = clock.sample()
        _uiState.update {
            it.copy(eventElapsedMs = sample.eventElapsedMs.coerceIn(0.0, it.eventDurationMs()), elapsedMs = sample.elapsedMs)
        }
    }

    /** 원본 이벤트는 끝에서만 옮기고, 박/정간/진행 시간은 긴 이벤트 안에서도 경과 시각으로 갱신한다. */
    private fun startStepping(carryMs: Double = 0.0) {
        stepJob?.cancel()
        val clock = PracticeTimelineClock(playbackTimeSource, carryMs, _uiState.value.elapsedMs)
        stepClock = clock
        stepJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val state = _uiState.value
                    val duration = state.eventDurationMs()
                    if (duration <= 0.0) break
                    val sample = clock.sample()
                    // TimeSource는 ns 정밀도다. 1ns보다 작은 Double 나눗셈 오차로 다음 ms까지 기다리지 않는다.
                    if (sample.eventElapsedMs + 0.000001 >= duration) {
                        val endElapsedMs = sample.elapsedMs - (sample.eventElapsedMs - duration)
                        _uiState.update {
                            it.copy(eventElapsedMs = duration, elapsedMs = endElapsedMs)
                                .advance(0.0, micLevel = if (recorder != null) it.micLevel else 0.3f + Random.nextFloat() * 0.7f)
                        }
                        clock.completeEvent(duration)
                        val next = _uiState.value
                        if (next.mode == PracticeMode.Recording && next.finished) finishRecorder()
                        val running = (next.mode == PracticeMode.Model && next.playing) || (next.mode == PracticeMode.Recording && !next.finished)
                        if (!running) break
                    } else {
                        _uiState.update { it.copy(eventElapsedMs = sample.eventElapsedMs.coerceAtLeast(0.0),
                            elapsedMs = sample.elapsedMs, eventStartedAt = clock.eventStartedAt) }
                        delay(_uiState.value.nextTimelineSampleMs().milliseconds)
                    }
                }
            } finally {
                if (stepClock === clock) stepClock = null
            }
        }
    }

    override fun onCleared() {
        stopAll()
    }

    private companion object {
        const val LEVEL_INTERVAL_MS = 100L

        /** 알림을 기다리지 않는 동안(재생 중·이미 알림을 냄) 다시 확인하는 간격. */
        val IDLE_RECHECK = 30.seconds
    }
}
