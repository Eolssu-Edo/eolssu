package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * PRACTICE-01. 소리·녹음 없이 정간 길이만큼 기다리며 위치를 옮긴다(와이어프레임 타이머와 같은 방식).
 * 상태를 바꾸는 규칙은 `PracticeUiState`의 전환 함수에 두고, 여기서는 타이머를 켜고 끈다.
 */
class PracticeViewModel(
    songId: String,
    solo: Boolean,
    songRepository: SongRepository,
    private val settingsStore: PracticeSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeUiState(solo = solo, settings = settingsStore.settings.value))
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var stepJob: Job? = null
    private var countdownJob: Job? = null
    private var previewJob: Job? = null
    private var listenJob: Job? = null

    init {
        viewModelScope.launch { _uiState.update { it.copy(song = songRepository.getSong(songId)) } }
        viewModelScope.launch { settingsStore.settings.collect { s -> _uiState.update { it.copy(settings = s) } } }
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
        val state = _uiState.value
        val song = state.song ?: return
        val running = stepJob?.isActive == true
        val carry = currentFraction()
        settingsStore.update { it.applyTempo(state.draft, song) }
        _uiState.update { it.copy(settings = settingsStore.settings.value) }
        closePanel()
        if (running) startStepping(carry)
    }

    fun toggleBigScore() = settingsStore.update { it.copy(bigScore = !it.bigScore) }

    /** 장단 미리듣기: 한 정간마다 격자의 칸을 옮긴다. */
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
        startStepping()
    }

    fun toggleModel() {
        val wasPlaying = _uiState.value.playing
        if (wasPlaying) stepJob?.cancel()
        _uiState.update { it.toggleModel() }
        if (!wasPlaying) startStepping()
    }

    fun restartModel() {
        _uiState.update { it.restartModel() }
        if (_uiState.value.playing) startStepping()
    }

    fun stopModel() {
        stepJob?.cancel()
        _uiState.update { it.stopModel() }
    }

    // 평가 연주

    fun requestEvaluation() {
        stepJob?.cancel()
        _uiState.update { it.requestEvaluation(settingsStore.micGranted.value) }
        if (_uiState.value.mode == PracticeMode.Countdown) runCountdown()
    }

    /** 마이크 안내에서 '계속'. 실제 권한 요청은 녹음을 붙일 때 한다. */
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
        val fraction = currentFraction()
        stepJob?.cancel()
        _uiState.update { it.askAbort(fraction) }
    }

    /** '계속 연주': 3·2·1 뒤 멈춘 자리부터. */
    fun resumeRecording() {
        _uiState.update { it.beginCountdown(resume = true) }
        runCountdown()
    }

    fun askRestart() = _uiState.update { it.copy(popup = PracticePopup.Restart) }

    fun restartNow() {
        stepJob?.cancel()
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

    fun stopAll() {
        stepJob?.cancel()
        countdownJob?.cancel()
        previewJob?.cancel()
        listenJob?.cancel()
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
            val carry = _uiState.value.pausedFraction
            _uiState.update { if (resume) it.resumeRecording() else it.beginRecording() }
            startStepping(if (resume) carry else 0f)
        }
    }

    /** 현재 음을 지난 비율(0~1). */
    private fun currentFraction(): Float {
        val state = _uiState.value
        val started = state.eventStartedAt ?: return state.pausedFraction
        val duration = state.eventDurationMs()
        if (duration <= 0.0) return 0f
        return (started.elapsedNow().inWholeMilliseconds / duration).toFloat().coerceIn(0f, 1f)
    }

    /** 현재 음부터 정간 길이만큼 기다리며 다음 음으로 옮긴다. [carry]는 현재 음을 이미 지난 비율. */
    private fun startStepping(carry: Float = 0f) {
        stepJob?.cancel()
        stepJob = viewModelScope.launch {
            var fraction = carry
            while (true) {
                val duration = _uiState.value.eventDurationMs()
                val startedAt = TimeSource.Monotonic.markNow() - (duration * fraction).milliseconds
                _uiState.update { it.copy(eventStartedAt = startedAt, pausedFraction = fraction) }
                val remaining = duration * (1 - fraction)
                delay(remaining.toLong())
                _uiState.update { it.advance(remaining, micLevel = 0.3f + Random.nextFloat() * 0.7f) }
                fraction = 0f
                val next = _uiState.value
                val running = (next.mode == PracticeMode.Model && next.playing) || (next.mode == PracticeMode.Recording && !next.finished)
                if (!running) break
            }
        }
    }

    override fun onCleared() {
        stopAll()
    }
}
