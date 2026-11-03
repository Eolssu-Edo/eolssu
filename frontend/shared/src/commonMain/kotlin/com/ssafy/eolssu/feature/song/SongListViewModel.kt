package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class SongListViewModel(private val songRepository: SongRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0

    init { retry() }

    fun selectInstrument(instrumentId: String?) {
        val state = _uiState.value
        if (instrumentId == state.instrumentId ||
            (instrumentId != null && state.instruments.none { it.instrumentId == instrumentId })) return
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(instrumentId = instrumentId, songs = emptyList(), selectedSongId = null,
            songPicked = false, loadState = SongLoadState.Loading) }
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        val generation = ++loadGeneration
        val instrumentId = _uiState.value.instrumentId
        _uiState.update { it.copy(loadState = SongLoadState.Loading) }
        loadJob = viewModelScope.launch {
            try {
                val instruments = songRepository.getInstruments()
                val songs = songRepository.getSongs(instrumentId)
                if (generation == loadGeneration) _uiState.update { it.copy(instruments = instruments).receiveSongs(songs) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration) _uiState.update { it.copy(loadState = SongLoadState.Failed) }
            }
        }
    }

    fun changeQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun clearQuery() = changeQuery("")

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    /** 세로 목록에서 곡 상세로 가는 곡을 고른다. 가로로 바뀌면 오른쪽에 보인다. */
    fun selectSong(songId: String) = _uiState.update { it.selectSong(songId) }

    /** 태블릿 가로 두 칸 배치에서 오른쪽에 보일 곡을 직접 고른다. */
    fun pickSong(songId: String) = _uiState.update { it.pickSong(songId) }

    fun openPickedSong() = _uiState.update { it.openPickedSong() }

    fun resetFilters() {
        _uiState.update { it.copy(query = "", filter = SongLevelFilter.All) }
        selectInstrument(null)
    }
}
