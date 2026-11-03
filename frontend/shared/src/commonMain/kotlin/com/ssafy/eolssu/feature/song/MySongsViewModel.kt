package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
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
    private val instrumentRepository: InstrumentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MySongsUiState())
    val uiState: StateFlow<MySongsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0

    private fun reloadForInstrument() {
        ++loadGeneration
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(instrumentId = null, songs = emptyList(),
            loadState = SongLoadState.Loading) }
        retry()
    }

    init {
        viewModelScope.launch { instrumentRepository.instrument.collect { reloadForInstrument() } }
        viewModelScope.launch {
            mySheetRepository.sheets.collect { sheets -> _uiState.update { it.receiveSheets(sheets) } }
        }
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        val generation = ++loadGeneration
        val instrument = instrumentRepository.instrument.value
        _uiState.update { it.copy(loadState = SongLoadState.Loading) }
        loadJob = viewModelScope.launch {
            try {
                checkNotNull(instrument)
                val instruments = songRepository.getInstruments()
                val instrumentId = checkNotNull(instruments.firstOrNull { it.code == instrument.serverCode }?.instrumentId)
                if (generation != loadGeneration || instrumentRepository.instrument.value != instrument) return@launch
                _uiState.update { it.copy(instrumentId = instrumentId) }
                val songs = songRepository.getSongs(instrumentId)
                if (generation == loadGeneration && instrumentRepository.instrument.value == instrument) _uiState.update {
                    it.copy(songs = songs, loadState = SongLoadState.Ready)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration && instrumentRepository.instrument.value == instrument) _uiState.update { it.copy(loadState = SongLoadState.Failed) }
            }
        }
    }

    fun selectTab(tab: MySongsTab) = _uiState.update { it.copy(tab = tab) }

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    fun openAddSheet() = _uiState.update { it.copy(addSheetOpen = true) }

    fun closeAddSheet() = _uiState.update { it.copy(addSheetOpen = false) }

    fun leaveForPdfUpload() = _uiState.update { it.leaveForPdfUpload() }

    fun returnToScreen() = _uiState.update { it.returnToScreen() }

    fun consumeSavedNotice() = _uiState.update { it.copy(savedSheet = null) }
}
