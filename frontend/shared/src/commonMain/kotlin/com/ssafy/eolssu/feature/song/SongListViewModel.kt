package com.ssafy.eolssu.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class SongListViewModel(
    private val songRepository: SongRepository,
    private val instrumentRepository: InstrumentRepository,
    userRepository: UserRepository,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { instrumentSelection.clear() }

    /** 상단 바 연습 악기 드롭다운. 고른 악기가 바뀌면 아래 [reloadForInstrument]로 곡 목록을 다시 받는다. */
    internal val instrumentSelection: SongInstrumentSelection = SongInstrumentSelection(instrumentRepository, userRepository, accountScope)

    private var loadJob: Job? = null
    private var loadGeneration = 0

    init {
        viewModelScope.launch { instrumentRepository.instrument.collect { reloadForInstrument() } }
    }

    private fun reloadForInstrument() {
        ++loadGeneration
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(instrumentId = null, songs = emptyList(), selectedSongId = null, songPicked = false,
            loadState = SongLoadState.Loading) }
        retry()
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
                if (generation == loadGeneration && instrumentRepository.instrument.value == instrument) _uiState.update { it.copy(instruments = instruments).receiveSongs(songs) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == loadGeneration && instrumentRepository.instrument.value == instrument) _uiState.update { it.copy(loadState = loadFailure(error)) }
            }
        }
    }

    fun changeQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun clearQuery() = changeQuery("")

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    /** 곡 이름 오름차순·내림차순을 바꾼다. */
    fun toggleSort() = _uiState.update { it.copy(sort = it.sort.toggled()) }

    /** 세로 목록에서 곡 상세로 가는 곡을 고른다. 가로로 바뀌면 오른쪽에 보인다. */
    fun selectSong(songId: String) = _uiState.update { it.selectSong(songId) }

    /** 태블릿 가로 두 칸 배치에서 오른쪽에 보일 곡을 직접 고른다. */
    fun pickSong(songId: String) = _uiState.update { it.pickSong(songId) }

    fun openPickedSong() = _uiState.update { it.openPickedSong() }

    fun resetFilters() {
        _uiState.update { it.copy(query = "", filter = SongLevelFilter.All) }
    }
}
