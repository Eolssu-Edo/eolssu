package com.ssafy.eolssu.feature.guide.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaLoad
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaRepository
import com.ssafy.eolssu.data.song.CatalogInstrument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
 * 기초부터 배우기 단계 연습(3~7단계). 녹음기로 소리 크기를 재고 녹음 파일은 남기지 않는다(멈추면 [AudioRecorder.cancel]).
 * 4~6단계는 음높이 관측([AudioRecorder.pitchReadings])이 있고 판정 기준([loadCriteria])을 받으면 음높이로 통과를 판정한다.
 * 녹음기·마이크 권한은 플랫폼 구현이 있을 때만 받는다(현재 Android·iOS). 없으면 스스로 확인해 완료로 표시하게 한다.
 */
class LearnPracticeViewModel(
    private val stage: Int,
    private val recorder: AudioRecorder?,
    private val micPermission: MicPermission?,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val loadCriteria: (suspend (GuideInstrument) -> NotePracticeCriteria?)? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LearnPracticeUiState(stage = stage, mic = if (recorder?.measuresLevel == true) LearnMicStatus.Available else LearnMicStatus.Unsupported),
    )
    val uiState: StateFlow<LearnPracticeUiState> = _uiState.asStateFlow()
    private var startJob: Job? = null
    private var levelJob: Job? = null
    private var pitchJob: Job? = null
    private var planJob: Job? = null
    private var planKey: Pair<GuideInstrument, LearnStageDetail>? = null
    private var plan: LearnPitchPlan? = null
    private var tracker: LearnPitchTracker? = null

    /**
     * 단계 내용을 받았을 때 음높이 판정을 준비한다. 4~6단계이고 음높이 관측과 판정 기준이 있을 때만 켜진다.
     * 같은 악기·내용이면 다시 준비하지 않는다. 기준을 받지 못하면 스스로 확인으로 남는다.
     */
    fun prepare(instrument: GuideInstrument, detail: LearnStageDetail) {
        val load = loadCriteria ?: return
        if (stage !in 4..6 || recorder?.pitchReadings == null || planKey == instrument to detail) return
        planKey = instrument to detail
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val criteria = load(instrument) ?: return@launch
            val next = learnPitchPlan(stage, detail, instrument, criteria) ?: return@launch
            plan = next
            // 듣는 도중이면 판정 방식을 바꾸지 않고 다음 '듣기 시작'부터 쓴다.
            if (_uiState.value.listening) return@launch
            tracker = LearnPitchTracker(next)
            _uiState.update { it.withPitchPlan(next) }
        }
    }

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
            val waiting = plan
            if (waiting != null && _uiState.value.pitch == null) {
                tracker = LearnPitchTracker(waiting)
                _uiState.update { it.withPitchPlan(waiting) }
            }
            val state = _uiState.value
            if (state.phase == LearnPracticePhase.Passed || state.pitch?.finished == true) tracker?.reset()
            _uiState.update { it.startListening() }
            // 녹음보다 먼저 구독해야 첫 관측을 놓치지 않는다(관측 흐름은 지난 값을 다시 주지 않는다).
            watchPitch(recorder)
            if (runCatching { recorder.start() }.isFailure) {
                pitchJob?.cancel()
                _uiState.update { it.micUnavailable(LearnMicStatus.Failed) }
                return@launch
            }
            recorder.level() // 시작 전까지 쌓인 크기를 버린다
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
        tracker?.reset()
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
                    pitchJob?.cancel()
                    _uiState.update { it.stopListening() }
                    break
                }
                val state = _uiState.updateAndGet { it.recordLevel(recorder.level(), sinceMs) }
                // 통과했거나 너무 오래 들었으면 녹음을 버리고 멈춘다(통과 상태는 그대로 남는다).
                if (state.phase == LearnPracticePhase.Passed || state.listenedMs >= LEARN_LISTEN_LIMIT_MS) {
                    runCatching { recorder.cancel() }
                    pitchJob?.cancel()
                    _uiState.update { it.stopListening() }
                    break
                }
            }
        }
    }

    /** 음높이 판정이 켜져 있으면 관측을 판정기에 넣는다. 통과하거나 통과할 수 없게 되면 녹음을 버리고 멈춘다. */
    private fun watchPitch(recorder: AudioRecorder) {
        pitchJob?.cancel()
        val judge = tracker ?: return
        val readings = recorder.pitchReadings ?: return
        pitchJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            readings.collect { reading ->
                val result = judge.accept(reading)
                _uiState.update { it.recordPitch(judge.index, judge.hits, judge.holdMs, result, judge.passed, judge.finished) }
                if (judge.finished) {
                    levelJob?.cancel()
                    runCatching { recorder.cancel() }
                    _uiState.update { it.stopListening() }
                    this@launch.cancel()
                }
            }
        }
    }

    private fun releaseRecorder() {
        startJob?.cancel()
        levelJob?.cancel()
        levelJob = null
        pitchJob?.cancel()
        pitchJob = null
        runCatching { recorder?.cancel() }
    }

    override fun onCleared() {
        releaseRecorder()
    }

    private companion object {
        const val LEVEL_INTERVAL_MS = 100L
    }
}

/**
 * 기초부터 배우기 악기의 한 음씩 연습 기준(PRA-06)을 받는다. 활성 악기 목록에서 code로 서버 ID를 찾는다(순번·seed ID를 쓰지 않는다).
 * 목록·기준을 받지 못하거나 기준이 없는 악기면 null이다.
 */
suspend fun loadLearnCriteria(
    instrument: GuideInstrument,
    instruments: suspend () -> List<CatalogInstrument>,
    repository: NotePracticeCriteriaRepository,
): NotePracticeCriteria? {
    val code = when (instrument) {
        GuideInstrument.Danso -> "DANSO"
        GuideInstrument.Sogeum -> "SOGEUM"
        GuideInstrument.DaegeumJeongak -> "JEONGAK_DAEGEUM"
        GuideInstrument.DaegeumSanjo -> "SANJO_DAEGEUM"
    }
    val id = try {
        instruments().firstOrNull { it.code == code }?.instrumentId
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } ?: return null
    return (repository.criteria(id) as? NotePracticeCriteriaLoad.Found)?.criteria
}
