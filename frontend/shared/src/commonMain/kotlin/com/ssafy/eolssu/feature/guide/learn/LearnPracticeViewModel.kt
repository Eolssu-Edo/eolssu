package com.ssafy.eolssu.feature.guide.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * 기초부터 배우기 단계 연습(3~7단계). 녹음기로 소리 크기만 재고 녹음 파일은 남기지 않는다(멈추면 [AudioRecorder.cancel]).
 * 녹음기·마이크 권한은 플랫폼 구현이 있을 때만 받는다(현재 Android·iOS). 없으면 스스로 확인해 완료로 표시하게 한다.
 */
class LearnPracticeViewModel(
    stage: Int,
    private val recorder: AudioRecorder?,
    private val micPermission: MicPermission?,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LearnPracticeUiState(stage = stage, mic = if (recorder?.measuresLevel == true) LearnMicStatus.Available else LearnMicStatus.Unsupported),
    )
    val uiState: StateFlow<LearnPracticeUiState> = _uiState.asStateFlow()
    private var startJob: Job? = null
    private var levelJob: Job? = null

    /** '듣기 시작'. OS 권한을 먼저 묻고, 허용되면 녹음기를 켜 소리 크기를 잰다. */
    fun startListening() {
        val recorder = recorder ?: return
        if (_uiState.value.listening || startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            val permission = micPermission
            if (permission != null && !permission.isGranted() && !permission.request()) {
                val blocked = permission.isPermanentlyDenied()
                _uiState.update { it.micUnavailable(if (blocked) LearnMicStatus.Blocked else LearnMicStatus.Denied) }
                return@launch
            }
            if (runCatching { recorder.start() }.isFailure) {
                _uiState.update { it.micUnavailable(LearnMicStatus.Failed) }
                return@launch
            }
            recorder.level() // 시작 전까지 쌓인 크기를 버린다
            _uiState.update { it.startListening() }
            watchLevel(recorder)
        }
    }

    /** '그만하기'·화면 이탈. 녹음 파일은 지운다. */
    fun stopListening() {
        if (!_uiState.value.listening) return
        releaseRecorder()
        _uiState.update { it.stopListening() }
    }

    /** 통과한 뒤나 중간에 '처음부터'. */
    fun reset() {
        releaseRecorder()
        _uiState.update { it.reset() }
    }

    /** 거부 안내의 '설정에서 허용하기'. 열 수 없으면 false. */
    fun openMicSettings(): Boolean = micPermission?.openSettings() == true

    /** 화면에 돌아왔을 때: 설정에서 권한을 허용하고 왔으면 거부 안내를 걷는다. */
    fun refreshPermission() {
        val state = _uiState.value
        if ((state.mic == LearnMicStatus.Denied || state.mic == LearnMicStatus.Blocked) && micPermission?.isGranted() == true) {
            _uiState.update { it.copy(mic = LearnMicStatus.Available) }
        }
    }

    private fun watchLevel(recorder: AudioRecorder) {
        levelJob?.cancel()
        levelJob = viewModelScope.launch {
            var last = timeSource.markNow()
            while (isActive) {
                delay(LEVEL_INTERVAL_MS)
                // 실제로 지난 시간으로 센다(delay는 간격보다 늦게 깰 수 있다).
                val sinceMs = last.elapsedNow().inWholeMicroseconds / 1_000.0
                last = timeSource.markNow()
                // 전화·Siri처럼 시스템이 녹음을 끊었으면 더 재지 않고 멈춘다. 센 횟수는 남는다.
                if (recorder.interrupted) {
                    runCatching { recorder.cancel() }
                    _uiState.update { it.stopListening() }
                    break
                }
                val state = _uiState.updateAndGet { it.recordLevel(recorder.level(), sinceMs) }
                // 통과했거나 너무 오래 들었으면 녹음을 버리고 멈춘다(통과 상태는 그대로 남는다).
                if (state.phase == LearnPracticePhase.Passed || state.listenedMs >= LEARN_LISTEN_LIMIT_MS) {
                    runCatching { recorder.cancel() }
                    _uiState.update { it.stopListening() }
                    break
                }
            }
        }
    }

    private fun releaseRecorder() {
        startJob?.cancel()
        levelJob?.cancel()
        levelJob = null
        runCatching { recorder?.cancel() }
    }

    override fun onCleared() {
        releaseRecorder()
    }

    private companion object {
        const val LEVEL_INTERVAL_MS = 100L
    }
}
