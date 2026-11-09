package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.UnavailableJangdanSamplePlayer
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.planJangdan
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
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.ModelAudioRepository
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
 * 마이크 권한은 앱 안의 '허용'과 [micPermission](OS 권한)이 모두 있어야 있는 것으로 본다.
 *
 * 화면이 보이는 동안 [timeMeter]로 연습 시간을 잰다. 재생·녹음·장단 미리듣기 상태는 화면 상태에서 읽어 넘기고,
 * 입력·화면 보임은 Route가 알린다. 열린 구간은 30초마다, 닫힌 구간은 닫을 때 [timeQueue]에 남긴다.
 *
 * 혼자 연습의 '한 음씩 따라 불기'는 [stepJudge]가 기다리는 음을 맞게 불었다고 알리면([onStepNoteMatched]) 다음 음으로 넘어간다.
 * 판정은 기기 안에서만 하고 녹음을 보내거나 분석하지 않는다. 곡을 불러오면 [notePractice]로 그 악기의 판정 기준을 받아
 * 마이크로 듣는 판정기([PitchStepNoteJudge])로 바꾼다. 혼자 연습 화면은 평가 녹음을 하지 않으므로 마이크 권한·녹음 실패 안내는
 * 한 음씩 것이다. [mine]은 내 악보 연습(평가 버튼 없음)이다.
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
    mine: Boolean = false,
    stepJudge: StepNoteJudge = ManualStepNoteJudge(),
    member: SignedInMember? = null,
    private val jangdanPlayer: JangdanSamplePlayer = UnavailableJangdanSamplePlayer(),
    private val notePractice: NotePracticeCriteriaRepository? = null,
) : ViewModel() {
    private var stepJudge: StepNoteJudge = stepJudge

    private val _uiState = MutableStateFlow(
        PracticeUiState(solo = solo, mine = mine, step = PracticeStep(manualConfirm = stepJudge.manualConfirm), settings = settingsStore.settings.value, recordingSupported = recorder != null,
            inputMeasured = recorder?.measuresLevel == true, isSongLoading = true),
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
    private var stepCountdownJob: Job? = null
    private var screenShown = false
    private val recordingClock = RecordingClock()
    private var takeKey = ""
    private var take: RecordedTake? = null
    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        pitchSession.stop()
        pitchTally.value = LivePitchTally()
        take = null
        takeKey = ""
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
     * 이번 평가 연주에서 음마다 모은 실시간 판정(S15P31E102-597). 지나간 음은 색을 고정한다. 멈춤·이어 연주·연주 완료 동안 남고,
     * 새 연주를 시작하거나(처음부터 다시 포함) 준비로 돌아가거나 연주 중 화면을 벗어나면 비운다.
     */
    val livePitch: StateFlow<LivePitchTally> = pitchTally.asStateFlow()

    private val jangdanAudio = JangdanSession(jangdanPlayer, accountScope)
    private val modelAudio = ModelAudioSession(modelAudioRepository, audioPlayer, accountScope, playbackTimeSource) { song, bpm ->
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
        val point = song.modelPointAt(audio, playback.mediaPositionMs.toDouble())
        val event = song.timeline[point.position]
        return requireNotNull(event.startTick) + requireNotNull(event.durationTick) * point.fraction
    }

    private fun currentPlan(): JangdanPlan? {
        val state = _uiState.value
        val song = state.song ?: return null
        return song.jangdanPlanAt(state.bpm)
    }

    private fun Song.jangdanPlanAt(bpm: Int): JangdanPlan? {
        val timing = score.timing ?: return null
        return jangdanPlayback?.let { planJangdan(it, bpm, timing.ticksPerBeat) }
    }

    private fun stopJangdan() {
        soundPurpose = null
        modelSoundBpm = null
        jangdanAudio.stop()
    }

    private fun syncModelJangdan() {
        val state = _uiState.value
        if (state.mode != PracticeMode.Model || !state.playing || !state.settings.jangdanSound) {
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
        jangdanAudio.start(currentPlan(), accompanyingModel = true, resumeAfterTick = resumeAfter, startAtTick = startAt, tick = {
            val now = _uiState.value
            val song = now.song
            val clock = now.modelClock
            if (now.mode != PracticeMode.Model || !now.playing || song == null || clock == null) null
            else {
                val mediaMs = clock.anchor.positionNow()
                val endMs = clock.audio.startOffsetMs + (clock.audio.performance?.durationMs ?: song.durationMs(clock.audio.bpm))
                when {
                    mediaMs < clock.audio.startOffsetMs -> -1.0
                    mediaMs >= endMs -> null
                    else -> {
                        val point = song.modelPointAt(clock.audio, mediaMs)
                        val event = song.timeline[point.position]
                        requireNotNull(event.startTick) + requireNotNull(event.durationTick) * point.fraction
                    }
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
            else (stepClock?.sample()?.elapsedMs ?: now.elapsedMs) / plan.msPerTick
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
        jangdanAudio.start(plan, accompanyingModel = false, resumeAfterTick = resumeAfter, tick = {
            val end = song.timeline.last().let { it.startTick!! + it.durationTick!! }
            if (_uiState.value.mode == PracticeMode.Rhythm && _uiState.value.playing)
                stepClock?.sample()?.elapsedMs?.div(plan.msPerTick)?.takeIf { it < end } else null
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
        stopJangdan()
    }

    private fun seekRhythm(deltaMs: Long) {
        val wasPlaying = _uiState.value.playing
        pauseRhythm()
        val state = _uiState.value
        val song = state.song ?: return
        val msPerTick = 60_000.0 / state.bpm / (song.score.timing?.ticksPerBeat ?: return)
        val last = song.timeline.last()
        val target = ((state.currentTick ?: 0.0) + deltaMs / msPerTick)
            .coerceIn(0.0, (last.startTick!! + last.durationTick!!).toDouble())
        val position = song.timeline.indexOfLast { it.startTick!! <= target }.coerceAtLeast(0)
        _uiState.update { it.copy(position = position, eventElapsedMs = (target - song.timeline[position].startTick!!) * msPerTick,
            elapsedMs = target * msPerTick, eventStartedAt = null) }
        rhythmStartsAtAttack = true
        if (wasPlaying && target < last.startTick + last.durationTick) startRhythm()
    }


    init {
        accountScope.launch {
            _uiState.map { it.position }.distinctUntilChanged().collect { position ->
                pitchSession.refresh()
                // 두 음 앞까지 지나간 음은 색을 고정한다. 직전 음은 늦게 도착하는 결과를 더 받는다.
                _uiState.value.song?.let { song -> pitchTally.update { it.freezeBefore(song, position - 1) } }
            }
        }
        accountScope.launch {
            _uiState.map { it.mode == PracticeMode.Ready || (it.mode == PracticeMode.Countdown && !it.resume) }
                .distinctUntilChanged()
                .collect { newTake -> if (newTake) pitchTally.value = LivePitchTally() }
        }
        retryLoadSong()
        accountScope.launch {
            modelAudio.state.collect { playback ->
                _uiState.update { if (it.mode == PracticeMode.Model) it.withModelAudio(playback) else it }
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
        songJob = accountScope.launch {
            try {
                val song = songRepository.getSong(songId)
                when {
                    song == null -> _uiState.update { it.copy(songError = PracticeSongError.NotFound) }
                    song.timeline.isEmpty() -> _uiState.update { it.copy(songError = PracticeSongError.Failed) }
                    else -> {
                        _uiState.update { it.copy(song = song) }
                        _uiState.update { it.copy(jangdanSupported = jangdanPlayer.available && currentPlan() != null) }
                        if (screenShown) startTimeTracking()
                        prepareStepJudge(song)
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
        if (!accountScope.isActive) return
        screenShown = true
        if (_uiState.value.song == null) return
        startTimeTracking()
    }

    private fun startTimeTracking() {
        if (!accountScope.isActive) return
        timeMeter.show(songId)
        watchIdle()
        snapshotJob?.cancel()
        snapshotJob = accountScope.launch {
            while (true) {
                delay(PracticeTimeMeter.SnapshotInterval)
                timeMeter.snapshot()?.let(timeQueue::saveOpen)
            }
        }
    }

    /** 화면이 가려짐(떠남·백그라운드·화면 잠금·탭 숨김). 구간을 닫아 보내고 떠 있던 알림을 내린다. */
    fun onScreenHidden() {
        screenShown = false
        pauseRhythm()
        stopPreview()
        stopJangdan()
        jangdanAudio.release()
        modelAudio.suspendAndRelease()
        pauseStep()
        idleJob?.cancel()
        snapshotJob?.cancel()
        val segment = timeMeter.hide()
        if (accountScope.isActive) timeQueue.close(segment)
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
        idleJob = accountScope.launch {
            while (true) {
                delay(timeMeter.untilReminder() ?: IDLE_RECHECK)
                if (timeMeter.takeReminder()) _uiState.update { it.copy(idleReminder = true) }
            }
        }
    }

    // 악보·패널

    fun selectUnit(unit: ScoreDisplayUnit) = _uiState.update { it.selectUnit(unit) }

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
            PracticePanel.Notation -> settingsStore.update { it.applyNotation(state.draft, song) }
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

    /** 한 음씩 따라 불기에서 재생 연결 전까지 유지하는 표시다. */
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
            val song = _uiState.value.song ?: return
            settingsStore.update { it.applyTempo(_uiState.value.draft, song) }
            _uiState.update { it.copy(settings = settingsStore.settings.value).withModelAudio(modelAudio.state.value) }
            modelAudio.setBpm(_uiState.value.bpm)
            if (close) closePanel()
            return
        }
        captureStepProgress()
        val state = _uiState.value
        val song = state.song ?: return
        val preparingRhythm = soundPurpose == SoundPurpose.Rhythm && jangdanAudio.status.value == JangdanStatus.Loading
        val running = stepJob?.isActive == true || preparingRhythm
        if (state.mode == PracticeMode.Rhythm) pauseRhythm()
        val carry = state.currentEventFraction
        settingsStore.update { it.applyTempo(state.draft, song) }
        _uiState.update {
            val updated = it.copy(settings = settingsStore.settings.value)
            updated.copy(eventElapsedMs = updated.eventDurationMs() * carry, pausedFraction = carry.toFloat())
        }
        if (close) closePanel()
        if (state.mode == PracticeMode.Rhythm) {
            val msPerTick = 60_000.0 / _uiState.value.bpm / song.score.timing!!.ticksPerBeat
            _uiState.update { it.copy(elapsedMs = (it.currentTick ?: 0.0) * msPerTick) }
            if (running) startRhythm()
        } else if (running) startStepping(_uiState.value.eventElapsedMs)
    }

    fun toggleBigScore() = settingsStore.update { it.copy(bigScore = !it.bigScore) }

    /** 연습 설정의 ‘운지법과 함께 보기’. 바로 반영된다(적용 버튼 없음). */
    fun toggleFingeringView() = settingsStore.update { it.copy(fingeringView = !it.fingeringView) }

    /** 연습 설정의 ‘연습 악기’. 운지 그림과 제목 옆 표시에만 쓰고 프로필 악기는 바꾸지 않는다. */
    fun setInstrument(id: String) = settingsStore.update { it.copy(instrumentId = id) }

    /** 플레이어의 −5초·+5초. 모범 연주 중에는 음원 위치를, 준비 상태에서는 고른 음을 옮긴다. */
    fun seekModel(deltaMs: Long) {
        when (_uiState.value.mode) {
            PracticeMode.Model -> {
                modelStartsAtAttack = true
                modelResumeAfterTick = null
                stopJangdan()
                modelAudio.seekBy(deltaMs)
            }
            PracticeMode.Rhythm -> seekRhythm(deltaMs)
            PracticeMode.Ready -> _uiState.update { it.seekSelection(deltaMs) }
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
        if (state.mode == PracticeMode.Model && modelAudio.state.value.audio != null) {
            modelStartsAtAttack = true
            modelResumeAfterTick = null
            stopJangdan()
            modelAudio.seekToFraction(progress)
            return
        }
        val wasPlaying = state.mode == PracticeMode.Rhythm && state.playing
        pauseRhythm()
        val timing = song.score.timing ?: return
        val last = song.timeline.last()
        val endTick = (last.startTick!! + last.durationTick!!).toDouble()
        val target = endTick * progress
        val position = song.timeline.indexOfLast { it.startTick!! <= target }.coerceAtLeast(0)
        val msPerTick = 60_000.0 / state.bpm / timing.ticksPerBeat
        _uiState.update { it.copy(mode = if (it.mode == PracticeMode.Ready) PracticeMode.Model else it.mode,
            position = position, selected = position, selectedCell = state.cellForEvent(position), playing = false, follow = true,
            eventElapsedMs = (target - song.timeline[position].startTick!!) * msPerTick,
            elapsedMs = target * msPerTick, eventStartedAt = null, modelClock = null) }
        modelStartsAtAttack = true
        rhythmStartsAtAttack = true
        if (wasPlaying && progress < 1f) startRhythm()
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
            if (plan == null) null else mark?.elapsedNow()?.inWholeNanoseconds?.div(1_000_000.0)?.div(plan.msPerTick)?.plus(plan.mapping.startTick)
        }, onStarted = {
            mark = playbackTimeSource.markNow()
            _uiState.update { it.copy(draft = it.draft.copy(previewing = true, previewBeat = 0)) }
            previewJob = accountScope.launch {
                while (isActive) {
                    val elapsed = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0
                    val cell = plan!!.mapping.cellAt(plan.mapping.startTick + elapsed / plan.msPerTick)
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

    // 모범 연주

    fun startModel() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val song = state.song ?: return
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
        modelAudio.play(song, state.bpm, _uiState.value.currentTick)
    }

    fun toggleModel() {
        if (!accountScope.isActive) return
        if (_uiState.value.playing) modelAudio.pause() else startModel()
    }

    fun restartModel() {
        if (!accountScope.isActive) return
        modelStartsAtAttack = true
        modelResumeAfterTick = null
        stopJangdan()
        val song = _uiState.value.song ?: return
        _uiState.update { it.restartModel().copy(playing = false, eventStartedAt = null) }
        modelAudio.restart(song, _uiState.value.bpm)
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

    private var entryEvaluationStarted = false

    /** 일반 연습에서 평가로 이동한 경우, 곡 로딩 후 한 번만 권한 확인·카운트다운을 시작한다. */
    fun startEvaluationOnEntry() {
        if (entryEvaluationStarted || _uiState.value.song == null || !_uiState.value.recordingSupported) return
        entryEvaluationStarted = true
        requestEvaluation()
    }

    fun requestEvaluation() {
        if (!accountScope.isActive) return
        if (recorder == null) return
        pauseRhythm()
        stopPreview()
        stopJangdan()
        modelAudio.stop()
        stepJob?.cancel()
        _uiState.update { it.requestEvaluation(micGranted()) }
        if (_uiState.value.mode == PracticeMode.Countdown) runCountdown()
    }

    /** 마이크 안내에서 '계속'을 누르면 OS 권한을 묻는다. 허용하면 true. */
    suspend fun requestMicPermission(): Boolean = recorder != null && (micPermission?.request() ?: true)

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
        settingsStore.grantMic()
        _uiState.update { it.beginCountdown() }
        runCountdown()
    }

    /** OS 권한 창에서 허용하지 않음. 화면에 머물며 거부 안내(다시 묻지 못하면 설정 안내)를 띄운다. */
    fun micDenied() {
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

    fun askRestart() = _uiState.update { it.copy(popup = PracticePopup.Restart) }

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

    /** 나가기 확인 창의 '계속 연주': 멈춘 연주는 3·2·1 뒤 멈춘 자리부터, 멈춘 카운트다운은 3부터 다시 센다. */
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
            state.song?.let { pitchSession.start(it, state.bpm, state.elapsedMs) }
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
     * 오른쪽 '내 연주' 칸: 듣는 중이면 멈추고, 멈춰 있으면 1초마다 3·2·1을 센 뒤 이어 간다.
     * 마이크로 듣는 판정기인데 마이크 권한이 없으면 시작하지 않고 권한 안내를 띄운다.
     */
    fun tapStepPlaying() {
        val state = _uiState.value
        val starting = state.stepUi && state.mode == PracticeMode.Ready && state.step.phase != StepPhase.Listening && state.step.phase != StepPhase.Countdown
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
                    val judge = pitchStepJudgeFor(song, load.criteria, recorder, accountScope, ::onStepJudgeFailed) ?: return@launch
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

    /** 상태에 맞춰 판정기와 3·2·1 타이머를 켜고 끈다. 듣는 중이 아니면 마이크 자원을 놓는다. */
    private fun syncStep() {
        val state = _uiState.value
        if (state.step.phase != StepPhase.Countdown || !state.stepUi) {
            stepCountdownJob?.cancel()
            stepCountdownJob = null
        }
        val event = state.song?.timeline?.getOrNull(state.step.target)
        if (state.stepUi && state.step.phase == StepPhase.Listening && event != null) {
            stepJudge.listen(StepTarget(event.index, event.note, event.sigimsae.isNotEmpty())) { index -> viewModelScope.launch { onStepNoteMatched(index) } }
        } else {
            stepJudge.stop()
        }
        if (state.stepUi && state.step.phase == StepPhase.Countdown && stepCountdownJob?.isActive != true) runStepCountdown()
    }

    private fun runStepCountdown() {
        stepCountdownJob = viewModelScope.launch {
            while (_uiState.value.step.phase == StepPhase.Countdown) {
                delay(StepCountdownTickMs)
                _uiState.update { it.tickStepCountdown() }
            }
            stepCountdownJob = null
            syncStep()
        }
    }

    // 녹음

    /** 정상 종료한 연주에서만 분석으로 넘긴다. 저장된 녹음이 없으면 화면에 실패를 알린다. */
    fun takeForEvaluation(): RecordedTake? {
        if (!accountScope.isActive) return null
        val state = _uiState.value
        if (!state.finished || state.popup != PracticePopup.End) return null
        if (take == null) _uiState.update { it.failRecording() }
        return take
    }

    private fun startRecorder(): Boolean {
        val recorder = recorder ?: return false
        take = null
        takeKey = newKey()
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

    private fun pauseRecorder() {
        pitchSession.stop()
        val recorder = recorder ?: return
        levelJob?.cancel()
        recordingClock.pause()
        runCatching { recorder.pause() }
        _uiState.update { it.copy(micLevel = 0f) }
    }

    private fun cancelRecorder() {
        pitchSession.stop()
        levelJob?.cancel()
        recordingClock.reset()
        recorder?.cancel()
    }

    /** 곡 끝이나 최대 길이에서 녹음을 마치고 저장된 파일을 확인한다. */
    private fun finishRecorder() {
        pitchSession.stop()
        levelJob?.cancel()
        recordingClock.reset()
        val audio = recorder?.stop()
        take = audio?.takeUnless { it.tooLarge }?.let { RecordedTake(it.path, takeKey) }
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
                if (elapsed >= RecordingSpec.MaxDuration) {
                    captureStepProgress()
                    stepJob?.cancel()
                    _uiState.update { it.endRecording() }
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
        val clock = PracticeTimelineClock(playbackTimeSource, carryMs, _uiState.value.elapsedMs)
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
                                .resumeFollowOnNextGak()
                        }
                        clock.completeEvent(duration)
                        val next = _uiState.value
                        if (next.mode == PracticeMode.Recording && next.finished) finishRecorder()
                        val running = (next.mode in setOf(PracticeMode.Model, PracticeMode.Rhythm) && next.playing) || (next.mode == PracticeMode.Recording && !next.finished)
                        if (!running) { if (next.mode == PracticeMode.Rhythm) stopJangdan(); break }
                    } else {
                        _uiState.update { it.copy(eventElapsedMs = sample.eventElapsedMs.coerceAtLeast(0.0),
                            elapsedMs = sample.elapsedMs, eventStartedAt = clock.eventStartedAt).resumeFollowOnNextGak() }
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

        /** 알림을 기다리지 않는 동안(재생 중·이미 알림을 냄) 다시 확인하는 간격. */
        val IDLE_RECHECK = 30.seconds
    }
}
