package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** SONG-02 상태. 곡을 불러오기 전에는 null이다. */
data class SongDetailUiState(val song: Song? = null)

class SongDetailViewModel(songId: String, songRepository: SongRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { _uiState.value = SongDetailUiState(songRepository.getSong(songId)) }
    }
}
