package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordListChange
import com.ssafy.eolssu.data.practice.RecordListPage
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RECORD-01. 곡 머리는 한 번 받고, 펼친 곡의 페이지는 현재 화면에서만 보관한다.
 * 화면은 제목이 같은 곡을 한 묶음([RecordSongGroup])으로 보이고, 페이지는 서버 곡 ID마다 받는다.
 */
class RecordListViewModel(
    justSavedId: String?,
    private val recordRepository: PracticeRecordRepository,
    now: () -> Long,
    member: SignedInMember? = null,
    private val songs: SongRepository? = null,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordListUiState(
            today = KoreanDateTime.fromEpochMillis(now()),
            justSavedId = justSavedId,
            linkedId = justSavedId,
            recordsAvailable = recordRepository.available,
        ),
    )
    val uiState: StateFlow<RecordListUiState> = _uiState.asStateFlow()
    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = RecordListUiState(today = _uiState.value.today, recordsAvailable = recordRepository.available)
    }
    private val pageJobs = mutableMapOf<String, Job>()
    private val accountSession = member?.session?.value
    private val triedInstruments = mutableSetOf<String>()
    private var catalogInstruments: Map<String, Instrument>? = null
    private var catalogSongs: Map<String, Instrument>? = null
    private var resolving: Job? = null
    private var reloadPending = false
    private var perfRequested = false

    init {
        accountScope.launch {
            recordRepository.listChanges.collect { change ->
                when (change) {
                    is RecordListChange.Updated -> applyUpdate(change)
                    is RecordListChange.Reload -> {
                        change.deletedId?.let(::removeRecord)
                        if (_uiState.value.loading) reloadPending = true else retry()
                    }
                }
            }
        }
        if (recordRepository.available) retry()
    }

    fun retry() {
        if (!accountScope.isActive || _uiState.value.loading) return
        cancelPages()
        val current = _uiState.value
        val target = current.groups.firstOrNull { current.isOpen(it.key) }?.newestSongId
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        accountScope.launch(perf.context(if (perfRequested) PerfReason.RETRY else PerfReason.INITIAL)) {
            perfRequested = true
            val first = pageOrNull { recordRepository.loadListPage(target, weekOf = current.today) }
            ensureAccountSession()
            if (reloadPending) {
                reloadPending = false
                _uiState.update { it.copy(loading = false) }
                retry()
                return@launch
            }
            if (first == null) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            } else {
                val known = first.songs.mapNotNull { song ->
                    Instrument.entries.firstOrNull { it.serverCode == song.instrumentCode }?.let { song.songId to it }
                }.toMap()
                _uiState.update { state ->
                    state.copy(
                        summaries = first.songs,
                        totalRecordCount = first.totalRecordCount,
                        week = first.week,
                        songInstruments = state.songInstruments.filterKeys { id -> first.songs.any { it.songId == id } } + known,
                        pages = first.songId?.let { mapOf(it to first.pagesAfter(0)) }.orEmpty(),
                        loading = false,
                    )
                }
                resolveInstruments()
                loadOpenedSongs()
            }
        }
    }

    /** 곡 묶음([RecordSongGroup.key])을 펼치거나 접는다. */
    fun toggle(groupKey: String) {
        if (!accountScope.isActive || _uiState.value.groups.none { it.key == groupKey }) return
        _uiState.update { state ->
            val open = state.openGroups
            state.copy(opened = if (groupKey in open) open - groupKey else open + groupKey)
        }
        loadOpenedSongs()
    }

    /** 곡 묶음 안의 날짜([RecordDay.epochDay])를 펼치거나 접는다. */
    fun toggleDay(groupKey: String, epochDay: Long) {
        if (!accountScope.isActive) return
        _uiState.update { state ->
            val closed = state.closedDays[groupKey].orEmpty()
            state.copy(closedDays = state.closedDays + (groupKey to if (epochDay in closed) closed - epochDay else closed + epochDay))
        }
    }

    /** 곡 묶음의 다음 페이지: 다음 페이지가 남았거나 받기에 실패한 곡을 모두 이어 받는다. */
    fun loadMoreGroup(groupKey: String) {
        val state = _uiState.value
        val group = state.groups.firstOrNull { it.key == groupKey } ?: return
        group.songIds.filter { id -> state.pages[id]?.let { it.nextPage != null || it.failed } ?: true }.forEach(::loadMore)
    }

    /** 이름 토글: 곡 묶음을 ㄱ→ㅎ과 ㅎ→ㄱ으로 오간다. 펼침·선택은 그대로 둔다. */
    fun toggleNameOrder() {
        if (accountScope.isActive) _uiState.update { it.copy(nameOrder = it.nameOrder.toggled()) }
    }

    /** 곡 제목 검색. 처음 펼치는 묶음이 바뀔 수 있어 펼친 곡의 페이지를 이어 받는다. */
    fun search(query: String) {
        if (!accountScope.isActive || _uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        loadOpenedSongs()
    }

    fun changeInstrument(instrument: Instrument?) {
        if (!accountScope.isActive || _uiState.value.instrument == instrument) return
        _uiState.update {
            it.copy(instrument = instrument, opened = null, closedDays = emptyMap(), selectedId = null, linkedId = null)
        }
        loadOpenedSongs()
    }

    fun select(recordId: String) {
        if (accountScope.isActive) _uiState.update { it.copy(selectedId = recordId, linkedId = null) }
    }

    fun rememberDetailPreview(recordId: String) {
        if (!accountScope.isActive) return
        _uiState.value.recordForDetail(recordId)?.let { recordRepository.rememberDetailPreview(it, accountSession) }
    }

    /** 상세 경로의 기록은 아직 목록 페이지에 없어도 직접 연다. */
    fun show(recordId: String) {
        if (accountScope.isActive) _uiState.update { it.copy(linkedId = recordId, selectedId = null) }
    }

    fun revealLinkedRecord(record: PracticeRecord) {
        val state = _uiState.value
        if (!accountScope.isActive || record.id != state.linkedId || state.isSongOpen(record.songId)) return
        val key = state.groupOf(record.songId)?.key ?: return
        _uiState.update { it.copy(opened = it.openGroups + key) }
        loadOpenedSongs()
    }

    fun loadMore(songId: String) {
        val state = _uiState.value
        if (!accountScope.isActive || state.loading || !state.isSongOpen(songId)) return
        val existing = state.pages[songId] ?: RecordPages()
        val page = existing.nextPage ?: return
        if (existing.loading) return
        _uiState.update { it.copy(pages = it.pages + (songId to existing.copy(loading = true, failed = false))) }
        pageJobs[songId] = accountScope.launch(perf.context()) {
            val result = pageOrNull { recordRepository.loadListPage(songId, page = page) }
            ensureAccountSession()
            _uiState.update { latest ->
                val current = latest.pages[songId] ?: return@update latest
                val updated = result?.pagesAfter(page, current.records) ?: current.copy(loading = false, failed = true)
                latest.copy(pages = latest.pages + (songId to updated))
            }
        }
    }

    private fun loadOpenedSongs() {
        val state = _uiState.value
        state.groups.filter { state.isOpen(it.key) }.flatMap { it.songIds }.filter { it !in state.pages }.forEach(::loadMore)
    }

    private fun cancelPages() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        _uiState.update { state -> state.copy(pages = state.pages.mapValues { (_, pages) -> pages.copy(loading = false) }) }
    }

    private fun RecordListPage.pagesAfter(page: Int, existing: List<PracticeRecord> = emptyList()) =
        RecordPages((existing + records).distinctBy { it.id }, (page + 1).takeIf { it < totalPages })

    private fun applyUpdate(change: RecordListChange.Updated) {
        _uiState.update { state ->
            fun updated(row: PracticeRecord): PracticeRecord =
                if (row.id == change.id) row.copy(memo = change.memo, isNew = row.isNew && !change.viewed) else row
            state.copy(pages = state.pages.mapValues { (_, page) -> page.copy(records = page.records.map(::updated)) })
        }
    }

    private fun removeRecord(recordId: String) {
        _uiState.update { state ->
            val songId = state.recordForDetail(recordId)?.songId
            val summary = state.summaries.firstOrNull { it.songId == songId }
            state.copy(
                selectedId = state.selectedId?.takeUnless { it == recordId },
                linkedId = state.linkedId?.takeUnless { it == recordId },
                summaries = state.summaries.mapNotNull {
                    when {
                        it != summary -> it
                        it.recordCount > 1 -> it.copy(recordCount = it.recordCount - 1)
                        else -> null
                    }
                },
                totalRecordCount = if (summary == null) state.totalRecordCount else (state.totalRecordCount - 1).coerceAtLeast(0),
                week = null,
                pages = state.pages.mapValues { (_, page) -> page.copy(records = page.records.filterNot { it.id == recordId }) },
            )
        }
    }

    /** 코드가 없거나 모르는 곡만 공개 목록과 개별 곡 상세를 사용한다. */
    private fun resolveInstruments() {
        val repo = songs ?: return
        val state = _uiState.value
        val missing = state.summaries.map { it.songId }.filter { it !in state.songInstruments && it !in triedInstruments }
        if (missing.isEmpty() || resolving?.isActive == true) return
        resolving = accountScope.launch(perf.context()) {
            val byId = catalogInstruments ?: orEmpty { repo.getInstruments() }
                .mapNotNull { item -> Instrument.entries.firstOrNull { it.serverCode == item.code }?.let { item.instrumentId to it } }
                .toMap().also { catalogInstruments = it }
            fun instrumentOf(id: String?, code: String?): Instrument? =
                id?.let(byId::get) ?: Instrument.entries.firstOrNull { it.serverCode == code }
            val listed = catalogSongs ?: orEmpty { repo.getSongs() }
                .mapNotNull { song -> instrumentOf(song.instrumentId, song.instrumentCode)?.let { song.id to it } }
                .toMap().also { catalogSongs = it }

            val fallback = missing.mapNotNull { id ->
                triedInstruments += id
                val instrument = listed[id] ?: pageOrNull { repo.getSong(id) }
                    ?.let { instrumentOf(it.instrumentId, it.score.source?.instrument) }
                instrument?.let { id to it }
            }.toMap()
            ensureAccountSession()
            _uiState.update { latest ->
                val activeIds = latest.summaries.map { it.songId }.toSet()
                latest.copy(songInstruments = fallback.filterKeys { it in activeIds } + latest.songInstruments)
            }
            loadOpenedSongs()
            resolving = null
            resolveInstruments()
        }
    }

    private suspend fun <T> orEmpty(load: suspend () -> List<T>): List<T> = pageOrNull(load).orEmpty()

    private suspend fun <T> pageOrNull(load: suspend () -> T?): T? =
        try { load() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
