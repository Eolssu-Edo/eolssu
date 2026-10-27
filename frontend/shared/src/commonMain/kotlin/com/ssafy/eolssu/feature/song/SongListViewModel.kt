package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongListViewModel(private val songRepository: SongRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val songs = songRepository.getSongs()
            _uiState.update { it.copy(songs = songs) }
        }
    }

    fun changeQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun clearQuery() = changeQuery("")

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    fun resetFilters() = _uiState.update { it.copy(query = "", filter = SongLevelFilter.All) }
}
