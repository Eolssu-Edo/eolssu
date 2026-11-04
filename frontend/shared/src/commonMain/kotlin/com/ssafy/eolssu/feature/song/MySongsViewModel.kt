package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.MySheet
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
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MySongsUiState())
    val uiState: StateFlow<MySongsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = MySongsUiState()
    }

    private fun reloadForInstrument() {
        if (!accountScope.isActive) return
        ++loadGeneration
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(instrumentId = null, songs = emptyList(),
            loadState = SongLoadState.Loading) }
        retry()
    }

    init {
        accountScope.launch { instrumentRepository.instrument.collect { reloadForInstrument() } }
        accountScope.launch {
            mySheetRepository.sheets.collect { sheets -> _uiState.update { it.receiveSheets(sheets) } }
        }
    }

    fun retry() {
        if (!accountScope.isActive) return
        if (loadJob?.isActive == true) return
        val generation = ++loadGeneration
        val instrument = instrumentRepository.instrument.value
        _uiState.update { it.copy(loadState = SongLoadState.Loading) }
        loadJob = accountScope.launch {
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

    private var tabChosen = false

    fun selectTab(tab: MySongsTab) {
        if (!accountScope.isActive) return
        tabChosen = true
        _uiState.update { it.copy(tab = tab) }
    }

    /** 홈 '악보 추가·편집하기'로 들어오면 내 악보 탭부터 보인다. 화면을 다시 그릴 때 사용자가 고른 탭을 되돌리지 않는다. */
    fun showSheetsFirst() {
        if (!accountScope.isActive) return
        if (!tabChosen) selectTab(MySongsTab.Sheets)
    }

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    /** 휴지통: 삭제를 묻는 창을 연다. */
    fun askDelete(sheet: MySheet) = _uiState.update { it.copy(deleting = sheet) }

    fun cancelDelete() = _uiState.update { it.copy(deleting = null) }

    /** 창의 '삭제': 묻던 악보를 지운다. 목록은 [MySheetRepository.sheets]로 다시 받는다. */
    fun confirmDelete() {
        val sheet = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null) }
        viewModelScope.launch { mySheetRepository.delete(sheet.id) }
    }
}
