package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import com.ssafy.eolssu.data.song.PublishedSheetCopyRepository
import com.ssafy.eolssu.data.song.PublishedSheetCopyResult
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 불러올 악보 고르기. 악기를 고르면 그 악기의 제공 곡(CAT-01 `instrumentId`)과 내 악보를 받는다.
 * 내 악보는 목록(SHT-01)의 악기 코드로 거르고, 코드가 없으면 공통 악보(SHT-08)를 조회한다.
 * 악보를 읽지 못한 내 악보는 어느 악기 목록에도 넣지 않고 수만 알린다.
 */
class EditPickViewModel(
    private val mySheetRepository: MySheetRepository,
    private val songRepository: SongRepository,
    private val documents: SheetDocumentRepository? = null,
    private val copies: PublishedSheetCopyRepository? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditPickUiState())
    val uiState: StateFlow<EditPickUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val copyKeys = mutableMapOf<String, String>()

    /** 내 악보 ID·수정 시각 → 공통 악보의 악기 code. 읽지 못했으면 null. 같은 화면에서 악기를 바꿔도 다시 받지 않는다. */
    private val sheetInstruments = mutableMapOf<Pair<String, Any>, String?>()

    private val accountScope = member.screenScope(viewModelScope) {
        copyKeys.clear()
        sheetInstruments.clear()
        _uiState.value = EditPickUiState()
    }

    fun selectInstrument(instrument: Instrument) {
        val state = _uiState.value
        if (!accountScope.isActive || state.copying) return
        if (state.instrument == instrument && state.load != EditPickLoad.Failed) return
        _uiState.update { it.chooseInstrument(instrument) }
        loadJob?.cancel()
        loadJob = accountScope.launch { load(instrument) }
    }

    fun retry() {
        val instrument = _uiState.value.instrument ?: return
        selectInstrument(instrument)
    }

    private suspend fun load(instrument: Instrument) {
        try {
            val instrumentId = songRepository.getInstruments().firstOrNull { it.code == instrument.serverCode }?.instrumentId
            val songs = if (instrumentId == null) emptyList() else songRepository.getSongs(instrumentId).mapNotNull { song ->
                song.level?.let { EditPickSong(song.id, song.title, it, song.jangdanName) }
            }
            mySheetRepository.refresh()
            val all = mySheetRepository.sheets.value
            val codes = all.associate { it.id to instrumentCodeOf(it) }
            _uiState.update {
                if (it.instrument != instrument) it else it.copy(
                    load = EditPickLoad.Ready,
                    songs = songs,
                    sheets = all.filter { sheet -> codes[sheet.id] == instrument.serverCode },
                    uncheckedSheets = codes.values.count { code -> code == null },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _uiState.update { if (it.instrument != instrument) it else it.copy(load = EditPickLoad.Failed) }
        }
    }

    private suspend fun instrumentCodeOf(sheet: MySheet): String? {
        if (!sheet.instrumentCode.isNullOrBlank()) return sheet.instrumentCode

        val repository = documents ?: return null
        val key = sheet.id to sheet.updatedAt
        if (key in sheetInstruments) return sheetInstruments[key]
        val document = try { repository.get(sheet.id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        val code = (document?.draft?.score ?: document?.score)?.score?.instrument?.takeIf { it.isNotBlank() }
        if (document != null) sheetInstruments[key] = code
        return code
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
