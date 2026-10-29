package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.SongsUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MySongsViewModel(
    private val songRepository: SongRepository,
    private val mySheetRepository: MySheetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MySongsUiState())
    val uiState: StateFlow<MySongsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 곡 목록을 받지 못하면 내 악보만 보인다.
            val songs = try {
                songRepository.getSongs()
            } catch (e: SongsUnavailableException) {
                emptyList()
            }
            _uiState.update { it.copy(songs = songs) }
        }
        viewModelScope.launch {
            mySheetRepository.sheets.collect { sheets -> _uiState.update { it.receiveSheets(sheets) } }
        }
    }

    fun selectTab(tab: MySongsTab) = _uiState.update { it.copy(tab = tab) }

    fun openAddSheet() = _uiState.update { it.copy(addSheetOpen = true) }

    fun closeAddSheet() = _uiState.update { it.copy(addSheetOpen = false) }

    fun leaveForPdfUpload() = _uiState.update { it.leaveForPdfUpload() }

    fun returnToScreen() = _uiState.update { it.returnToScreen() }

    fun consumeSavedNotice() = _uiState.update { it.copy(savedSheet = null) }
}
