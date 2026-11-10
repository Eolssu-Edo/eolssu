package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LEARN-02. 단계 목록과 단계 상세 화면이 하나의 ViewModel을 함께 쓴다(화면 이동 그래프 `learn` 범위).
 * 단계 목록·완료 표시(LRN-01·03)와 단계 상세(LRN-02)는 서버에서 받는다. 완료 표시는 화면에 먼저 보이고 서버에 남기며,
 * 남기지 못하면 되돌리고 [LearnUiState.saveError]로 알린다.
 */
class LearnViewModel(
    private val progressRepository: LearnProgressRepository,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()
    private val trackJobs = mutableMapOf<GuideInstrument, Job>()
    private val detailJobs = mutableMapOf<Pair<GuideInstrument, Int>, Job>()
    private val saveLock = Mutex()
    private var pendingSaves = 0

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = LearnUiState()
    }

    init {
        loadTrack(_uiState.value.instrument)
        accountScope.launch { progressRepository.saves.drop(1).collect { refreshDone() } }
    }

    /**
     * 다른 화면이 완료 표시를 바꾸면(단계 연습 통과 등) 받아 둔 트랙을 로딩 표시 없이 다시 받는다.
     * 이 화면의 저장이 진행 중이면 먼저 보인 표시가 되돌아가지 않도록 건너뛴다.
     */
    private suspend fun refreshDone() {
        for ((instrument, load) in _uiState.value.tracks) {
            if (pendingSaves > 0) return
            if (load !is LearnTrackLoad.Loaded || trackJobs[instrument]?.isActive == true) continue
            val track = progressRepository.track(instrument.id) ?: continue
            if (pendingSaves > 0) return
            _uiState.update { it.trackLoaded(instrument, track.items.shownStages(), track.items.shownDone()) }
        }
    }

    /** 지금 악기의 단계 목록을 다시 받는다. */
    fun retryTrack() = loadTrack(_uiState.value.instrument, force = true)

    /** 지금 악기의 [stage] 상세 내용을 받는다. 이미 받았거나 받는 중이면 그대로 둔다. [force]면 실패한 것을 다시 받는다. */
    fun loadDetail(stage: Int, force: Boolean = false) {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val instrument = state.instrument
        val lessonId = state.stageOf(stage)?.lessonId ?: return
        val key = instrument to stage
        if (detailJobs[key]?.isActive == true) return
        val current = state.details[instrument]?.get(stage)
        if (current is LearnDetailLoad.Loaded || (current is LearnDetailLoad.Failed && !force)) return
        _uiState.update { it.withDetail(instrument, stage, LearnDetailLoad.Loading) }
        detailJobs[key] = accountScope.launch {
            val detail = progressRepository.lesson(lessonId)?.toStageDetail()
            _uiState.update { it.withDetail(instrument, stage, detail?.let(LearnDetailLoad::Loaded) ?: LearnDetailLoad.Failed) }
        }
    }

    fun clearSaveError() = _uiState.update { it.copy(saveError = null) }

    fun toggleDropdown() = _uiState.update { it.toggleDropdown() }

    fun dismissDropdown() = _uiState.update { it.dismissDropdown() }

    fun selectInstrument(instrument: GuideInstrument) {
        _uiState.update { it.selectInstrument(instrument) }
        loadTrack(instrument)
    }

    /** 지금 악기의 완료 표시를 모두 지운다. 지우지 못한 단계는 되돌린다. */
    fun resetDone() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val instrument = state.instrument
        val cleared = state.stages.filter { it.number in state.doneStages }
        _uiState.update { it.resetDone() }
        cleared.forEach { stage -> save(instrument, stage, completed = false, LearnSaveError.Reset) }
    }

    fun toggleDone(stage: Int) = updateDone(stage) { it.toggleDone(stage) }

    fun confirm(stage: Int) = updateDone(stage) { it.confirm(stage) }

    fun selectStage(stage: Int?) = _uiState.update { it.selectStage(stage) }

    fun toggleShownDone(stage: Int) = updateDone(stage) { it.toggleShownDone(stage) }

    fun viewCard(stage: Int) = _uiState.update { it.viewCard(stage) }

    fun listen(hanja: String) = _uiState.update { it.listen(hanja) }

    fun finishListening(hanja: String) = _uiState.update { it.finishListening(hanja) }

    private fun loadTrack(instrument: GuideInstrument, force: Boolean = false) {
        if (!accountScope.isActive) return
        if (trackJobs[instrument]?.isActive == true) return
        if (!force && _uiState.value.tracks[instrument] is LearnTrackLoad.Loaded) return
        _uiState.update { it.trackLoading(instrument) }
        trackJobs[instrument] = accountScope.launch {
            val track = progressRepository.track(instrument.id)
            val stages = track?.items?.shownStages()
            val done = track?.items.orEmpty().shownDone()
            _uiState.update { it.trackLoaded(instrument, stages, done) }
        }
    }

    /** 완료 표시를 화면에 먼저 바꾸고, 바뀌었으면 서버에 남긴다. */
    private fun updateDone(stage: Int, transform: (LearnUiState) -> LearnUiState) {
        if (!accountScope.isActive) return
        val before = _uiState.value
        val target = before.stageOf(stage) ?: return
        val after = transform(before)
        _uiState.value = after
        val completed = after.isDone(stage)
        if (completed != before.isDone(stage)) save(before.instrument, target, completed, LearnSaveError.Mark)
    }

    /** 빠르게 여러 번 눌러도 누른 순서대로 남도록 한 번에 하나씩 보낸다. */
    private fun save(instrument: GuideInstrument, stage: LearnStage, completed: Boolean, error: LearnSaveError) {
        pendingSaves++
        accountScope.launch {
            val saved = try {
                saveLock.withLock { progressRepository.setCompleted(stage.lessonId, completed) }
            } finally {
                pendingSaves--
            }
            if (!saved) {
                _uiState.update { it.revertDone(instrument, stage.number, !completed, error) }
            }
        }
    }
}
