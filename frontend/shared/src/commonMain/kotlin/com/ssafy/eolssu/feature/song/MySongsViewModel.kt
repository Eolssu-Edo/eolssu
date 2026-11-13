package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRetry
import com.ssafy.eolssu.data.song.SheetConversionStatus
import kotlinx.coroutines.delay
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    private var watchJob: Job? = null
    private var watching = false
    private val failedDetailAttempts = mutableSetOf<String>()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = MySongsUiState()
        failedDetailAttempts.clear()
        instrumentSelection.clear()
    }

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
        accountScope.launch { instrumentRepository.instrument.collect { reloadForInstrument() } }
        accountScope.launch {
            mySheetRepository.sheets.collect { sheets ->
                _uiState.update { it.receiveSheets(sheets) }
                failedDetailAttempts.retainAll(_uiState.value.failedConversionIdsWithoutDetails.toSet())
                startWatch()
            }
        }
    }

    /**
     * 화면이 보이는 동안 변환 중인 내 악보의 상태(SHT-10)를 `pollAfterMs`마다 다시 묻는다.
     * 하나라도 끝나면(DONE·FAILED·404) 목록을 다시 받아 제목·장단·수정 시각을 맞춘다.
     * 이미 실패한 목록 항목은 원인을 한 번 받으며, 조회 실패는 재진입·목록 새로고침 때 다시 시도한다.
     */
    fun watchConversions() {
        if (!watching) failedDetailAttempts.clear()
        watching = true
        startWatch()
    }

    /** 화면이 가려지면 더 묻지 않는다. 다시 보이면 [watchConversions]로 이어 간다. */
    fun stopWatchingConversions() {
        watching = false
        watchJob?.cancel()
        watchJob = null
    }

    private fun startWatch() {
        val repository = conversionRepository ?: return
        if (!watching || !accountScope.isActive || watchJob?.isActive == true || conversionIdsToQuery().isEmpty()) return
        val job = accountScope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                val ids = conversionIdsToQuery()
                if (ids.isEmpty()) break
                var finished = false
                // 서버가 준 간격 중 가장 짧은 것을 따른다. 아무도 주지 않았으면(응답 없음 포함) 기본 간격이다.
                var wait: Long? = null
                for (id in ids) {
                    val current = _uiState.value
                    if (current.sheets.orEmpty().none { it.id == id }) continue
                    val failureDetailsOnly = id in current.failedConversionIdsWithoutDetails
                    val lookup = try { repository.status(id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { SheetConversionLookup.Unavailable }
                    if (!isActive) return@launch
                    if (_uiState.value.sheets.orEmpty().none { it.id == id }) continue
                    if (failureDetailsOnly) failedDetailAttempts += id
                    when (lookup) {
                        is SheetConversionLookup.Found -> {
                            val conversion = lookup.conversion
                            _uiState.update { it.copy(conversions = it.conversions + (id to conversion)) }
                            if (conversion.status.inProgress) {
                                val next = conversion.pollAfterMs ?: CONVERSION_POLL_DEFAULT_MS
                                wait = wait?.let { minOf(it, next) } ?: next
                            }
                            else if (!failureDetailsOnly || conversion.status != SheetConversionStatus.Failed) finished = true
                        }
                        SheetConversionLookup.NotFound -> {
                            _uiState.update { it.copy(conversions = it.conversions - id) }
                            finished = true
                        }
                        SheetConversionLookup.Unavailable -> Unit
                    }
                }
                if (finished) mySheetRepository.refresh()
                if (conversionIdsToQuery().isEmpty()) break
                delay((wait ?: CONVERSION_POLL_DEFAULT_MS).coerceIn(CONVERSION_POLL_MIN_MS, CONVERSION_POLL_MAX_MS))
            }
        }
        watchJob = job
        job.invokeOnCompletion {
            if (watchJob === job) {
                watchJob = null
                startWatch()
            }
        }
        job.start()
    }

    private fun conversionIdsToQuery(): List<String> = _uiState.value.let { state ->
        state.convertingIds + state.failedConversionIdsWithoutDetails.filterNot { it in failedDetailAttempts }
    }

    /**
     * 실패한 변환의 '다시 시도'(SHT-14). 서버가 다시 시도할 수 있다고 한 악보만 보낸다.
     * 409면 원본이 없어 새 파일로 다시 불러와야 한다고 알리고, 받아 들이면 다시 상태를 묻기 시작한다.
     */
    fun retryConversion(sheet: MySheet) {
        val repository = conversionRepository ?: return
        val state = _uiState.value
        if (!accountScope.isActive || sheet.id in state.retrying || state.conversionOf(sheet)?.retryable != true) return
        _uiState.update { it.copy(retrying = it.retrying + sheet.id, conversionNotice = null) }
        accountScope.launch {
            val result = try { repository.retry(sheet.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SheetConversionRetry.Unavailable }
            _uiState.update { current ->
                val done = current.copy(retrying = current.retrying - sheet.id)
                when (result) {
                    is SheetConversionRetry.Accepted -> done.copy(conversions = done.conversions + (sheet.id to result.conversion))
                    SheetConversionRetry.NotRetryable -> done.copy(
                        conversions = done.conversions + (sheet.id to (done.conversionOf(sheet) ?: return@update done).copy(retryable = false)),
                        conversionNotice = ConversionNotice.Reimport,
                    )
                    SheetConversionRetry.NotFound -> done.copy(conversions = done.conversions - sheet.id)
                    SheetConversionRetry.Unavailable -> done.copy(conversionNotice = ConversionNotice.RetryFailed)
                }
            }
            when (result) {
                is SheetConversionRetry.Accepted -> startWatch()
                SheetConversionRetry.NotFound -> mySheetRepository.refresh()
                else -> Unit
            }
        }
    }

    fun consumeConversionNotice() = _uiState.update { it.copy(conversionNotice = null) }

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
        failedDetailAttempts.clear()
        sheetJob = accountScope.launch {
            _uiState.update { it.copy(sheetLoadState = SongLoadState.Loading) }
            val ok = mySheetRepository.refresh()
            _uiState.update { it.copy(sheetLoadState = if (ok) SongLoadState.Ready else SongLoadState.Failed) }
            startWatch()
        }
    }

    fun consumeDeleteFailure() = _uiState.update { it.copy(deleteFailed = false) }
}

/** 서버가 `pollAfterMs`를 주지 않을 때의 간격(SHT-10 진행 중 기본값과 같다). */
internal const val CONVERSION_POLL_DEFAULT_MS = 2_000L
private const val CONVERSION_POLL_MIN_MS = 1_000L
private const val CONVERSION_POLL_MAX_MS = 30_000L
