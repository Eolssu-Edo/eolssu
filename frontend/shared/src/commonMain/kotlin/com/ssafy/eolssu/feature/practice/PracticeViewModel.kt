package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.LimitedJangdanSamplePlayer
import com.ssafy.eolssu.core.audio.NoJangdanVolumeLimit
import com.ssafy.eolssu.core.audio.UnavailableJangdanSamplePlayer
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.audio.planBasicAccompaniment
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaLoad
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.song.SheetPracticeDownload
import com.ssafy.eolssu.data.song.SheetPracticeNeedsReview
import com.ssafy.eolssu.data.song.SheetPracticeRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.ModelAudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.ssafy.eolssu.core.model.ScoreSection
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * PRACTICE-01. 정간 길이만큼 기다리며 위치를 옮긴다(와이어프레임 타이머와 같은 방식).
 * 상태를 바꾸는 규칙은 `PracticeUiState`의 전환 함수에 두고, 여기서는 타이머와 녹음을 켜고 끈다.
 *
 * 평가 연주는 [recorder]로 녹음한다. 연주 중단 확인 창·이어 연주 카운트다운 동안은 일시정지하고,
 * 끊기거나 버린 연주의 녹음은 지운다. [recorder]가 없는 플랫폼은 평가 연주를 시작할 수 없다.
 * 입력 없음 안내·팝업은 [AudioRecorder.measuresLevel]인 녹음기가 있을 때만 띄우고, 나머지는 서버 판정에 맡긴다.
 * 끝까지 마친 녹음은 [takeForEvaluation]으로 분석 화면에 넘긴다. 녹음마다 새 세션 키를 [newKey]로 만든다.
 * 연주 평가는 [micPermission](OS 권한)만 보고 서비스 안내 창 없이 기기 권한 창을 바로 띄운다. 혼자 연습 '한 음씩'은
 * 앱 안의 '허용'과 OS 권한이 모두 있어야 있는 것으로 본다.
 *
 * 화면이 보이는 동안 [timeMeter]로 연습 시간을 잰다. 재생·녹음·장단 미리듣기 상태는 화면 상태에서 읽어 넘기고,
 * 입력·화면 보임은 Route가 알린다. 열린 구간은 30초마다, 닫힌 구간은 닫을 때 [timeQueue]에 남긴다.
 *
 * 혼자 연습의 '한 음씩 따라 불기'는 [stepJudge]가 기다리는 음을 맞게 불었다고 알리면([onStepNoteMatched]) 다음 음으로 넘어간다.
 * 판정은 기기 안에서만 하고 녹음을 보내거나 분석하지 않는다. 곡을 불러오면 [notePractice]로 그 악기의 판정 기준을 받아
 * 마이크로 듣는 판정기([PitchStepNoteJudge])로 바꾼다. 혼자 연습 화면은 평가 녹음을 하지 않으므로 마이크 권한·녹음 실패 안내는
 * 한 음씩 것이다. [mine]은 내 악보 연습(평가 버튼 없음)이다.
 *
 * 구간 연습(S15P31E102-794)은 연습 기록 → 구간 고르기에서 [initialSection]으로 연다. 모범 연주·장단 연습·한 음씩이
 * 그 구간만 한 번 연습하고 구간 처음에 멈춘다. 타임라인도 구간 안에서만 움직인다.
 *
 * 구간 재평가(S15P31E102-824)는 연주 평가([direct])에 [initialSection]과 [sectionSource]를 함께 준 것이다. 화면·진행·확인 창은
 * 연주 평가와 같고, 바탕 세션의 악보를 불러와 그 구간만 녹음한다. 마친 녹음은 [sectionForEvaluation]의 구간과 함께 분석에 넘긴다.
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
    private val newKey: () -> String = { Uuid.random().toString() },
    modelAudioRepository: ModelAudioRepository? = null,
    audioPlayer: AudioPlayer = UnavailableAudioPlayer(),
    private val mine: Boolean = false,
    learn: Boolean = false,
    stepJudge: StepNoteJudge = ManualStepNoteJudge(),
    member: SignedInMember? = null,
    private val jangdanPlayer: JangdanSamplePlayer = UnavailableJangdanSamplePlayer(),
    /** 평가 녹음 중 장단 배율(S15P31E102-878). 플랫폼이 시스템 볼륨으로 계산한다. */
    jangdanVolume: JangdanVolumeLimit = NoJangdanVolumeLimit,
    private val notePractice: NotePracticeCriteriaRepository? = null,
    private val loadPersonalSheet: (suspend (String) -> Song?)? = null,
    private val perf: PerformanceVisit? = null,
    private val personalAudio: SheetPracticeRepository? = null,
    private val direct: Boolean = false,
    /** 평가 녹음 최대 길이. 테스트만 짧게 바꾼다(S15P31E102-737). */
    private val maxRecording: Duration = RecordingSpec.MaxDuration,
    /** 구간 연습의 구간. 있으면 이 구간만 연습한다. */
    private val initialSection: ScoreSection? = null,
    /** 구간 재평가의 바탕 세션. 있으면 곡 대신 그 세션의 악보를 불러오고, 녹음 전에 그 악보의 판본을 확인한다. */
    private val sectionSource: SectionEvaluationSource? = null,
) : ViewModel() {
    private var stepJudge: StepNoteJudge = stepJudge

    private val _uiState = MutableStateFlow(
        PracticeUiState(solo = solo || mine, mine = mine, learn = learn, direct = direct && !solo && !mine && !learn && (recorder != null || sectionSource != null), fingeringOverride = if (learn) true else null, step = PracticeStep(manualConfirm = stepJudge.manualConfirm), settings = settingsStore.settings.value, recordingSupported = recorder != null,
            inputMeasured = recorder?.measuresLevel == true, isSongLoading = true,
            // 구간 재평가는 악보를 받기 전에도 제목이 ‘구간 재평가’로 보이게 구간을 먼저 둔다.
            sectionPractice = initialSection?.takeIf { sectionSource != null }?.let(::PracticeSectionState)),
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var stepJob: Job? = null
    private var stepClock: PracticeTimelineClock? = null
    private var countdownJob: Job? = null
    private var previewJob: Job? = null
    private var listenJob: Job? = null
    private var levelJob: Job? = null
    private var snapshotJob: Job? = null
    private var songJob: Job? = null
    private var evaluationCheckJob: Job? = null
    private var personalAudioJob: Job? = null
    /** 구간 끝에서 모범 연주를 구간 처음으로 옮긴 뒤, 음원 위치가 구간 안으로 돌아오기 전까지는 다시 되풀이하지 않는다. */
    private var sectionSeekPending = false
    private var screenShown = false
    private val recordingClock = RecordingClock(playbackTimeSource)
    private var takeKey = ""
    private var takeScoreVersion: Int? = null
    private var take: RecordedTake? = null
    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        pitchSession.stop()
        pitchTally.value = LivePitchTally()
        take = null
        takeKey = ""
        takeScoreVersion = null
        screenShown = false
        timeMeter.hide()
        _uiState.value = PracticeUiState()
        viewModelScope.launch { stopAll() }
    }
    private val pitchTally = MutableStateFlow(LivePitchTally())
    private val pitchSession = PracticePitchSession(recorder, accountScope, { feedback -> pitchTally.update { it.add(feedback) } }) {
        _uiState.value.position
    }
    /** 현재·직전 음높이 안내. 화면에서는 이 흐름만 구독하며 녹음 제어와 독립적이다. */
    val pitchFeedback: StateFlow<PracticePitchFeedback> = pitchSession.state

    /**
     * 이번 평가 연주에서 음마다 모은 실시간 판정(S15P31E102-597). 측정이 끝난 음은 판정을 확정해 색을 고정한다(980). 멈춤·이어 연주·연주 완료 동안 남고,
     * 새 연주를 시작하거나(처음부터 다시 포함) 준비로 돌아가거나 연주 중 화면을 벗어나면 비운다.
     */
    val livePitch: StateFlow<LivePitchTally> = pitchTally.asStateFlow()

    private val stepGaugeState = MutableStateFlow<StepPitchGauge?>(null).takeIf { recorder?.diagnostics == true }

    /**
     * 한 음씩 따라 불기에서 지금 소리가 목표 음에 어떻게 닿는지(S15P31E102-666). 디버그 빌드에서만 있고 릴리스·관측 없는 플랫폼은 null이다.
     * 관측마다(약 20ms) 바뀌므로 [uiState]에 넣지 않고 막대만 따로 구독한다.
     */
    val stepGauge: StateFlow<StepPitchGauge?>? = stepGaugeState?.asStateFlow()

    private val jangdanAudio = JangdanSession(
        LimitedJangdanSamplePlayer(jangdanPlayer, jangdanVolume) { soundPurpose == SoundPurpose.Recording }, accountScope)
    private val modelAudio = ModelAudioSession(modelAudioRepository, audioPlayer, accountScope, playbackTimeSource, perf = perf) { song, bpm ->
        val plan = if (settingsStore.settings.value.jangdanSound && jangdanPlayer.available) song.jangdanPlanAt(bpm) else null
        plan == null || jangdanAudio.prepareAccompaniment(plan)
    }
    private enum class SoundPurpose { Preview, Model, Rhythm, Recording }
    private var soundPurpose: SoundPurpose? = null
    private var modelSoundBpm: Int? = null
    private var rhythmStartsAtAttack = true
    private var modelStartsAtAttack = true
    private var modelResumeAfterTick: Double? = null

    private fun modelTickAtReportedPosition(): Double? {
        val song = _uiState.value.song ?: return null
        val playback = modelAudio.state.value
        val audio = playback.audio ?: return null
        return song.modelJangdanTickAt(audio, playback.mediaPositionMs.toDouble())
    }

    private fun currentPlan(): JangdanPlan? {
        val state = _uiState.value
        val song = state.song ?: return null
        return song.jangdanPlanAt(state.bpm)
    }

    private fun Song.jangdanPlanAt(bpm: Int): JangdanPlan? {
        val timing = score.timing ?: return null
        basicAccompaniment?.let { return planBasicAccompaniment(it, timing, bpm) }
        return jangdanPlayback?.let { planJangdan(it, bpm, timing.ticksPerBeat) }
    }

    private fun stopJangdan() {
        soundPurpose = null
        modelSoundBpm = null
        jangdanAudio.stop()
    }

    private fun syncModelJangdan() {
        val state = _uiState.value
        if (state.mode != PracticeMode.Model || !state.playing || !state.settings.jangdanSound ||
            modelAudio.state.value.audio?.sheetPlayback?.withJangdan == true
        ) {
            if (soundPurpose == SoundPurpose.Model) stopJangdan()
            return
        }
        if (soundPurpose == SoundPurpose.Model && modelSoundBpm == state.bpm) return
        soundPurpose = SoundPurpose.Model
        modelSoundBpm = state.bpm
        modelStartsAtAttack = false
        val resumeAfter = modelResumeAfterTick
        modelResumeAfterTick = null
        val startAt = modelTickAtReportedPosition()
        val sectionEndMs = state.sectionTicks?.let { ticks ->
            val playback = modelAudio.state.value
            val audio = playback.audio
            if (audio == null) null else state.song?.modelSectionEndMediaPosition(audio, ticks, playback.mediaPositionMs.toDouble())
        }
        var lastMediaMs: Double? = null
        val startAudio = modelAudio.state.value.audio
        val startSong = state.song
        // 반복이 있는 수행표는 재생 중 장단 tick이 원래 위치로 돌아가므로 미리 예약하지 않는다.
        val linear = startAudio != null && startAudio.performance == null
        val untilTick = if (startSong == null || startAudio == null || !linear) null else {
            val endMs = startAudio.startOffsetMs + startSong.modelScoreDurationMs(startAudio)
            startSong.modelJangdanTickAt(startAudio, minOf(endMs, sectionEndMs ?: endMs))
        }
        jangdanAudio.start(currentPlan(), accompanyingModel = true, resumeAfterTick = resumeAfter, startAtTick = startAt,
            untilTick = untilTick, lookaheadMs = if (linear) JANGDAN_LOOKAHEAD_MS else 0.0,
            clockReady = { modelAudio.playbackClockReady }, tick = {
            val now = _uiState.value
            val song = now.song
            val audio = modelAudio.state.value.audio
            val anchor = modelAudio.playbackAnchor
            if (now.mode != PracticeMode.Model || !now.playing || song == null || audio == null || anchor == null) null
            else {
                // Decoder reports may jitter slightly backward. An explicit seek creates a new session.
                val mediaMs = maxOf(anchor.positionNow(), lastMediaMs ?: 0.0).also { lastMediaMs = it }
                val endMs = audio.startOffsetMs + song.modelScoreDurationMs(audio)
                when {
                    mediaMs < audio.startOffsetMs -> -1.0
                    mediaMs >= endMs || sectionEndMs != null && mediaMs >= sectionEndMs -> null
                    else -> song.modelJangdanTickAt(audio, mediaMs)
                }
            }
        })
    }

    /** 녹음 시계에 장단을 맞춘다. 소리 설정은 녹음 자체를 중단하지 않는다. */
    private fun syncRecordingJangdan() {
        val state = _uiState.value
        val enabled = state.mode == PracticeMode.Recording && !state.finished && state.popup == null && state.settings.jangdanSound
        if (!enabled) {
            if (soundPurpose == SoundPurpose.Recording) stopJangdan()
            return
        }
        if (soundPurpose == SoundPurpose.Recording) return
        val plan = currentPlan() ?: return
        soundPurpose = SoundPurpose.Recording
        val start = state.elapsedMs / plan.msPerTick
        jangdanAudio.start(plan, accompanyingModel = true, startAtTick = start, tick = {
            val now = _uiState.value
            if (now.mode != PracticeMode.Recording || now.finished || now.popup != null || !now.settings.jangdanSound) null
            else (stepClock?.elapsedMs() ?: now.elapsedMs) / plan.msPerTick
        })
    }

    fun selectModel() = selectPlaybackMode(PracticeMode.Model)
    fun selectRhythm() = selectPlaybackMode(PracticeMode.Rhythm)

    private fun selectPlaybackMode(target: PracticeMode) {
        val before = _uiState.value
        if (!accountScope.isActive || before.mode !in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm) || before.mode == target) return
        if (before.mode == PracticeMode.Model) {
            modelAudio.suspendAndRelease()
            _uiState.update { it.withModelAudio(modelAudio.state.value) }
        }
        pauseRhythm()
        stopPreview()
        stopJangdan()
        pauseStep()
        stepJob?.cancel()
        stepClock = null
        _uiState.update { state ->
            val selected = state.selectPlaybackMode(target)
            val tick = selected.currentTick ?: 0.0
            val timing = selected.song?.score?.timing
            selected.copy(elapsedMs = if (timing == null) selected.elapsedMs else tick * 60_000.0 / selected.bpm / timing.ticksPerBeat)
        }
        rhythmStartsAtAttack = true
        modelStartsAtAttack = true
        if (target == PracticeMode.Model) {
            if (modelAudio.state.value.audio != null) {
                if (before.mode == PracticeMode.Rhythm) modelAudio.pauseAtTick(_uiState.value.currentTick ?: 0.0)
                _uiState.update { it.withModelAudio(modelAudio.state.value) }
            } else if (supportsPersonalAudio) {
                _uiState.value.song?.let(::checkPersonalAudio)
            }
        }
    }

    /** Non-recorded continuous practice uses the same absolute timeline clock as the score. */
    fun startRhythm() {
        if (!accountScope.isActive || _uiState.value.playbackSettingsLocked) return
        val before = _uiState.value
        val song = before.song ?: return
        val plan = currentPlan()
        if (plan == null || !jangdanPlayer.available) {
            jangdanAudio.start(null, false, { null })
            return
        }
        modelAudio.stop()
        pauseStep()
        stopPreview()
        if (before.mode != PracticeMode.Rhythm) {
            rhythmStartsAtAttack = true
            val position = if (before.mode == PracticeMode.Ready) before.selected else before.position
            _uiState.update { it.copy(mode = PracticeMode.Rhythm, position = position, playing = false,
                eventElapsedMs = 0.0, elapsedMs = song.timeline[position].startTick!!.toDouble() * 60_000 / before.bpm / song.score.timing!!.ticksPerBeat,
                modelClock = null, modelPerformance = null, panel = null, follow = true, step = it.step.copy(on = false)) }
        } else if (before.position == before.eventCount - 1 && before.currentEventFraction >= 1.0) {
            rhythmStartsAtAttack = true
            _uiState.update { it.restartModel() }
        }
        settingsStore.update { it.copy(jangdanSound = true) }
        _uiState.update { it.copy(follow = true) }
        soundPurpose = SoundPurpose.Rhythm
        val resumeAfter = if (rhythmStartsAtAttack) null else _uiState.value.elapsedMs / plan.msPerTick
        val endTick = _uiState.value.sectionTicks?.endInclusive
            ?: song.timeline.last().let { (it.startTick!! + it.durationTick!!).toDouble() }
        jangdanAudio.start(plan, accompanyingModel = false, resumeAfterTick = resumeAfter, untilTick = endTick, tick = {
            if (_uiState.value.mode == PracticeMode.Rhythm && _uiState.value.playing)
                stepClock?.elapsedMs()?.div(plan.msPerTick)?.takeIf { it < endTick } else null
        }, onStarted = {
            rhythmStartsAtAttack = false
            _uiState.update { it.copy(playing = true) }
            startStepping(_uiState.value.eventElapsedMs)
        })
    }

    fun toggleRhythm() { if (_uiState.value.playing) pauseRhythm() else startRhythm() }

    private fun pauseRhythm() {
        if (_uiState.value.mode != PracticeMode.Rhythm) return
        captureStepProgress()
        stepJob?.cancel()
        stepClock = null
        _uiState.update { it.copy(playing = false, eventStartedAt = null, pausedFraction = it.currentEventFraction.toFloat()) }
        stopPreview()
        stopJangdan()
    }

    private fun seekRhythm(deltaMs: Long) {
        val wasPlaying = _uiState.value.playing
        var live = canSeekPlayingRhythm()
        if (live) captureStepProgress() else pauseRhythm()
        val state = _uiState.value
        val song = state.song ?: return
        val msPerTick = 60_000.0 / state.bpm / (song.score.timing?.ticksPerBeat ?: return)
        val last = song.timeline.last()
        val end = (last.startTick!! + last.durationTick!!).toDouble()
        val target = ((state.currentTick ?: 0.0) + deltaMs / msPerTick).coerceIn(0.0, end)
        if (live && target >= end) { pauseRhythm(); live = false }
        val position = song.timeline.indexOfLast { it.startTick!! <= target }.coerceAtLeast(0)
        _uiState.update { it.copy(position = position, eventElapsedMs = (target - song.timeline[position].startTick!!) * msPerTick,
            elapsedMs = target * msPerTick, eventStartedAt = null) }
        rhythmStartsAtAttack = true
        if (live) continueRhythmAfterSeek() else if (wasPlaying && target < end) startRhythm()
    }

    /** 장단 연습이 소리를 내며 진행 중이면 세션을 다시 만들지 않고 탐색한다(로딩·출력 재시작 없음). */
    private fun canSeekPlayingRhythm(): Boolean {
        val state = _uiState.value
        return state.mode == PracticeMode.Rhythm && state.playing && soundPurpose == SoundPurpose.Rhythm &&
            stepJob?.isActive == true && jangdanAudio.status.value == JangdanStatus.Playing
    }

    /** 상태에 새 위치를 넣은 뒤 부른다. 새 악보 시계를 먼저 걸어야 장단이 이전 위치의 타격을 예약하지 않는다. */
    private fun continueRhythmAfterSeek() {
        stepJob?.cancel()
        startStepping(_uiState.value.eventElapsedMs)
        if (!jangdanAudio.reposition()) {
            pauseRhythm()
            startRhythm()
        }
    }


    init {
        accountScope.launch {
            _uiState.map { it.position }.distinctUntilChanged().collect { pitchSession.refresh() }
        }
        accountScope.launch {
            // 녹음하는 동안 측정이 끝난 음(끝난 뒤 300ms 또는 두 음 뒤)의 판정을 확정한다. 화면은 확정한 음만 칠한다(S15P31E102-980).
            _uiState.map { if (it.mode == PracticeMode.Recording && !it.finished) Triple(it.position, it.elapsedMs, it.bpm) else null }
                .distinctUntilChanged()
                .collect { at ->
                    val song = _uiState.value.song
                    if (at != null && song != null) pitchTally.update { it.confirm(song, at.first, at.second, at.third) }
                }
        }
        accountScope.launch {
            _uiState.map { it.mode == PracticeMode.Ready || (it.mode == PracticeMode.Countdown && !it.resume) }
                .distinctUntilChanged()
                .collect { newTake -> if (newTake) pitchTally.value = LivePitchTally() }
        }
        retryLoadSong()
        if (initialSection != null) openSectionEntry(initialSection)
        accountScope.launch {
            modelAudio.state.collect { playback ->
                _uiState.update { state ->
                    val next = state.withModelAudio(playback)
                    if (state.personalAudio == PracticePersonalAudio.Ready && playback.status == ModelAudioStatus.Unavailable) {
                        next.copy(personalAudio = PracticePersonalAudio.Missing)
                    } else next
                }
                loopModelSection(playback.status)
                syncModelJangdan()
            }
        }
        accountScope.launch { settingsStore.settings.collect { s ->
            _uiState.update { it.copy(settings = s) }
            _uiState.update { it.copy(jangdanSupported = jangdanPlayer.available && currentPlan() != null) }
            syncModelJangdan()
            syncRecordingJangdan()
        } }
        accountScope.launch {
            _uiState.map { it.mode == PracticeMode.Recording && !it.finished && it.popup == null && it.settings.jangdanSound }
                .distinctUntilChanged().collect { syncRecordingJangdan() }
        }
        accountScope.launch { jangdanAudio.status.collect { status ->
            _uiState.update { it.copy(jangdanStatus = status) }
            if (status in setOf(JangdanStatus.Failed, JangdanStatus.Unsupported, JangdanStatus.Interrupted)) {
                if (_uiState.value.mode == PracticeMode.Rhythm) {
                    captureStepProgress()
                    stepJob?.cancel()
                    stepClock = null
                    _uiState.update { it.copy(playing = false, eventStartedAt = null) }
                }
                previewJob?.cancel()
                _uiState.update { it.copy(draft = it.draft.copy(previewing = false)) }
            }
        } }
        accountScope.launch {
            _uiState.map { it.runningActivities }.distinctUntilChanged().collect { running ->
                ContinuousActivity.entries.forEach { timeMeter.setRunning(it, it in running) }
            }
        }
    }

    /** 화면 재구성은 요청을 반복하지 않는다. 실패 후 재시도만 가능하며 취소는 오류로 바꾸지 않는다. */
    fun retryLoadSong() {
        if (!accountScope.isActive) return
        if (songJob?.isActive == true || _uiState.value.song != null) return
        _uiState.update { it.copy(isSongLoading = true, songError = null) }
        songJob = accountScope.launch(perf.context(if (songJob == null) PerfReason.INITIAL else PerfReason.RETRY)) {
            try {
                val song = when {
                    mine -> loadPersonalSheet?.invoke(songId)
                    sectionSource != null -> sectionSource.song()
                    direct -> songRepository.retainedSong(songId) ?: songRepository.getSong(songId)
                    else -> songRepository.getSong(songId)
                }
                when {
                    song == null -> _uiState.update { it.copy(songError = PracticeSongError.NotFound) }
                    song.timeline.isEmpty() -> _uiState.update { it.copy(songError = PracticeSongError.Failed) }
                    else -> {
                        perf?.milestone(PerfPhase.PRACTICE_READY)
                        _uiState.update {
                            val audio = when {
                                supportsPersonalAudio -> PracticePersonalAudio.Checking
                                mine && personalAudio != null -> PracticePersonalAudio.Unsupported
                                else -> null
                            }
                            it.copy(song = song, personalAudio = audio)
                        }
                        if (supportsPersonalAudio) checkPersonalAudio(song)
                        _uiState.update { it.copy(jangdanSupported = jangdanPlayer.available && currentPlan() != null) }
                        if (screenShown) startTimeTracking()
                        prepareStepJudge(song)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SheetPracticeNeedsReview) {
                _uiState.update { it.copy(songError = PracticeSongError.NeedsReview) }
            } catch (_: Exception) {
                _uiState.update { it.copy(songError = PracticeSongError.Failed) }
            } finally {
                _uiState.update { it.copy(isSongLoading = false) }
            }
        }
    }

    // 연습 시간

    /** 화면이 앞에 보임(들어옴·앱으로 돌아옴·탭이 다시 보임). 연습 시간 측정을 시작한다. */
    fun onScreenShown() {
        if (!accountScope.isActive) return
        screenShown = true
        val song = _uiState.value.song ?: return
        if (supportsPersonalAudio && _uiState.value.personalAudio != PracticePersonalAudio.VersionChanged) checkPersonalAudio(song)
        startTimeTracking()
    }

    private fun startTimeTracking() {
        if (mine || !accountScope.isActive) return
        // 구간 재평가는 바탕 세션에서 받은 곡의 연습 시간으로 잰다.
        timeMeter.show(if (sectionSource != null) _uiState.value.song?.id ?: return else songId)
        snapshotJob?.cancel()
        snapshotJob = accountScope.launch {
            while (true) {
                delay(PracticeTimeMeter.SnapshotInterval)
                timeMeter.snapshot()?.let(timeQueue::saveOpen)
            }
        }
    }

    /** 화면이 가려짐(떠남·백그라운드·화면 잠금·탭 숨김). 구간을 닫아 보낸다. */
    fun onScreenHidden() {
        screenShown = false
        personalAudioJob?.cancel()
        _uiState.update {
            if (it.personalAudio == PracticePersonalAudio.Preparing || it.personalAudio == PracticePersonalAudio.Downloading) {
                it.copy(personalAudio = PracticePersonalAudio.Checking)
            } else it
        }
        pauseRhythm()
        stopPreview()
        stopJangdan()
        jangdanAudio.release()
        modelAudio.suspendAndRelease()
        pauseStep()
        snapshotJob?.cancel()
        val segment = timeMeter.hide()
        if (!mine && accountScope.isActive) timeQueue.close(segment)
    }

    /** 터치·스크롤·키 입력. */
    fun onUserActivity() = timeMeter.onActivity()

    // 악보·패널

    /** 정간을 누름: 그 음을 고르고 운지법을 연다. 구간 연습은 구간이 정해져 있어 고르지 않는다. */
    fun selectUnit(unit: ScoreDisplayUnit) {
        val state = _uiState.value
        if (state.stepUi) {
            // 한 음씩은 운지법 대신 지금 음을 옮기고, 듣는 중이면 판정기가 새 음을 기다리게 한다(S15P31E102-1006).
            _uiState.update { it.selectUnit(unit) }
            syncStep()
            return
        }
        if (state.sectionPractice != null) return
        _uiState.update { it.selectUnit(unit) }
    }

    // 구간 연습

    /** 연습 기록 → 구간 고르기에서 들어옴: 곡을 받은 뒤 구간을 정해 구간 연습으로 둔다. */
    private fun openSectionEntry(section: ScoreSection) {
        accountScope.launch {
            _uiState.map { it.song }.filterNotNull().first()
            _uiState.update { it.openSectionPractice(section) }
        }
    }

    /** 모범 연주가 구간 끝을 지나면 구간 처음으로 돌아가 멈춘다. 다시 누르면 또 한 번 듣는다. */
    private fun loopModelSection(status: ModelAudioStatus) {
        val state = _uiState.value
        if (state.mode != PracticeMode.Model || state.sectionEvents == null) {
            sectionSeekPending = false
            return
        }
        if (sectionSeekPending) {
            if (!state.passedSectionEnd && status != ModelAudioStatus.Completed) sectionSeekPending = false
            return
        }
        val range = state.sectionEvents ?: return
        val playback = modelAudio.state.value
        val ticks = state.sectionTicks
        val audio = playback.audio
        val repeatedOutside = state.position < range.first && ticks != null && audio != null &&
            audio.performance?.segments?.firstOrNull { ticks.start >= it.startTick && ticks.start < it.endTick }
                ?.let { playback.mediaPositionMs >= audio.startOffsetMs + it.mediaMsAt(ticks.start) } == true
        val ended = status == ModelAudioStatus.Completed || (state.playing && (state.passedSectionEnd || repeatedOutside))
        if (!ended) return
        val song = state.song ?: return
        val first = state.sectionEvents?.first ?: return
        val tick = song.timeline.getOrNull(first)?.startTick?.toDouble() ?: return
        sectionSeekPending = true
        stopJangdan()
        _uiState.value = state.stopAtSectionStart()
        modelAudio.pauseAtTick(tick)
        modelStartsAtAttack = true
    }

    fun reportFingeringAudio(playing: Boolean) {
        _uiState.update { it.copy(fingeringAudioPlaying = playing && it.panel == PracticePanel.Fingering) }
    }

    fun openPanel(panel: PracticePanel) {
        val state = _uiState.value
        if (panel == PracticePanel.Settings && state.mode == PracticeMode.Recording &&
            !state.finished && state.popup == null) pauseRecordingFor(PracticePopup.SettingsPaused)
        stopPreview()
        _uiState.update { it.openPanel(panel) }
    }

    fun closePanel() {
        stopPreview()
        listenJob?.cancel()
        _uiState.update { it.closePanel() }
    }

    fun dismissPanels() {
        stopPreview()
        listenJob?.cancel()
        _uiState.update { it.copy(panel = null, listening = null) }
    }

    fun updateDraft(transform: (PracticeDraft) -> PracticeDraft) {
        _uiState.update { it.copy(draft = transform(it.draft)) }
        val state = _uiState.value
        val song = state.song ?: return
        when (state.panel) {
            PracticePanel.Jangdan -> {
                settingsStore.update { it.applyJangdan(state.draft) }
                if (!state.draft.jangdanSound) pauseRhythm()
            }
            PracticePanel.Tempo -> if (!state.playbackSettingsLocked) applyTempo(close = false)
            else -> Unit
        }
        _uiState.update { it.copy(settings = settingsStore.settings.value) }
    }

    fun moveFingering(delta: Int) = _uiState.update { it.moveFingering(delta) }

    /** 누른 음 표시. 예시음도 1.2초만 들려주므로([com.ssafy.eolssu.feature.practice.component.StepNotePlayMs]) 같은 길이로 둔다. */
    private fun listen(yul: Yul) {
        if (!accountScope.isActive) return
        listenJob?.cancel()
        _uiState.update { it.copy(listening = yul) }
        listenJob = accountScope.launch {
            delay(1200)
            _uiState.update { it.copy(listening = null) }
        }
    }

    /** 모범 연주 중에 속도를 바꾸면 현재 음의 남은 몫부터 새 속도로 이어 간다. */
    fun applyTempo(close: Boolean = true) {
        if (_uiState.value.mode == PracticeMode.Model) {
            modelResumeAfterTick = modelTickAtReportedPosition()
            val state = _uiState.value
            val song = state.song ?: return
            settingsStore.update { it.applyTempo(state.draft, song, mine) }
            _uiState.update {
                val playback = modelAudio.state.value
                val updated = it.copy(settings = settingsStore.settings.value).withModelAudio(playback)
                if (playback.audio == null) {
                    val scale = state.bpm.toDouble() / updated.bpm
                    updated.copy(elapsedMs = state.elapsedMs * scale, eventElapsedMs = state.eventElapsedMs * scale)
                } else updated
            }
            modelAudio.setBpm(_uiState.value.bpm)
            if (close) closePanel()
            return
        }
        captureStepProgress()
        val state = _uiState.value
        val song = state.song ?: return
        val preparingRhythm = soundPurpose == SoundPurpose.Rhythm &&
            (jangdanAudio.status.value == JangdanStatus.Loading || jangdanAudio.starting)
        val running = stepJob?.isActive == true || preparingRhythm
        if (state.mode == PracticeMode.Rhythm) pauseRhythm()
        val carry = state.currentEventFraction
        settingsStore.update { it.applyTempo(state.draft, song, mine) }
        _uiState.update {
            val updated = it.copy(settings = settingsStore.settings.value)
            updated.copy(eventElapsedMs = updated.eventDurationMs() * carry, pausedFraction = carry.toFloat())
        }
        modelAudio.setBpm(_uiState.value.bpm)
        if (close) closePanel()
        if (state.mode == PracticeMode.Rhythm) {
            val msPerTick = 60_000.0 / _uiState.value.bpm / song.score.timing!!.ticksPerBeat
            _uiState.update { it.copy(elapsedMs = (it.currentTick ?: 0.0) * msPerTick) }
            if (running) startRhythm()
        } else if (running) startStepping(_uiState.value.eventElapsedMs)
    }

    fun toggleBigScore() = settingsStore.update { it.copy(bigScore = !it.bigScore) }

    /** 연습 설정의 ‘운지법과 함께 보기’. 바로 반영된다(적용 버튼 없음). */
    /** 기초부터 배우기 곡 연습은 저장한 설정을 바꾸지 않고 이 화면에서만 켜고 끈다. */
    fun toggleFingeringView() {
        if (_uiState.value.fingeringOverride != null) _uiState.update { it.copy(fingeringOverride = !it.fingeringView) }
        else settingsStore.update { it.copy(fingeringView = !it.fingeringView) }
    }

    /** 연습 설정의 ‘한글 읽는 법 표시’ 스위치. 바로 켜고 끈다(S15P31E102-875). */
    fun toggleHangul() = settingsStore.update { it.copy(showHangul = !it.showHangul) }

    /** 연주 설정의 ‘실시간 채점’ 스위치. 판정 색만 숨기거나 다시 보이고, 판정 집계는 그대로 둔다(S15P31E102-980). */
    fun toggleLiveMarks() = settingsStore.update { it.copy(liveMarks = !it.liveMarks) }

    /** 연습 설정의 ‘연습 악기’. 운지 그림과 제목 옆 표시에만 쓰고 프로필 악기는 바꾸지 않는다. */
    fun setInstrument(id: String) = settingsStore.update { it.copy(instrumentId = id) }

    /** 플레이어의 −5초·+5초. 음원을 아직 받지 않았어도 타임라인과 다음 재생 위치를 옮긴다. */
    fun seekModel(deltaMs: Long) {
        val state = _uiState.value
        // 구간 연습은 구간 안에서만 옮긴다(S15P31E102-794).
        val sectionTick = state.sectionTickBy(deltaMs)
        if (sectionTick != null) {
            seekSection(sectionTick)
            return
        }
        if (state.mode in setOf(PracticeMode.Ready, PracticeMode.Model) && modelAudio.state.value.audio == null) {
            val duration = state.song?.durationMs(state.bpm) ?: return
            if (duration > 0.0) seekToFraction(((state.elapsedMs + deltaMs) / duration).toFloat())
            return
        }
        when (state.mode) {
            PracticeMode.Ready, PracticeMode.Model -> {
                if (state.mode == PracticeMode.Ready) _uiState.update { it.copy(mode = PracticeMode.Model) }
                modelStartsAtAttack = true
                modelResumeAfterTick = null
                stopJangdan()
                modelAudio.seekBy(deltaMs)
                _uiState.update { it.withModelAudio(modelAudio.state.value) }
            }
            PracticeMode.Rhythm -> seekRhythm(deltaMs)
            else -> Unit
        }
    }

    /** 타임라인은 음원/악보의 전체 길이에 대한 비율로 이동한다. 녹음에는 적용하지 않는다. */
    fun seekToFraction(fraction: Float) {
        if (!accountScope.isActive || !fraction.isFinite()) return
        val state = _uiState.value
        val song = state.song ?: return
        if (state.mode !in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm) ||
            song.timeline.isEmpty() || state.playerNotice == PracticePlayerNotice.Loading) return
        val progress = fraction.coerceIn(0f, 1f)
        // 구간 연습의 막대는 구간 처음 ~ 끝이다.
        val sectionTick = state.sectionTickAt(progress)
        if (sectionTick != null) {
            seekSection(sectionTick)
            return
        }
        if (state.mode in setOf(PracticeMode.Ready, PracticeMode.Model) && modelAudio.state.value.audio != null) {
            if (state.mode == PracticeMode.Ready) _uiState.update { it.copy(mode = PracticeMode.Model) }
            modelStartsAtAttack = true
            modelResumeAfterTick = null
            stopJangdan()
            modelAudio.seekToFraction(progress)
            _uiState.update { it.withModelAudio(modelAudio.state.value) }
            return
        }
        val wasPlaying = state.mode == PracticeMode.Rhythm && state.playing
        val live = progress < 1f && canSeekPlayingRhythm()
        if (!live) pauseRhythm()
        val timing = song.score.timing ?: return
        val last = song.timeline.last()
        val endTick = (last.startTick!! + last.durationTick!!).toDouble()
        val target = endTick * progress
        val position = song.timeline.indexOfLast { it.startTick!! <= target }.coerceAtLeast(0)
        val msPerTick = 60_000.0 / state.bpm / timing.ticksPerBeat
        _uiState.update { it.copy(mode = if (it.mode == PracticeMode.Ready) PracticeMode.Model else it.mode,
            position = position, selected = position, selectedCell = state.cellForEvent(position), playing = live, follow = true,
            eventElapsedMs = (target - song.timeline[position].startTick!!) * msPerTick,
            elapsedMs = target * msPerTick, eventStartedAt = null, modelClock = null) }
        modelStartsAtAttack = true
        rhythmStartsAtAttack = true
        if (live) continueRhythmAfterSeek() else if (wasPlaying && progress < 1f) startRhythm()
    }

    /**
     * 구간 연습의 타임라인 이동: 모범 연주는 음원을 그 tick으로, 장단 연습·준비는 악보 자리를 그 tick으로 옮긴다.
     * 구간 끝에 닿으면 구간 처음에 멈춘다.
     */
    private fun seekSection(tick: Double) {
        val state = _uiState.value
        val song = state.song ?: return
        val timing = song.score.timing ?: return
        val ticks = state.sectionTicks ?: return
        if (tick >= ticks.endInclusive) {
            when (state.mode) {
                PracticeMode.Model -> {
                    val start = song.timeline.getOrNull(state.sectionStartIndex)?.startTick
                    if (start != null) modelAudio.pauseAtTick(start.toDouble()) else modelAudio.pause()
                    modelStartsAtAttack = true
                }
                PracticeMode.Rhythm -> pauseRhythm()
                else -> Unit
            }
            stopJangdan()
            _uiState.update { it.stopAtSectionStart() }
            return
        }
        if (state.mode in setOf(PracticeMode.Ready, PracticeMode.Model) && modelAudio.state.value.audio != null) {
            if (state.mode == PracticeMode.Ready) _uiState.update { it.copy(mode = PracticeMode.Model) }
            modelStartsAtAttack = true
            modelResumeAfterTick = null
            stopJangdan()
            modelAudio.seekToTick(tick)
            _uiState.update { it.withModelAudio(modelAudio.state.value) }
            return
        }
        val wasPlaying = state.mode == PracticeMode.Rhythm && state.playing
        val live = canSeekPlayingRhythm()
        if (!live) pauseRhythm()
        val position = song.timeline.indexOfLast { (it.startTick ?: 0) <= tick }.coerceAtLeast(0)
        val msPerTick = 60_000.0 / state.bpm / timing.ticksPerBeat
        _uiState.update { it.copy(mode = if (it.mode == PracticeMode.Ready) PracticeMode.Model else it.mode,
            position = position, selected = position, selectedCell = state.cellForEvent(position), playing = live, follow = true,
            eventElapsedMs = (tick - (song.timeline[position].startTick ?: 0)) * msPerTick,
            elapsedMs = tick * msPerTick, eventStartedAt = null, modelClock = null) }
        modelStartsAtAttack = true
        rhythmStartsAtAttack = true
        if (live) continueRhythmAfterSeek() else if (wasPlaying) startRhythm()
    }

    /** Preview uses the reviewed score tick scale and the selected practice BPM. */
    fun togglePreview() {
        if (!accountScope.isActive || _uiState.value.playbackSettingsLocked) return
        if (soundPurpose == SoundPurpose.Preview) { stopPreview(); return }
        modelAudio.pause()
        pauseRhythm()
        pauseStep()
        val plan = currentPlan()
        var mark: kotlin.time.TimeMark? = null
        soundPurpose = SoundPurpose.Preview
        jangdanAudio.start(plan, accompanyingModel = false, tick = {
            if (plan == null) null else mark?.elapsedNow()?.inWholeNanoseconds?.div(1_000_000.0)?.div(plan.msPerTick)?.plus(plan.startTick)
        }, onStarted = {
            mark = playbackTimeSource.markNow()
            _uiState.update { it.copy(draft = it.draft.copy(previewing = true, previewBeat = 0)) }
            previewJob = accountScope.launch {
                while (isActive) {
                    val elapsed = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
                    val cell = plan!!.cellAt(plan.startTick + elapsed / plan.msPerTick)
                    _uiState.update { it.copy(draft = it.draft.copy(previewBeat = cell)) }
                    delay(50)
                }
            }
        })
    }

    private fun stopPreview() {
        previewJob?.cancel()
        if (soundPurpose == SoundPurpose.Preview) stopJangdan()
        _uiState.update { it.copy(draft = it.draft.copy(previewing = false, previewBeat = 0)) }
    }

    fun stopFollowing() = _uiState.update { it.stopFollowing() }

    /** 내 악보 제목을 곡 제목 대신 보인다. 비어 있으면 곡 제목으로 돌아간다. */
    fun showSheetTitle(title: String?) = _uiState.update { it.copy(sheetTitle = title?.takeIf(String::isNotBlank)) }

    fun followNow() = _uiState.update { it.followNow() }

    // 내 악보 음원
    private val supportsPersonalAudio: Boolean get() = mine && personalAudio?.deviceAudioAvailable == true

    private fun checkPersonalAudio(song: Song) {
        val repository = personalAudio ?: return
        if (personalAudioJob?.isActive == true) return
        val missing = if (_uiState.value.personalAudio == PracticePersonalAudio.Failed) {
            PracticePersonalAudio.Failed
        } else PracticePersonalAudio.Missing
        _uiState.update { it.copy(personalAudio = PracticePersonalAudio.Checking) }
        personalAudioJob = accountScope.launch {
            val audio = try {
                repository.get(song)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!accountScope.isActive || _uiState.value.song != song) return@launch
            if (audio != null) modelAudio.prepareMetadata(song, _uiState.value.bpm, audio, _uiState.value.currentTick)
            _uiState.update {
                if (it.song != song) it
                else if (audio != null) it.copy(personalAudio = PracticePersonalAudio.Ready).withModelAudio(modelAudio.state.value)
                else it.copy(personalAudio = missing, modelPerformance = null, sheetPerformanceBpm = null)
            }
        }
    }

    fun downloadPersonalAudio() {
        if (!accountScope.isActive || personalAudioJob?.isActive == true) return
        val repository = personalAudio ?: return
        val state = _uiState.value
        val song = state.song ?: return
        if (state.personalAudioControl !in setOf(PracticePersonalAudio.Missing, PracticePersonalAudio.Failed)) return
        _uiState.update { it.copy(personalAudio = PracticePersonalAudio.Preparing) }
        personalAudioJob = accountScope.launch {
            val result = try {
                repository.download(song) {
                    _uiState.update { current ->
                        if (current.song == song && current.personalAudio == PracticePersonalAudio.Preparing) {
                            current.copy(personalAudio = PracticePersonalAudio.Downloading)
                        } else current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SheetPracticeDownload.Failed
            }
            val audio = if (result == SheetPracticeDownload.Saved) try {
                repository.get(song)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } else null
            val latest = _uiState.value
            if (!accountScope.isActive || latest.song != song ||
                latest.personalAudio !in setOf(PracticePersonalAudio.Preparing, PracticePersonalAudio.Downloading)) return@launch
            if (audio != null) modelAudio.prepareMetadata(song, latest.bpm, audio, latest.currentTick)
            _uiState.update { current ->
                if (current.song != song ||
                    current.personalAudio !in setOf(PracticePersonalAudio.Preparing, PracticePersonalAudio.Downloading)
                ) return@update current
                when (result) {
                    SheetPracticeDownload.Saved -> if (audio != null) {
                        current.copy(personalAudio = PracticePersonalAudio.Ready,
                            modelAudioStatus = ModelAudioStatus.Idle).withModelAudio(modelAudio.state.value)
                    } else current.copy(personalAudio = PracticePersonalAudio.Failed)
                    SheetPracticeDownload.UnsupportedPitches -> current.copy(personalAudio = PracticePersonalAudio.UnsupportedPitches)
                    SheetPracticeDownload.Failed -> current.copy(personalAudio = PracticePersonalAudio.Failed)
                    SheetPracticeDownload.VersionChanged -> current.copy(personalAudio = PracticePersonalAudio.VersionChanged)
                }
            }
        }
    }

    // 모범 연주

    fun startModel() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val song = state.song ?: return
        if (state.personalAudio != null && state.personalAudio != PracticePersonalAudio.Ready) return
        if (state.mode !in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm)) return
        if (state.mode == PracticeMode.Model && (state.modelAudioStatus == ModelAudioStatus.Completed ||
                state.position == state.eventCount - 1 && state.currentEventFraction >= 1.0)) {
            restartModel()
            return
        }
        modelResumeAfterTick = if (!modelStartsAtAttack && state.mode == PracticeMode.Model &&
            state.modelAudioStatus in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)) modelTickAtReportedPosition() else null
        modelStartsAtAttack = false
        pauseRhythm()
        stopPreview()
        stopJangdan()
        pauseStep()
        if (state.mode == PracticeMode.Rhythm) _uiState.update { it.toReady() }
        stepJob?.cancel()
        stepClock = null
        _uiState.update { it.startModel().copy(playing = false, eventStartedAt = null) }
        val startTick = if (state.mode == PracticeMode.Model && modelAudio.state.value.audio != null) null else _uiState.value.currentTick
        modelAudio.play(song, state.bpm, startTick)
    }

    fun toggleModel() {
        if (!accountScope.isActive) return
        // 재생 중 −5초·+5초로 새 자리를 불러오는 동안에도 재생 중으로 보고 멈춘다(S15P31E102-807).
        if (_uiState.value.playing || _uiState.value.modelSeekBuffering) modelAudio.pause() else startModel()
    }

    fun restartModel() {
        if (!accountScope.isActive || _uiState.value.personalAudio?.let { it != PracticePersonalAudio.Ready } == true) return
        modelStartsAtAttack = true
        modelResumeAfterTick = null
        stopJangdan()
        val song = _uiState.value.song ?: return
        _uiState.update { it.restartModel().copy(playing = false, eventStartedAt = null) }
        modelAudio.restart(song, _uiState.value.bpm, _uiState.value.currentTick)
    }

    fun stopModel() {
        modelStartsAtAttack = true
        modelResumeAfterTick = null
        pauseRhythm()
        stopJangdan()
        modelAudio.stop()
        _uiState.update { it.stopModel().copy(modelAudioStatus = ModelAudioStatus.Idle) }
    }

    // 평가 연주

    /** 마친 녹음을 분석 화면에 넘겼는지([takeForEvaluation]). */
    private var handedToAnalysis = false

    /**
     * 연주 평가([PracticeUiState.direct])의 녹음을 분석에 넘긴 뒤 이 화면으로 돌아왔는지. 분석·결과에서 기기 뒤로 가기로 돌아와도
     * 평가 대기 상태를 다시 보이지 않고 들어온 화면으로 돌아갈지 판단한다. 기기 권한 창이 닫힐 때의 화면 복귀는 해당하지 않는다.
     * 구간 재평가는 결과에서 돌아오면 같은 구간을 다시 평가할 수 있게 평가 대기 상태로 남는다.
     */
    fun leavesDirectOnReturn(): Boolean {
        val state = _uiState.value
        return state.direct && sectionSource == null && handedToAnalysis && state.mode == PracticeMode.Ready && state.popup == null
    }

    /** 기기 권한 창의 응답을 기다리는 평가 시작. 응답 전에는 다시 눌러도 권한 요청·카운트다운을 겹쳐 시작하지 않는다. */
    private var micRequestJob: Job? = null

    /**
     * '연주 평가 시작'(S15P31E102-733). 서비스 안내 창 없이 기기(OS) 마이크 권한만 확인한다.
     * 이미 허용돼 있으면 바로 카운트다운, 아직 묻지 않았거나 다시 물을 수 있으면 기기 권한 창을 바로 띄우고 허용될 때만 시작한다.
     * 기기가 더 묻지 않는 차단 상태면 권한 창을 띄울 수 없으므로 설정 안내만 보인다.
     */
    fun requestEvaluation() {
        if (mine || !accountScope.isActive || recorder == null) return
        // 구간에 연주할 율명 음이 없으면(쉼표만) 평가할 것이 없다.
        if (sectionSource != null && sectionForEvaluation() == null) {
            _uiState.update { it.copy(popup = PracticePopup.NoSectionNotes) }
            return
        }
        whenCurrentScore { requestCurrentEvaluation() }
    }

    private fun requestCurrentEvaluation() {
        if (mine || !accountScope.isActive) return
        if (recorder == null) return
        if (micRequestJob?.isActive == true) return
        handedToAnalysis = false
        pauseRhythm()
        stopPreview()
        stopJangdan()
        modelAudio.stop()
        stepJob?.cancel()
        val permission = micPermission
        if (permission == null || permission.isGranted()) {
            settingsStore.grantMic()
            _uiState.update { it.requestEvaluation(micGranted = true) }
            runCountdown()
            return
        }
        _uiState.update { it.requestEvaluation(micGranted = false) }
        if (permission.isPermanentlyDenied()) {
            micDenied()
            return
        }
        micRequestJob = accountScope.launch {
            val allowed = try {
                permission.request()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false // 권한 창을 띄우지 못했거나 기기 마이크를 쓸 수 없으면 거부와 같이 평가를 시작하지 않는다.
            }
            if (allowed) grantMicAndStart() else micDenied()
        }
    }

    private fun whenCurrentScore(start: () -> Unit) {
        if (evaluationCheckJob?.isActive == true) return
        evaluationCheckJob = accountScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (currentScoreForEvaluation()) start()
        }
    }

    /** 녹음 화면의 악보와 현재 공개 판본을 비교한다. 개인 악보·혼자 연습은 기존 흐름을 유지한다. */
    private suspend fun currentScoreForEvaluation(): Boolean {
        val song = _uiState.value.song ?: return false
        if (sectionSource != null) return currentSectionScore(sectionSource, song)
        val version = song.scoreVersion ?: return true
        if (mine || _uiState.value.solo) return true
        val current = try { songRepository.getSong(song.id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        if (!accountScope.isActive || _uiState.value.song?.id != song.id || _uiState.value.song?.scoreVersion != version) return false
        val popup = when {
            current == null -> PracticePopup.ScoreUnavailable
            current.scoreVersion != version -> PracticePopup.ScoreChanged
            else -> return true
        }
        if (_uiState.value.mode == PracticeMode.Recording || _uiState.value.resume) cancelRecorder()
        _uiState.update { it.toReady().copy(popup = popup) }
        return false
    }

    /** 구간 재평가: 바탕 세션의 악보가 지금도 공개 판본인지 확인한다. 바뀌었거나 확인하지 못하면 녹음하지 않고 알린다. */
    private suspend fun currentSectionScore(source: SectionEvaluationSource, song: Song): Boolean {
        val current = try { source.isCurrent() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        if (!accountScope.isActive || _uiState.value.song !== song) return false
        val popup = when (current) {
            true -> return true
            false -> PracticePopup.ScoreChanged
            null -> PracticePopup.ScoreUnavailable
        }
        if (_uiState.value.mode == PracticeMode.Recording || _uiState.value.resume) cancelRecorder()
        _uiState.update { it.toReady().copy(popup = popup) }
        return false
    }

    /**
     * 구간 재평가의 녹음과 함께 분석에 넘길 구간: 구간 안 율명 음의 번호 범위와 바탕 세션. 구간 재평가가 아니거나 구간에 율명 음이 없으면 null.
     */
    fun sectionForEvaluation(): SessionSection? {
        val source = sectionSource ?: return null
        val state = _uiState.value
        val song = state.song ?: return null
        val section = state.sectionPractice?.section ?: return null
        return noteRange(song, section)?.let { SessionSection(it.first, it.last, source.sourceId) }
    }

    /** 혼자 연습 마이크 안내에서 '계속'을 누르면 OS 권한을 묻는다. 허용하면 true. */
    suspend fun requestMicPermission(): Boolean = recorder != null && (micPermission?.request() ?: true)

    /** 혼자 연습 '한 음씩'의 마이크 권한: 앱 안의 '허용'과 OS 권한이 모두 있어야 한다. */
    private fun micGranted(): Boolean = settingsStore.micGranted.value && micPermission?.isGranted() != false

    /** OS 권한까지 허용된 뒤 연주를 시작한다. 혼자 연습이면 한 음씩 듣기를 시작한다. */
    fun grantMicAndStart() {
        if (!accountScope.isActive) return
        if (recorder == null) return
        if (_uiState.value.solo) {
            settingsStore.grantMic()
            closePopup()
            tapStepPlaying()
            return
        }
        pauseRhythm()
        stopPreview()
        stopJangdan()
        modelAudio.stop()
        whenCurrentScore {
            settingsStore.grantMic()
            _uiState.update { it.beginCountdown() }
            runCountdown()
        }
    }

    /**
     * OS 권한을 얻지 못함. 혼자 연습은 화면에 머물며 거부 안내(다시 묻지 못하면 설정 안내)를 띄운다.
     * 연주 평가는 시작하지 않고, 기기가 더 묻지 않는 차단일 때만 설정 안내를 띄운다([denyMic]).
     */
    fun micDenied() {
        if (_uiState.value.popup in setOf(PracticePopup.ScoreChanged, PracticePopup.ScoreUnavailable)) return
        if (_uiState.value.solo) {
            // 한 음씩은 아직 시작하지 않았다. 악보·진행 위치는 그대로 두고 안내만 띄운다.
            _uiState.update { it.copy(popup = if (micPermission?.isPermanentlyDenied() == true) PracticePopup.PermBlocked else PracticePopup.PermDenied) }
            return
        }
        stopAll()
        _uiState.update { it.denyMic(blocked = micPermission?.isPermanentlyDenied() == true) }
    }

    /** ‘설정에서 허용하기’: 기기의 앱 설정을 연다. 열 수 없으면 false. */
    fun openMicSettings(): Boolean = micPermission?.openSettings() == true

    fun closePopup() = _uiState.update { it.copy(popup = null) }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.cancelCountdown() }
    }

    fun askAbort() = pauseRecordingFor(PracticePopup.Abort)

    private fun pauseRecordingFor(popup: PracticePopup) {
        captureStepProgress()
        val state = _uiState.value
        stepJob?.cancel()
        pauseRecorder()
        _uiState.update { it.askAbort(state.currentEventFraction.toFloat(), state.eventElapsedMs).copy(popup = popup) }
    }

    /** '계속 연주': 3·2·1 뒤 멈춘 자리부터. */
    fun resumeRecording() {
        if (!accountScope.isActive) return
        if (recorder == null) return
        _uiState.update { it.beginCountdown(resume = true) }
        runCountdown()
    }

    /**
     * '처음부터'. 연주 중이면 연주 중단·설정을 열 때처럼 녹음·진행을 그 자리에서 멈추고 확인 창을 띄운다.
     * 이미 멈춰 있으면 창만 바꾼다. '계속 연주'는 [keepPlayingAfterExit], '처음부터 시작'은 [restartNow].
     */
    fun askRestart() {
        val state = _uiState.value
        if (state.mode == PracticeMode.Recording && !state.finished && state.eventStartedAt != null) {
            pauseRecordingFor(PracticePopup.Restart)
        } else {
            _uiState.update { it.copy(popup = PracticePopup.Restart) }
        }
    }

    fun restartNow() {
        if (!accountScope.isActive) return
        if (recorder == null) return
        stepJob?.cancel()
        cancelRecorder()
        _uiState.update { it.beginCountdown() }
        runCountdown()
    }

    fun toReady() {
        stopAll()
        _uiState.update { it.toReady() }
    }

    /**
     * 뒤로 가기. 연주·카운트다운 중이면 나갈지 묻고 false를 돌려준다.
     * 묻는 동안은 설정을 열 때처럼 녹음·진행을 멈추고, 3·2·1은 세지 않는다. '계속 연주'는 [keepPlayingAfterExit].
     */
    fun onBack(): Boolean {
        val state = _uiState.value
        when {
            state.mode == PracticeMode.Countdown -> {
                countdownJob?.cancel()
                _uiState.update { it.copy(popup = PracticePopup.Exit) }
            }
            state.mode == PracticeMode.Recording && !state.finished && state.popup == null -> pauseRecordingFor(PracticePopup.Exit)
            state.mode == PracticeMode.Recording -> _uiState.update { it.copy(popup = PracticePopup.Exit) }
            else -> {
                stopAll()
                return true
            }
        }
        return false
    }

    /** 나가기·처음부터 확인 창의 '계속 연주': 멈춘 연주는 3·2·1 뒤 멈춘 자리부터, 멈춘 카운트다운은 3부터 다시 센다. */
    fun keepPlayingAfterExit() {
        val state = _uiState.value
        when {
            state.mode == PracticeMode.Countdown -> {
                if (!accountScope.isActive || recorder == null) return closePopup()
                _uiState.update { it.beginCountdown(resume = state.resume) }
                runCountdown()
            }
            state.mode == PracticeMode.Recording && !state.finished && state.eventStartedAt == null -> resumeRecording()
            else -> closePopup()
        }
    }

    /** 화면을 벗어남(앱 전환·전화 등). 연주 중이면 끊긴 것으로 처리하고, 모범 연주는 일시정지한다. */
    fun onLeaveScreen() {
        evaluationCheckJob?.cancel()
        micRequestJob?.cancel()
        pauseRhythm()
        stopPreview()
        stopJangdan()
        val state = _uiState.value
        when (state.mode) {
            PracticeMode.Recording, PracticeMode.Countdown -> {
                stopAll()
                pitchTally.value = LivePitchTally()
                _uiState.update { it.interrupt() }
            }
            PracticeMode.Model -> modelAudio.suspendAndRelease()
            else -> pauseStep()
        }
    }

    /** 타이머를 모두 멈추고 진행 중인 녹음을 버린다. 마친 녹음 파일은 남긴다. */
    fun stopAll() {
        evaluationCheckJob?.cancel()
        micRequestJob?.cancel()
        pauseRhythm()
        stopPreview()
        stopJangdan()
        modelAudio.stop()
        captureStepProgress()
        stepJob?.cancel()
        stepClock = null
        _uiState.update { it.copy(eventStartedAt = null, pausedFraction = it.currentEventFraction.toFloat()) }
        countdownJob?.cancel()
        previewJob?.cancel()
        listenJob?.cancel()
        cancelRecorder()
        pauseStep()
    }

    private fun runCountdown() {
        countdownJob?.cancel()
        countdownJob = accountScope.launch {
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
            val state = _uiState.value
            // 녹음 범위와 같이 구간 재평가일 때만 구간 최저음을 대금 하한 기준으로 쓴다(S15P31E102-892).
            state.song?.let { pitchSession.start(it, state.bpm, state.elapsedMs, state.sectionEvents.takeIf { state.sectionEvaluation }) }
        }
    }

    // 한 음씩 따라 불기

    /** 설정 옆 '한 음씩' 토글. 모범 연주 중이면 음원을 멈추고 듣던 자리부터 이어 갈 수 있게 멈춤으로 둔다. */
    fun toggleStep() {
        val before = _uiState.value
        if (!before.solo || (before.mode != PracticeMode.Ready && before.mode != PracticeMode.Model)) return
        if (before.mode == PracticeMode.Model) modelAudio.stop()
        _uiState.update { it.toggleStep() }
        syncStep()
    }

    /**
     * 오른쪽 '내 연주' 칸: 듣는 중이면 멈추고, 멈춰 있으면 카운트다운 없이 그 자리부터 바로 이어 간다.
     * 마이크로 듣는 판정기인데 마이크 권한이 없으면 시작하지 않고 권한 안내를 띄운다.
     */
    fun tapStepPlaying() {
        val state = _uiState.value
        val starting = state.stepUi && state.mode == PracticeMode.Ready && state.step.phase != StepPhase.Listening
        if (starting && stepJudge is PitchStepNoteJudge && !micGranted()) {
            _uiState.update { it.copy(panel = null, popup = PracticePopup.Permission) }
            return
        }
        _uiState.update { it.tapStepPlaying() }
        syncStep()
    }

    /** 왼쪽 '지금 음' 칸: 지금 기다리는 음을 들려준다. 이 버튼에는 아직 예시음을 연결하지 않았다. */
    fun listenStepNote() {
        val state = _uiState.value
        val yul = state.stepShownIndex?.let { state.song?.timeline?.getOrNull(it) }?.note?.fingeringYul ?: return
        listen(yul)
    }

    /**
     * 판정 연결 지점: 기다리던 음 [index]를 맞게 불었음. [stepJudge]가 부르고, 대역([ManualStepNoteJudge])일 때는 화면의 임시 버튼이 부른다.
     * 이미 다른 음을 기다리거나 멈춘 뒤에 도착한 판정은 버린다.
     */
    fun onStepNoteMatched(index: Int) {
        _uiState.update { it.matchStepNote(index) }
        syncStep()
    }

    /** 판정 대역의 임시 버튼: 지금 음을 맞게 분 것으로 넘긴다. 스스로 판정하는 판정기에서는 쓰지 않는다. */
    fun confirmStepNoteManually() {
        if (!_uiState.value.step.manualConfirm) return
        onStepNoteMatched(_uiState.value.step.target)
    }

    private fun pauseStep() {
        if (!_uiState.value.stepBusy) return
        _uiState.update { it.pauseStep() }
        syncStep()
    }

    /**
     * 곡 악기의 한 음씩 판정 기준을 받아 마이크로 듣는 판정기로 바꾼다. 서버에 기준이 없는 악기(404)면 한 음씩을 숨기고,
     * 기준을 받지 못했거나 이 기기·악보에서 판정할 수 없으면 임시 버튼 대역을 그대로 둔다.
     */
    private fun prepareStepJudge(song: Song) {
        val repository = notePractice ?: return
        if (!_uiState.value.solo) return
        val instrumentId = song.instrumentId ?: return
        accountScope.launch {
            when (val load = repository.criteria(instrumentId)) {
                is NotePracticeCriteriaLoad.Found -> {
                    val gauge = stepGaugeState?.let { state -> { value: StepPitchGauge? -> state.value = value } }
                    val judge = pitchStepJudgeFor(song, load.criteria, recorder, accountScope, gauge, ::onStepJudgeFailed) ?: return@launch
                    pauseStep()
                    stepJudge.stop()
                    stepJudge = judge
                    _uiState.update { it.copy(step = it.step.copy(manualConfirm = false)) }
                }
                NotePracticeCriteriaLoad.Unsupported -> {
                    pauseStep()
                    stepJudge.stop()
                    _uiState.update { it.copy(step = PracticeStep(available = false, manualConfirm = it.step.manualConfirm)) }
                }
                NotePracticeCriteriaLoad.Failed -> Unit
            }
        }
    }

    /** 판정기가 마이크를 켜지 못함. 그 자리에서 멈추고 녹음 실패 안내를 띄운다. */
    private fun onStepJudgeFailed() {
        _uiState.update { it.pauseStep().copy(popup = PracticePopup.RecordFailed) }
        syncStep()
    }

    /** 상태에 맞춰 판정기를 켜고 끈다. 듣는 중이 아니면 마이크 자원을 놓는다. */
    private fun syncStep() {
        val state = _uiState.value
        val event = state.song?.timeline?.getOrNull(state.step.target)
        if (state.stepUi && state.step.phase == StepPhase.Listening && event != null) {
            stepJudge.listen(StepTarget(event.index, event.note, event.sigimsae.isNotEmpty())) { index -> viewModelScope.launch { onStepNoteMatched(index) } }
        } else {
            stepJudge.stop()
        }
    }

    // 녹음

    /** 곡 끝이나 녹음 한도에서 마친 연주를 분석으로 넘긴다. 저장된 녹음이 없으면 화면에 실패를 알린다. */
    fun takeForEvaluation(): RecordedTake? {
        if (!accountScope.isActive) return null
        val state = _uiState.value
        if (!state.finished || (state.popup != PracticePopup.End && state.popup != PracticePopup.EndAtLimit)) return null
        if (take == null) _uiState.update { it.failRecording() } else handedToAnalysis = true
        return take
    }

    private fun startRecorder(): Boolean {
        val recorder = recorder ?: return false
        take = null
        takeKey = newKey()
        takeScoreVersion = _uiState.value.song?.scoreVersion
        if (runCatching { recorder.start() }.isFailure) return false
        recordingClock.start()
        watchRecording()
        return true
    }

    private fun resumeRecorder(): Boolean {
        val recorder = recorder ?: return false
        if (runCatching { recorder.resume() }.isFailure) {
            cancelRecorder()
            return false
        }
        recordingClock.resume()
        watchRecording()
        return true
    }

    /** 녹음을 멈추거나 마쳐 음높이 결과가 끊길 때 [before]번보다 앞의 음 판정을 확정한다. 기본은 지금 음 앞까지다. */
    private fun confirmPitchBefore(before: Int = _uiState.value.position) {
        val song = _uiState.value.song ?: return
        pitchTally.update { it.confirmBefore(song, before) }
    }

    private fun pauseRecorder() {
        pitchSession.stop()
        confirmPitchBefore()
        val recorder = recorder ?: return
        levelJob?.cancel()
        recordingClock.pause()
        runCatching { recorder.pause() }
        _uiState.update { it.copy(micLevel = 0f) }
    }

    private fun cancelRecorder() {
        pitchSession.stop()
        confirmPitchBefore()
        levelJob?.cancel()
        recordingClock.reset()
        recorder?.cancel()
    }

    /** 곡 끝이나 최대 길이에서 녹음을 마치고 저장된 파일을 확인한다. */
    private fun finishRecorder() {
        pitchSession.stop()
        confirmPitchBefore(_uiState.value.song?.timeline?.size ?: 0)
        levelJob?.cancel()
        recordingClock.reset()
        val audio = recorder?.stop()
        take = audio?.takeUnless { it.tooLarge }?.let { RecordedTake(it.path, takeKey, takeScoreVersion) }
        _uiState.update { it.checkRecording(saved = audio != null, tooLarge = audio?.tooLarge == true) }
    }

    /** 녹음하는 동안 입력 크기와 무입력 시간을 반영하고, 최대 길이(10분)에 닿으면 연주를 마친다. */
    private fun watchRecording() {
        val recorder = recorder ?: return
        levelJob?.cancel()
        levelJob = accountScope.launch {
            var measuredAt = recordingClock.elapsed()
            while (isActive) {
                val elapsed = recordingClock.elapsed()
                if (elapsed >= maxRecording) {
                    captureStepProgress()
                    stepJob?.cancel()
                    _uiState.update { it.endRecording(atLimit = true) }
                    finishRecorder()
                    break
                }
                if (recorder.interrupted) {
                    // 시스템이 녹음을 끊었다. 연주 시계만 흐르는 녹음을 보내지 않도록 화면 이탈과 같이 처리한다.
                    stopAll()
                    _uiState.update { it.interrupt() }
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
        var clock = PracticeTimelineClock(playbackTimeSource, carryMs, _uiState.value.elapsedMs)
        stepClock = clock
        stepJob = accountScope.launch {
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
                                .advance(0.0, micLevel = it.micLevel)
                        }
                        clock.completeEvent(duration)
                        if (_uiState.value.mode == PracticeMode.Rhythm && (_uiState.value.passedSectionEnd ||
                                _uiState.value.sectionEvents != null && !_uiState.value.playing)) {
                            // 구간 연습: 구간 끝에서 구간 처음으로 돌아가 멈춘다.
                            val stopped = _uiState.value.stopAtSectionStart()
                            val song = stopped.song
                            val startTick = song?.timeline?.getOrNull(stopped.position)?.startTick?.toDouble()
                            val timing = song?.score?.timing
                            val startMs = if (startTick != null && timing != null) startTick * 60_000.0 / stopped.bpm / timing.ticksPerBeat else stopped.elapsedMs
                            _uiState.value = stopped.copy(elapsedMs = startMs)
                            stopJangdan()
                            break
                        }
                        val next = _uiState.value
                        if (next.mode == PracticeMode.Recording && next.finished) finishRecorder()
                        val running = (next.mode in setOf(PracticeMode.Model, PracticeMode.Rhythm) && next.playing) || (next.mode == PracticeMode.Recording && !next.finished)
                        if (!running) { if (next.mode == PracticeMode.Rhythm) stopJangdan(); break }
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
        stepJudge.stop()
        modelAudio.release()
        jangdanAudio.release()
    }

    private companion object {
        const val LEVEL_INTERVAL_MS = 100L
    }
}
