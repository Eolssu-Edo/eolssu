package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class MySongsViewModel(
    private val songRepository: SongRepository,
    private val mySheetRepository: MySheetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MySongsUiState())
    val uiState: StateFlow<MySongsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun selectInstrument(instrumentId: String?) {
        val state = _uiState.value
        if (instrumentId == state.instrumentId ||
            (instrumentId != null && state.instruments.none { it.instrumentId == instrumentId })) return
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(instrumentId = instrumentId, songs = emptyList(), loadState = SongLoadState.Loading) }
        retry()
    }

    init {
        retry()
        viewModelScope.launch {
            mySheetRepository.sheets.collect { sheets -> _uiState.update { it.receiveSheets(sheets) } }
        }
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
                if (generation == loadGeneration) _uiState.update {
                    it.copy(instruments = instruments, songs = songs, loadState = SongLoadState.Ready)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration) _uiState.update { it.copy(loadState = SongLoadState.Failed) }
            }
        }
    }

    fun selectTab(tab: MySongsTab) = _uiState.update { it.copy(tab = tab) }

    fun openAddSheet() = _uiState.update { it.copy(addSheetOpen = true) }

    fun closeAddSheet() = _uiState.update { it.copy(addSheetOpen = false) }

    fun leaveForPdfUpload() = _uiState.update { it.leaveForPdfUpload() }

    fun returnToScreen() = _uiState.update { it.returnToScreen() }

    fun consumeSavedNotice() = _uiState.update { it.copy(savedSheet = null) }
}
