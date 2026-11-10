package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

/**
 * PRACTICE-03. 들어오면 3·2·1을 센 뒤 구간을 반복해 따라간다. 멈췄다가 ‘반복 계속’을 누르면 다시 3·2·1(1초마다)을 센다.
 * 반복에는 녹음·소리가 없다. ‘구간 재평가’는 마이크를 확인하고 3·2·1 뒤 구간을 한 번 따라가며 [recorder]로 녹음하며,
 * 마치면 [SectionLoopUiState.take]로 분석에 넘긴다(바탕 세션 [sourceId]와 구간 음 번호를 함께). 화면을 떠나면 녹음을 버린다.
 *
 * @param recorder 녹음기. 없는 플랫폼(웹)에서는 재평가를 시작하지 못한다고 알린다.
 * @param micPermission OS 마이크 권한. 없으면 허용된 것으로 본다.
 * @param newKey 녹음 하나의 `Idempotency-Key`(세션 생성 응답을 놓쳐 다시 만들 때 같은 키).
 * @param settingsStore 연습 화면의 악보 설정(크게 보기·한글·계이름). 구간 반복 악보도 기본 평가 화면과 같은 모양으로 그린다.
 */
class SectionLoopViewModel(
    private val sourceId: String,
    private val section: ScoreSection,
    private val bpm: Int,
    private val repeats: Int?,
    private val repository: WeakSectionRepository,
    private val recorder: AudioRecorder? = null,
    private val micPermission: MicPermission? = null,
    private val newKey: () -> String = { Uuid.random().toString() },
    private val settingsStore: PracticeSettingsStore? = null,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionLoopUiState(section = section, bpm = bpm, repeats = repeats))
    val uiState: StateFlow<SectionLoopUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        settingsStore?.let { store ->
            viewModelScope.launch { store.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } } }
        }
        viewModelScope.launch(perf.context()) {
            val source = repository.source(sourceId)
            _uiState.update {
                it.copy(
                    loading = false,
                    song = source?.song,
                    cells = source?.let { s -> section.positions(s.song.score) }.orEmpty(),
                )
            }
            if (source != null) startCountdown()
        }
    }

    /** ‘반복 멈춤’ · ‘반복 계속’(마친 뒤에는 ‘다시 연습’). 재평가 녹음 중에는 녹음을 그만둔다. */
    fun togglePause() {
        val state = _uiState.value
        when {
            state.recording -> cancelRecording()
            state.running || state.countdown != null -> {
                job?.cancel()
                _uiState.update { it.pause() }
            }
            else -> startCountdown()
        }
    }

    /** 화면을 떠나거나 앱이 가려졌다. 반복은 멈추고, 녹음 중이면 버린다. */
    fun stop() {
        if (_uiState.value.recording) {
            cancelRecording()
        } else {
            job?.cancel()
            _uiState.update { it.pause() }
        }
    }

    /** ‘구간 재평가’: 마이크를 확인하고 구간을 한 번 녹음한다. */
    fun startEvaluation() {
        val state = _uiState.value
        val song = state.song ?: return
        if (state.recording) return
        job?.cancel()
        _uiState.update { it.pause() }
        val recorder = recorder ?: return notice(SectionLoopNotice.NoRecorder)
        val notes = noteRange(song, section) ?: return notice(SectionLoopNotice.NoNotes)
        job = viewModelScope.launch {
            val granted = micPermission?.let { it.isGranted() || it.request() } ?: true
            if (!granted) {
                notice(if (micPermission?.isPermanentlyDenied() == true) SectionLoopNotice.MicBlocked else SectionLoopNotice.MicDenied)
                return@launch
            }
            record(recorder, SessionSection(notes.first, notes.last, sourceId))
        }
    }

    /** 화면이 녹음을 분석으로 넘겼다. */
    fun takeConsumed() = _uiState.update { it.copy(take = null) }

    fun noticeShown() = _uiState.update { it.copy(notice = null) }

    /** 마이크 설정을 연다. 열 수 없으면 false. */
    fun openMicSettings(): Boolean = micPermission?.openSettings() == true

    private suspend fun record(recorder: AudioRecorder, sessionSection: SessionSection) {
        val key = newKey()
        _uiState.update { it.beginRecording() }
        while (currentCoroutineContext().isActive && _uiState.value.countdown != null) {
            delay(CountdownTickMillis)
            _uiState.update { it.countdownTick() }
        }
        if (runCatching { recorder.start() }.isFailure) {
            _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
            return
        }
        while (currentCoroutineContext().isActive && _uiState.value.running) {
            delay(_uiState.value.cellMillis.roundToLong().coerceAtLeast(1))
            if (recorder.interrupted) {
                // 전화 등으로 시스템이 녹음을 끊었다. 끊긴 녹음은 보내지 않는다.
                recorder.cancel()
                _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
                return
            }
            _uiState.update { it.advance() }
        }
        val audio = runCatching { recorder.stop() }.getOrNull()
        val take = audio?.takeUnless { it.tooLarge }?.let { RecordedTake(it.path, key) }
        if (take == null) {
            _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
            return
        }
        _uiState.update { it.endRecording().copy(take = SectionTake(take, sessionSection)) }
    }

    private fun cancelRecording() {
        job?.cancel()
        recorder?.cancel()
        _uiState.update { it.endRecording() }
    }

    private fun notice(notice: SectionLoopNotice) = _uiState.update { it.copy(notice = notice) }

    /** 반복을 3·2·1부터 시작한다. 재평가 녹음 중에는 녹음을 끊지 않도록 시작하지 않는다. */
    private fun startCountdown() {
        if (_uiState.value.recording) return
        job?.cancel()
        _uiState.update { it.beginCountdown() }
        job = viewModelScope.launch {
            while (isActive && _uiState.value.countdown != null) {
                delay(CountdownTickMillis)
                _uiState.update { it.countdownTick() }
            }
            while (isActive && _uiState.value.running) {
                delay(_uiState.value.cellMillis.roundToLong().coerceAtLeast(1))
                _uiState.update { it.advance() }
            }
        }
    }

    override fun onCleared() {
        if (_uiState.value.recording) recorder?.cancel()
    }
}
