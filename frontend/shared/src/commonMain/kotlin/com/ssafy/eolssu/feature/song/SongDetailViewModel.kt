package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** SONG-02 상태. 곡을 불러오기 전에는 null이다. */
data class SongDetailUiState(val song: Song? = null, val loadState: SongLoadState = SongLoadState.Loading)

class SongDetailViewModel(private val songId: String, private val songRepository: SongRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init { retry() }

    fun retry() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.value = SongDetailUiState()
            try {
                val song = songRepository.getSong(songId)
                _uiState.value = SongDetailUiState(song, if (song == null) SongLoadState.NotFound else SongLoadState.Ready)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = SongDetailUiState(loadState = SongLoadState.Failed)
            }
        }
    }
}
