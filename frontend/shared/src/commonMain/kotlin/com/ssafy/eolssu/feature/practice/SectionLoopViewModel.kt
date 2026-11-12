package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource
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
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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
 * @param autoStart 들어오자마자 3·2·1 뒤 반복을 시작하는지. 구간 연습·구간 재평가 화면(S15P31E102-776)은 연습·연주 평가 화면처럼
 *   누를 때까지 기다리므로 false로 만든다.
 * @param jangdanPlayer 장단 소리. 설정의 ‘장단 소리’를 켜면 반복·재평가 녹음과 함께 치고, 장단 패널의 ‘장단 듣기’에도 쓴다.
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
    private val autoStart: Boolean = true,
    jangdanPlayer: JangdanSamplePlayer? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionLoopUiState(section = section, bpm = bpm, repeats = repeats))
    val uiState: StateFlow<SectionLoopUiState> = _uiState.asStateFlow()
    private var job: Job? = null
    private var loadedSource: WeakSectionSource? = null
    private val jangdan = jangdanPlayer?.takeIf { it.available }?.let { JangdanSession(it, viewModelScope) }
    private var previewJob: Job? = null
    private var previewing = false
    /** 지금 정간을 시작한 시각. 장단 소리가 정간 사이에서도 이어지도록 tick을 잰다. */
    private var stepMark: TimeMark? = null
    /** 진행 중인 재평가 녹음. ‘연주 중단’으로 멈춰도 남아 있다가 ‘계속 연주’에서 잇는다. */
    private var take: PendingTake? = null
    /** ‘연주 중단’ 때 지금 정간에서 이미 흐른 시간(ms). 이어 갈 때 그 정간의 남은 시간만 기다려 소리와 악보가 어긋나지 않게 한다. */
    private var pausedCellMs = 0.0

    private class PendingTake(val recorder: AudioRecorder, val key: String, val version: Int?, val section: SessionSection)

    init {
        settingsStore?.let { store ->
            viewModelScope.launch { store.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } } }
        }
        jangdan?.let { session ->
            viewModelScope.launch { session.status.collect { status -> _uiState.update { it.copy(jangdanStatus = status) } } }
        }
        _uiState.update { it.copy(jangdanSupported = jangdan != null) }
        viewModelScope.launch(perf.context()) {
            val source = repository.source(sourceId)
            loadedSource = source
            _uiState.update {
                it.copy(
                    loading = false,
                    song = source?.song,
                    cells = source?.let { s -> section.positions(s.song.score) }.orEmpty(),
                )
            }
            if (source != null && autoStart) startCountdown()
        }
    }

    /** ‘반복 멈춤’ · ‘반복 계속’(마친 뒤에는 ‘다시 연습’). 재평가 녹음 중에는 녹음을 그만둔다. */
    fun togglePause() {
        val state = _uiState.value
        when {
            state.recording -> cancelRecording()
            state.running || state.countdown != null -> {
                job?.cancel()
                stopJangdan()
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
            stopJangdan()
            stopPreview()
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
            when (loadedSource?.let { repository.isCurrent(it) }) {
                false -> return@launch notice(SectionLoopNotice.ScoreChanged)
                null -> return@launch notice(SectionLoopNotice.ScoreUnavailable)
                true -> Unit
            }
            val granted = micPermission?.let { it.isGranted() || it.request() } ?: true
            if (!granted) {
                notice(if (micPermission?.isPermanentlyDenied() == true) SectionLoopNotice.MicBlocked else SectionLoopNotice.MicDenied)
                return@launch
            }
            record(recorder, SessionSection(notes.first, notes.last, sourceId))
        }
    }

    /** 구간 재평가의 ‘시작 취소’·‘연주 대기로’: 녹음 중이면 버리고 평가 대기로 돌아간다. 녹음 중이 아니면 아무것도 하지 않는다. */
    fun cancelEvaluation() {
        if (_uiState.value.recording) cancelRecording()
    }

    /** 구간 재평가의 ‘연주 중단’: 연주 평가처럼 녹음·진행을 그 자리에서 멈추고 ‘연주를 마칠까요?’를 묻는다. */
    fun askAbort() {
        val state = _uiState.value
        val pending = take ?: return
        if (!state.recording || !state.running || state.abortAsked) return
        job?.cancel()
        pausedCellMs = (stepMark?.elapsedNow()?.inWholeNanoseconds ?: 0L) / 1_000_000.0
        stopJangdan()
        runCatching { pending.recorder.pause() }
        _uiState.update { it.askAbort() }
    }

    /** ‘계속 연주’·확인 창 닫기: 3·2·1 뒤 멈춘 자리부터 녹음을 잇는다. */
    fun keepRecording() {
        val pending = take ?: return
        if (!_uiState.value.abortAsked) return
        _uiState.update { it.keepRecording() }
        job = viewModelScope.launch {
            countdown()
            if (runCatching { pending.recorder.resume() }.isFailure) {
                pending.recorder.cancel()
                take = null
                _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
                return@launch
            }
            follow(pending, firstWaitMs = _uiState.value.cellMillis - pausedCellMs)
        }
    }

    /** 구간 재평가 제목 옆 ‘쉬운 표기’: 연습 화면과 같은 악보 설정의 한글 읽기를 켜고 끈다. */
    fun toggleNotation() {
        settingsStore?.update { it.copy(showHangul = !it.showHangul) }
    }

    /** 화면이 녹음을 분석으로 넘겼다. */
    fun takeConsumed() = _uiState.update { it.copy(take = null) }

    fun noticeShown() = _uiState.update { it.copy(notice = null) }

    /** 마이크 설정을 연다. 열 수 없으면 false. */
    fun openMicSettings(): Boolean = micPermission?.openSettings() == true

    private suspend fun record(recorder: AudioRecorder, sessionSection: SessionSection) {
        val key = newKey()
        _uiState.update { it.beginRecording() }
        countdown()
        val recordedVersion = _uiState.value.song?.scoreVersion
        if (runCatching { recorder.start() }.isFailure) {
            _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
            return
        }
        val pending = PendingTake(recorder, key, recordedVersion, sessionSection)
        take = pending
        follow(pending)
    }

    private suspend fun countdown() {
        while (currentCoroutineContext().isActive && _uiState.value.countdown != null) {
            delay(CountdownTickMillis)
            _uiState.update { it.countdownTick() }
        }
    }

    /**
     * 녹음하며 구간을 정간마다 따라간다. 끝까지 가면 녹음을 마쳐 넘긴다. ‘연주 중단’은 이 코루틴을 취소해 그 자리에 멈춘다.
     * [firstWaitMs]는 멈췄던 정간의 남은 시간이다.
     */
    private suspend fun follow(pending: PendingTake, firstWaitMs: Double? = null) {
        val recorder = pending.recorder
        if (_uiState.value.settings.jangdanSound) startJangdan()
        var wait = firstWaitMs
        stepMark = TimeSource.Monotonic.markNow()
        while (currentCoroutineContext().isActive && _uiState.value.running) {
            delay((wait ?: _uiState.value.cellMillis).roundToLong().coerceAtLeast(1))
            wait = null
            if (recorder.interrupted) {
                // 전화 등으로 시스템이 녹음을 끊었다. 끊긴 녹음은 보내지 않는다.
                recorder.cancel()
                take = null
                stopJangdan()
                _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
                return
            }
            val level = recorder.level()
            _uiState.update { it.advance().copy(micLevel = level) }
            stepMark = TimeSource.Monotonic.markNow()
        }
        if (!currentCoroutineContext().isActive) return
        take = null
        stopJangdan()
        val audio = runCatching { recorder.stop() }.getOrNull()
        val recorded = audio?.takeUnless { it.tooLarge }?.let { RecordedTake(it.path, pending.key, pending.version) }
        if (recorded == null) {
            _uiState.update { it.endRecording(SectionLoopNotice.RecordFailed) }
            return
        }
        _uiState.update { it.endRecording().copy(take = SectionTake(recorded, pending.section)) }
    }

    private fun cancelRecording() {
        job?.cancel()
        take = null
        recorder?.cancel()
        stopJangdan()
        _uiState.update { it.endRecording().copy(micLevel = 0f) }
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
            if (_uiState.value.running && _uiState.value.settings.jangdanSound) startJangdan()
            stepMark = TimeSource.Monotonic.markNow()
            while (isActive && _uiState.value.running) {
                delay(_uiState.value.cellMillis.roundToLong().coerceAtLeast(1))
                _uiState.update { it.advance() }
                stepMark = TimeSource.Monotonic.markNow()
            }
            stopJangdan()
        }
    }

    /** 연습 화면과 같은 설정 시트를 연다. 녹음 중에는 열지 않는다(녹음을 멈추지 않도록). */
    fun openPanel(panel: PracticePanel) {
        val state = _uiState.value
        if (state.recording) return
        stopPreview()
        val opened = state.asPracticeState(evaluation = false).openPanel(panel)
        _uiState.update { it.copy(panel = opened.panel, draft = opened.draft) }
    }

    /** 하위 설정의 뒤로가기(설정 목록으로) 또는 설정 목록 닫기. */
    fun closePanel() {
        stopPreview()
        val closed = _uiState.value.asPracticeState(evaluation = false).closePanel()
        _uiState.update { it.copy(panel = closed.panel, draft = closed.draft) }
    }

    fun dismissPanels() {
        stopPreview()
        _uiState.update { it.copy(panel = null) }
    }

    /** 설정 시트에서 고친 값을 바로 저장한다. 속도는 구간 고르기에서 정하므로 바꾸지 않는다. */
    fun updateDraft(transform: (PracticeDraft) -> PracticeDraft) {
        _uiState.update { it.copy(draft = transform(it.draft)) }
        val state = _uiState.value
        when (state.panel) {
            PracticePanel.Notation -> settingsStore?.update { it.applyNotation(state.draft) }
            PracticePanel.Jangdan -> {
                settingsStore?.update { it.applyJangdan(state.draft) }
                if (!state.draft.jangdanSound && !previewing) stopJangdan()
            }
            else -> Unit
        }
    }

    /** 연습 설정의 ‘연습 악기’(구간 연습). */
    fun setInstrument(id: String) {
        settingsStore?.update { it.copy(instrumentId = id) }
    }

    fun toggleBigScore() {
        settingsStore?.update { it.copy(bigScore = !it.bigScore) }
    }

    /** ‘운지법 보기’·‘운지법과 함께 보기’. 연습 화면과 같은 설정을 바로 바꾼다. */
    fun toggleFingeringView() {
        settingsStore?.update { it.copy(fingeringView = !it.fingeringView) }
    }

    /** 장단 패널의 ‘장단 듣기’: 구간 속도로 장단을 미리 듣는다. 반복·녹음 중에는 듣지 않는다. */
    fun togglePreview() {
        val state = _uiState.value
        if (previewing) {
            stopPreview()
            return
        }
        if (state.running || state.recording) return
        val session = jangdan ?: return
        val plan = plan() ?: return
        var mark: TimeMark? = null
        previewing = true
        session.start(plan, accompanyingModel = false, tick = {
            mark?.elapsedNow()?.inWholeNanoseconds?.div(1_000_000.0)?.div(plan.msPerTick)?.plus(plan.mapping.startTick)
        }, onStarted = {
            mark = TimeSource.Monotonic.markNow()
            _uiState.update { it.copy(draft = it.draft.copy(previewing = true, previewBeat = 0)) }
            previewJob = viewModelScope.launch {
                while (isActive) {
                    val elapsed = (mark?.elapsedNow()?.inWholeNanoseconds ?: 0L) / 1_000_000.0
                    val cell = plan.mapping.cellAt(plan.mapping.startTick + elapsed / plan.msPerTick)
                    _uiState.update { it.copy(draft = it.draft.copy(previewBeat = cell)) }
                    delay(50)
                }
            }
        })
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        if (previewing) {
            previewing = false
            jangdan?.stop()
        }
        _uiState.update { it.copy(draft = it.draft.copy(previewing = false, previewBeat = 0)) }
    }

    /** 이 곡 장단을 구간 속도로 칠 계획. 장단 정보나 박자 정보가 없으면 null. */
    private fun plan(): JangdanPlan? {
        val song = _uiState.value.song ?: return null
        val timing = song.score.timing ?: return null
        return song.jangdanPlayback?.let { planJangdan(it, bpm, timing.ticksPerBeat) }
    }

    /** 구간 첫 음의 tick부터 친다. 지금 정간까지 지난 정간 수와 정간 안에서 흐른 시간으로 지금 tick을 잰다. */
    private fun startJangdan() {
        val session = jangdan ?: return
        val plan = plan() ?: return
        val song = _uiState.value.song ?: return
        val startTick = song.timeline.firstOrNull { section.contains(it.column, it.row) && it.startTick != null }?.startTick?.toDouble() ?: return
        stopPreview()
        session.start(plan, accompanyingModel = false, startAtTick = startTick, tick = {
            val state = _uiState.value
            if (!state.running) {
                null
            } else {
                val within = (stepMark?.elapsedNow()?.inWholeNanoseconds ?: 0L) / 1_000_000.0
                startTick + (state.step * state.cellMillis + within.coerceAtMost(state.cellMillis)) / plan.msPerTick
            }
        })
    }

    private fun stopJangdan() {
        if (!previewing) jangdan?.stop()
    }

    override fun onCleared() {
        if (_uiState.value.recording) recorder?.cancel()
        jangdan?.release()
    }
}
