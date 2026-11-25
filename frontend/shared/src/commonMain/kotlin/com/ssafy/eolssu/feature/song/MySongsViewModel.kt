package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class MySongsViewModel(
    private val songRepository: SongRepository,
    private val mySheetRepository: MySheetRepository,
    private val instrumentRepository: InstrumentRepository,
    userRepository: UserRepository,
    member: SignedInMember? = null,
    private val conversionRepository: SheetConversionRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MySongsUiState())
    val uiState: StateFlow<MySongsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var sheetJob: Job? = null

    private val accountScope: CoroutineScope = member.screenScope(viewModelScope) {
        _uiState.value = MySongsUiState(practiceInstrument = instrumentRepository.instrument.value)
        conversionWatcher.forgetFailedDetails()
        instrumentSelection.clear()
    }

    private val conversionWatcher: MySheetConversionWatcher = MySheetConversionWatcher(
        accountScope, mySheetRepository, conversionRepository,
        read = { _uiState.value.mine },
        write = { change -> _uiState.update { it.copy(mine = change(it.mine)) } },
    )

    /** 상단 바 연습 악기 드롭다운. 고른 악기가 바뀌면 제공 곡 목록을 다시 받는다. */
    internal val instrumentSelection: SongInstrumentSelection = SongInstrumentSelection(instrumentRepository, userRepository, accountScope)

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
        accountScope.launch {
            instrumentRepository.instrument.collect { instrument ->
                // 내 악보는 바뀐 연습 악기의 악보를 먼저 보이고, 펼쳐 둔 다른 악기 묶음은 다시 접는다.
                _uiState.update { it.copy(practiceInstrument = instrument, otherSheetsOpen = false) }
                reloadForInstrument()
            }
        }
        accountScope.launch {
            mySheetRepository.sheets.collect { sheets ->
                _uiState.update { it.receiveSheets(sheets) }
                conversionWatcher.onSheetsReceived()
            }
        }
    }

    /** 화면이 보이는 동안 변환 중인 내 악보의 상태를 묻는다([MySheetConversionWatcher]). */
    fun watchConversions() = conversionWatcher.watch()

    /** 화면이 가려지면 더 묻지 않는다. 다시 보이면 [watchConversions]로 이어 간다. */
    fun stopWatchingConversions() = conversionWatcher.stop()

    /** 실패한 변환의 '다시 시도'(SHT-14). */
    fun retryConversion(sheet: MySheet) = conversionWatcher.retry(sheet)

    fun consumeConversionNotice() = _uiState.update { it.copy(mine = it.mine.copy(notice = null)) }

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
            } catch (error: Exception) {
                if (generation == loadGeneration && instrumentRepository.instrument.value == instrument) _uiState.update { it.copy(loadState = loadFailure(error)) }
            }
        }
    }

    private var tabChosen = false

    fun selectTab(tab: MySongsTab) {
        if (!accountScope.isActive) return
        tabChosen = true
        _uiState.update { it.copy(tab = tab) }
        if (tab == MySongsTab.Sheets) refreshSheets()
    }

    /** 홈 '악보 추가·편집하기'로 들어오면 내 악보 탭부터 보인다. 화면을 다시 그릴 때 사용자가 고른 탭을 되돌리지 않는다. */
    fun showSheetsFirst() {
        if (!accountScope.isActive) return
        if (!tabChosen) selectTab(MySongsTab.Sheets)
    }

    fun selectFilter(filter: SongLevelFilter) = _uiState.update { it.copy(filter = filter) }

    /** 두 탭이 함께 쓰는 제목 검색어를 바꾼다. */
    fun changeQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun clearQuery() = changeQuery("")

    /** 내 악보의 '다른 악기 악보 보기'·'접기'. */
    fun toggleOtherSheets() = _uiState.update { it.copy(otherSheetsOpen = !it.otherSheetsOpen) }

    /** 제공 곡 빈 결과의 초기화: 검색어와 단계를 모두 처음으로 되돌린다. */
    fun resetSongFilters() = _uiState.update { it.copy(query = "", filter = SongLevelFilter.All) }

    /** 제공 곡 이름 오름차순·내림차순을 바꾼다. */
    fun toggleSort() = _uiState.update { it.copy(sort = it.sort.toggled()) }

    /** 휴지통: 삭제를 묻는 창을 연다. */
    fun askDelete(sheet: MySheet) = _uiState.update { it.copy(deleting = sheet) }

    fun cancelDelete() = _uiState.update { it.copy(deleting = null) }

    /** 창의 '삭제': 묻던 악보를 지운다. 목록은 [MySheetRepository.sheets]로 다시 받는다. */
    fun confirmDelete() {
        val sheet = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null, deleteFailed = false) }
        accountScope.launch {
            try { mySheetRepository.delete(sheet.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(deleteFailed = true) } }
        }
    }

    fun refreshSheets() {
        if (!accountScope.isActive || sheetJob?.isActive == true) return
        conversionWatcher.forgetFailedDetails()
        sheetJob = accountScope.launch {
            _uiState.update { it.copy(sheetLoadState = SongLoadState.Loading) }
            val ok = mySheetRepository.refresh()
            _uiState.update { it.copy(sheetLoadState = if (ok) SongLoadState.Ready else SongLoadState.Failed) }
            conversionWatcher.start()
        }
    }

    fun consumeDeleteFailure() = _uiState.update { it.copy(deleteFailed = false) }
}
