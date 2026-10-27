package com.ssafy.eolssu.feature.guide.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.guide.GuideInstrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** LEARN-02. 단계 목록과 단계 상세 화면이 하나의 ViewModel을 함께 쓴다(화면 이동 그래프 `learn` 범위). */
class LearnViewModel(private val songRepository: SongRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val titles = LearnSongs.map { it.title }.toSet()
            val ids = songRepository.getSongs()
                .filter { it.evaluable && it.title in titles }
                .associate { it.title to it.id }
            _uiState.update { it.copy(songIds = ids) }
        }
    }

    fun toggleDropdown() = _uiState.update { it.toggleDropdown() }

    fun dismissDropdown() = _uiState.update { it.dismissDropdown() }

    fun selectInstrument(instrument: GuideInstrument) = _uiState.update { it.selectInstrument(instrument) }

    fun resetDone() = _uiState.update { it.resetDone() }

    fun toggleDone(stage: Int) = _uiState.update { it.toggleDone(stage) }

    fun confirm(stage: Int) = _uiState.update { it.confirm(stage) }

    fun listen(hanja: String) = _uiState.update { it.listen(hanja) }

    fun finishListening(hanja: String) = _uiState.update { it.finishListening(hanja) }
}
