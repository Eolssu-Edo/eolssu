package com.ssafy.eolssu.feature.guide.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.PitchGate
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
 * 3단계는 음높이 관측이 있으면 처음 낸 높이를 이어 내는지 판정한다([learnFirstSoundPlan], 기준을 받으면 그 허용 오차를 쓴다).
 * 4~6단계는 음높이 관측([AudioRecorder.pitchReadings])이 있고 판정 기준([loadCriteria])을 받으면 음높이로 통과를 판정한다.
 * 7단계는 음높이 관측이 있으면 첫 '투'와 같은 음만 센다([LearnTongueTracker], 기준을 받으면 그 허용 오차를 쓴다).
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
        LearnPracticeUiState(
            stage = stage,
            mic = if (recorder?.measuresLevel == true) LearnMicStatus.Available else LearnMicStatus.Unsupported,
            tonguesByPitch = stage == 7 && recorder?.pitchReadings != null,
        ),
    )
    val uiState: StateFlow<LearnPracticeUiState> = _uiState.asStateFlow()
    private val _celebrating = MutableStateFlow(false)

    /**
     * 4단계부터 통과하거나 스스로 확인했을 때 띄우는 '완료했어요!' 창. 화면 배치가 바뀌어도(태블릿 회전으로 단계 화면 ↔ 목록 2단) 이어지도록
     * 화면이 아니라 이 ViewModel이 갖는다(S15P31E102-1030).
     */
    val celebrating: StateFlow<Boolean> = _celebrating.asStateFlow()
    private var announcedPass = false
    private var startJob: Job? = null
    private var levelJob: Job? = null
    private var pitchJob: Job? = null
    private var planJob: Job? = null
    private var planKey: Pair<GuideInstrument, LearnStageDetail>? = null
    private var plan: LearnPitchPlan? = null
    private var tracker: LearnPitchTracker? = null
    private val tongue: LearnTongueTracker? = if (stage == 7 && recorder?.pitchReadings != null) LearnTongueTracker() else null

    /**
     * 단계 내용을 받았을 때 음높이 판정을 준비한다. 음높이 관측이 있을 때만 켜진다. 같은 악기·내용이면 다시 준비하지 않는다.
     * 3단계는 곧바로 공통 값으로 켜고 기준을 받으면 그 허용 오차로 바꾼다. 4~6단계는 기준을 받아야 켜지고, 받지 못하면 스스로 확인으로 남는다.
     * 7단계는 기준을 받으면 혀치기 같은 음 판정의 허용 오차만 바꾼다(받지 못해도 공통 값으로 판정한다).
     */
    fun prepare(instrument: GuideInstrument, detail: LearnStageDetail) {
        if (stage !in 3..7 || recorder?.pitchReadings == null || planKey == instrument to detail) return
        planKey = instrument to detail
        planJob?.cancel()
        if (stage == 3) usePlan(learnFirstSoundPlan(instrument, null))
        val load = loadCriteria ?: return
        planJob = viewModelScope.launch {
            val criteria = load(instrument) ?: return@launch
            if (tongue != null) {
                tongue.use(criteria)
                return@launch
            }
            val next = if (stage == 3) learnFirstSoundPlan(instrument, criteria) else learnPitchPlan(stage, detail, instrument, criteria)
            usePlan(next ?: return@launch)
        }
    }

    /** 판정 계획을 바꾼다. 듣는 도중이면 판정 방식을 바꾸지 않고 다음 '듣기 시작'부터 쓴다. */
    private fun usePlan(next: LearnPitchPlan) {
        plan = next
        if (_uiState.value.listening) return
        tracker = LearnPitchTracker(next)
        _uiState.update { it.withPitchPlan(next) }
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
            if (waiting != null && (_uiState.value.pitch == null || tracker?.plan != waiting)) {
                tracker = LearnPitchTracker(waiting)
                _uiState.update { it.withPitchPlan(waiting) }
            }
            val state = _uiState.value
            if (state.phase == LearnPracticePhase.Passed || state.pitch?.finished == true) tracker?.reset()
            if (state.phase == LearnPracticePhase.Passed) tongue?.reset()
            _uiState.update { it.startListening() }
            // 음높이로 판정하는 동안은 한 음씩과 같은 악기별 높이 문턱을 쓴다(단소는 완화, S15P31E102-857). 멈추면 되돌린다.
            recorder.pitchGate = plan?.takeIf { tracker != null }?.rule?.leniency?.gate ?: PitchGate.Default
            // 녹음보다 먼저 구독해야 첫 관측을 놓치지 않는다(관측 흐름은 지난 값을 다시 주지 않는다).
            watchPitch(recorder)
            if (runCatching { recorder.start() }.isFailure) {
                pitchJob?.cancel()
                recorder.pitchGate = PitchGate.Default
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
        tongue?.reset()
        _uiState.update { it.reset() }
    }

    /** 할 일 박스 체크를 풀어 [noteIndex]번째 음부터 다시 낸다(S15P31E102-749). 듣는 중이면 이어 듣는다. */
    fun rewindTo(noteIndex: Int) {
        tracker?.rewind(noteIndex)
        _uiState.update { it.rewindTo(noteIndex) }
    }

    /**
     * 통과 상태가 바뀔 때 부른다. [celebrates] 단계면 한 번 통과에 한 번만 '완료했어요!'를 띄우고(닫은 뒤 화면을 돌려도 다시 뜨지 않음),
     * 통과가 풀리면(처음부터) 다음 통과에 다시 띄운다.
     */
    fun onPassChanged(passed: Boolean, celebrates: Boolean) {
        if (!passed) {
            announcedPass = false
        } else if (celebrates && !announcedPass) {
            announcedPass = true
            _celebrating.value = true
        }
    }

    /** 스스로 확인으로 마칠 때 '완료했어요!'를 띄운다. 듣는 중이면 멈춘다. */
    fun celebrate() {
        stopListening()
        _celebrating.value = true
    }

    /** '완료했어요!' 창을 닫는다. */
    fun dismissCelebration() {
        _celebrating.value = false
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
                    discard(recorder)
                    pitchJob?.cancel()
                    _uiState.update { it.stopListening() }
                    break
                }
                val wasQuiet = _uiState.value.quiet
                val state = _uiState.updateAndGet { it.recordLevel(recorder.level(), sinceMs) }
                // 7단계: 새 '투'가 시작되면 높이 판정기에 알린다. 높이가 정해지면 watchPitch가 센다.
                if (wasQuiet && !state.quiet && state.listening) tongue?.onset()
                // 통과했거나 너무 오래 들었으면 녹음을 버리고 멈춘다(통과 상태는 그대로 남는다).
                if (state.phase == LearnPracticePhase.Passed || state.listenedMs >= LEARN_LISTEN_LIMIT_MS) {
                    discard(recorder)
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
        val readings = recorder.pitchReadings ?: return
        val tongue = tongue
        if (tongue != null) {
            pitchJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                readings.collect { reading -> if (tongue.accept(reading)) _uiState.update { it.recordTongue(tongue.count) } }
            }
            return
        }
        val judge = tracker ?: return
        pitchJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            readings.collect { reading ->
                val result = judge.accept(reading)
                _uiState.update { it.recordPitch(judge.index, judge.hits, judge.holdMs, result, judge.passed, judge.finished) }
                if (judge.finished) {
                    levelJob?.cancel()
                    discard(recorder)
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
        recorder?.let(::discard)
    }

    /** 녹음을 버리고 높이 문턱을 기본값으로 되돌린다. */
    private fun discard(recorder: AudioRecorder) {
        runCatching { recorder.cancel() }
        recorder.pitchGate = PitchGate.Default
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
    val code = instrument.criteriaCode
    val id = try {
        instruments().firstOrNull { it.code == code }?.instrumentId
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } ?: return null
    return (repository.criteria(id) as? NotePracticeCriteriaLoad.Found)?.criteria
}

/** 한 음씩 연습 기준·악기별 완화([com.ssafy.eolssu.core.audio.StepLeniency])가 쓰는 서버 악기 code. */
internal val GuideInstrument.criteriaCode: String
    get() = when (this) {
        GuideInstrument.Danso -> "DANSO"
        GuideInstrument.Sogeum -> "SOGEUM"
        GuideInstrument.DaegeumJeongak -> "JEONGAK_DAEGEUM"
        GuideInstrument.DaegeumSanjo -> "SANJO_DAEGEUM"
    }
