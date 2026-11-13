package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import com.ssafy.eolssu.data.song.PublishedSheetCopyRepository
import com.ssafy.eolssu.data.song.PublishedSheetCopyResult
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 악기를 고르면 제공 곡(CAT-01 `instrumentId`)과 내 악보를 각각 받아 탭별로 표시한다.
 * 내 악보는 목록(SHT-01)의 초안 우선 악기 코드로만 거른다. 본문은 편집기에 들어가서 받는다.
 * 악기를 확인하지 못한 내 악보는 어느 악기 목록에도 넣지 않고 수만 알린다.
 */
class EditPickViewModel(
    private val mySheetRepository: MySheetRepository,
    private val songRepository: SongRepository,
    private val copies: PublishedSheetCopyRepository? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditPickUiState())
    val uiState: StateFlow<EditPickUiState> = _uiState.asStateFlow()

    private var songsJob: Job? = null
    private var sheetsJob: Job? = null
    private val copyKeys = mutableMapOf<String, String>()

    private val accountScope = member.screenScope(viewModelScope) {
        copyKeys.clear()
        _uiState.value = EditPickUiState()
    }

    fun selectInstrument(instrument: Instrument) {
        val state = _uiState.value
        if (!accountScope.isActive || state.copying) return
        if (state.instrument == instrument) {
            retry()
            return
        }
        songsJob?.cancel()
        sheetsJob?.cancel()
        _uiState.update { it.chooseInstrument(instrument) }
        loadSongs(instrument)
        loadSheets(instrument)
    }

    fun retry() {
        val state = _uiState.value
        val instrument = state.instrument ?: return
        if (!accountScope.isActive || state.copying || state.load != EditPickLoad.Failed) return
        when (state.tab) {
            EditPickTab.Songs -> loadSongs(instrument)
            EditPickTab.Mine -> loadSheets(instrument)
        }
    }

    private fun loadSongs(instrument: Instrument) {
        _uiState.update { it.copy(songsLoad = EditPickLoad.Loading) }
        songsJob = accountScope.launch {
            try {
                val instrumentId = songRepository.getInstruments().firstOrNull { it.code == instrument.serverCode }?.instrumentId
                currentCoroutineContext().ensureActive()
                val songs = if (instrumentId == null) emptyList() else songRepository.getSongs(instrumentId).mapNotNull { song ->
                    song.level?.let { EditPickSong(song.id, song.title, it, song.jangdanName) }
                }
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(songsLoad = EditPickLoad.Ready, songs = songs) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(songsLoad = EditPickLoad.Failed) }
            }
        }
    }

    private fun loadSheets(instrument: Instrument) {
        _uiState.update { it.copy(sheetsLoad = EditPickLoad.Loading) }
        sheetsJob = accountScope.launch {
            try {
                val listRefreshed = mySheetRepository.refresh()
                currentCoroutineContext().ensureActive()
                if (!listRefreshed) {
                    _uiState.update { it.copy(sheetsLoad = EditPickLoad.Failed) }
                    return@launch
                }
                val all = mySheetRepository.sheets.value
                _uiState.update {
                    it.copy(
                        sheetsLoad = EditPickLoad.Ready,
                        sheets = all.filter { sheet -> sheet.instrumentCode == instrument.serverCode },
                        uncheckedSheets = all.count { sheet -> sheet.instrumentCode.isNullOrBlank() },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(sheetsLoad = EditPickLoad.Failed) }
            }
        }
    }

    fun selectTab(tab: EditPickTab) = _uiState.update {
        if (it.copying || it.tab == tab) it else it.copy(tab = tab, picked = null, copyProblem = null)
    }

    fun search(query: String) = _uiState.update { it.copy(query = query) }

    /** 제공 곡 이름 오름차순·내림차순을 바꾼다. */
    fun toggleSort() = _uiState.update { it.copy(sort = it.sort.toggled()) }

    fun toggle(target: EditPickTarget) = _uiState.update { if (it.copying) it else it.toggle(target) }

    @OptIn(ExperimentalUuidApi::class)
    fun confirmSelection(onOpenSheet: (String) -> Unit) {
        val current = _uiState.value
        if (!accountScope.isActive || !current.canSelect) return
        when (val target = current.picked) {
            is EditPickTarget.Sheet -> onOpenSheet(target.sheetId)
            is EditPickTarget.Song -> {
                val repository = copies
                if (repository == null) {
                    _uiState.update { it.copy(copyProblem = PublishedSheetCopyProblem.Failed) }
                    return
                }
                val key = copyKeys.getOrPut(target.songId) { Uuid.random().toString() }
                _uiState.update { it.copy(copying = true, copyProblem = null) }
                accountScope.launch {
                    val result = try {
                        repository.copy(target.songId, key)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        PublishedSheetCopyResult.Rejected(PublishedSheetCopyProblem.Failed)
                    }
                    if (!accountScope.isActive) return@launch
                    when (result) {
                        is PublishedSheetCopyResult.Created -> {
                            copyKeys.remove(target.songId)
                            _uiState.update { it.copy(copying = false, picked = null) }
                            onOpenSheet(result.sheetId)
                        }
                        is PublishedSheetCopyResult.Rejected ->
                            _uiState.update { it.copy(copying = false, copyProblem = result.problem) }
                    }
                }
            }
            null -> Unit
        }
    }
}
