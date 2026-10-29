package com.ssafy.eolssu.feature.guide.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.SongsUnavailableException
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.feature.guide.GuideInstrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * LEARN-02. 단계 목록과 단계 상세 화면이 하나의 ViewModel을 함께 쓴다(화면 이동 그래프 `learn` 범위).
 * 완료 표시는 [progressRepository]로 기기에 남겨 앱을 다시 켜도 이어진다.
 */
class LearnViewModel(
    private val songRepository: SongRepository,
    private val progressRepository: LearnProgressRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LearnUiState(done = GuideInstrument.entries.associateWith { progressRepository.doneStages(it.id) }),
    )
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val titles = LearnSongs.map { it.title }.toSet()
            // 곡 목록을 받지 못하면 곡 바로가기 없이 둔다.
            val songs = try {
                songRepository.getSongs()
            } catch (e: SongsUnavailableException) {
                emptyList()
            }
            val ids = songs
                .filter { it.evaluable && it.title in titles }
                .associate { it.title to it.id }
            _uiState.update { it.copy(songIds = ids) }
        }
    }

    fun toggleDropdown() = _uiState.update { it.toggleDropdown() }

    fun dismissDropdown() = _uiState.update { it.dismissDropdown() }

    fun selectInstrument(instrument: GuideInstrument) = _uiState.update { it.selectInstrument(instrument) }

    fun resetDone() = updateDone { it.resetDone() }

    fun toggleDone(stage: Int) = updateDone { it.toggleDone(stage) }

    fun confirm(stage: Int) = updateDone { it.confirm(stage) }

    fun selectStage(stage: Int?) = _uiState.update { it.selectStage(stage) }

    fun toggleShownDone(stage: Int) = updateDone { it.toggleShownDone(stage) }

    fun listen(hanja: String) = _uiState.update { it.listen(hanja) }

    fun finishListening(hanja: String) = _uiState.update { it.finishListening(hanja) }

    /** 완료 표시를 바꾸고 지금 악기의 완료 단계를 기기에 남긴다. */
    private fun updateDone(transform: (LearnUiState) -> LearnUiState) {
        val state = _uiState.updateAndGet(transform)
        progressRepository.saveDoneStages(state.instrument.id, state.doneStages)
    }
}
