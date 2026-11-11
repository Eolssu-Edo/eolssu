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

/** RECORD-01. 곡 머리는 한 번 받고, 펼친 곡의 페이지는 현재 화면에서만 보관한다. */
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
        val target = current.groups.firstOrNull { current.isOpen(it.songId) }?.songId
        val sort = current.sort
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        accountScope.launch(perf.context(if (perfRequested) PerfReason.RETRY else PerfReason.INITIAL)) {
            perfRequested = true
            val first = pageOrNull { recordRepository.loadListPage(target, sort == RecordSort.Oldest, weekOf = current.today) }
            ensureAccountSession()
            if (first == null) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            } else {
                val known = first.songs.mapNotNull { song ->
                    Instrument.entries.firstOrNull { it.serverCode == song.instrumentCode }?.let { song.songId to it }
                }.toMap()
                _uiState.update { state ->
                    val pages = if (sort == state.sort) first.songId?.let { mapOf(it to first.pagesAfter(0)) }.orEmpty() else emptyMap()
                    state.copy(
                        summaries = first.songs,
                        totalRecordCount = first.totalRecordCount,
                        week = first.week,
                        songInstruments = state.songInstruments.filterKeys { id -> first.songs.any { it.songId == id } } + known,
                        pages = pages,
                        loading = false,
                    )
                }
                resolveInstruments()
                loadOpenedSongs()
            }
            if (reloadPending) {
                reloadPending = false
                retry()
            }
        }
    }

    fun toggle(songId: String) {
        if (!accountScope.isActive || _uiState.value.groups.none { it.songId == songId }) return
        _uiState.update { state ->
            val open = state.openSongs
            state.copy(opened = if (songId in open) open - songId else open + songId)
        }
        loadOpenedSongs()
    }

    fun changeSort(sort: RecordSort) {
        if (!accountScope.isActive || _uiState.value.sort == sort) return
        cancelPages()
        _uiState.update { it.copy(sort = sort, pages = emptyMap(), selectedId = it.selectedRecordId) }
        loadOpenedSongs()
    }

    fun changeInstrument(instrument: Instrument?) {
        if (!accountScope.isActive || _uiState.value.instrument == instrument) return
        _uiState.update { it.copy(instrument = instrument, opened = null, selectedId = null, linkedId = null) }
        loadOpenedSongs()
    }

    fun select(recordId: String) {
        if (accountScope.isActive) _uiState.update { it.copy(selectedId = recordId, linkedId = null) }
    }

    /** 상세 경로의 기록은 아직 목록 페이지에 없어도 직접 연다. */
    fun show(recordId: String) {
        if (accountScope.isActive) _uiState.update { it.copy(linkedId = recordId, selectedId = null) }
    }

    fun revealLinkedRecord(record: PracticeRecord) {
        val state = _uiState.value
        if (!accountScope.isActive || record.id != state.linkedId || state.isOpen(record.songId)) return
        _uiState.update { it.copy(opened = it.openSongs + record.songId) }
        loadOpenedSongs()
    }

    fun loadMore(songId: String) {
        val state = _uiState.value
        if (!accountScope.isActive || state.loading || !state.isOpen(songId) || state.groups.none { it.songId == songId }) return
        val existing = state.pages[songId] ?: RecordPages()
        val page = existing.nextPage ?: return
        if (existing.loading) return
        _uiState.update { it.copy(pages = it.pages + (songId to existing.copy(loading = true, failed = false))) }
        pageJobs[songId] = accountScope.launch(perf.context()) {
            val result = pageOrNull { recordRepository.loadListPage(songId, state.sort == RecordSort.Oldest, page) }
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
        state.groups.filter { state.isOpen(it.songId) && it.songId !in state.pages }.forEach { loadMore(it.songId) }
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
            state.copy(pages = state.pages.mapValues { (_, page) ->
                page.copy(records = page.records.map { row ->
                    if (row.id == change.id) row.copy(memo = change.memo, isNew = row.isNew && !change.viewed) else row
                })
            })
        }
    }

    private fun removeRecord(recordId: String) {
        _uiState.update { state ->
            state.copy(
                selectedId = state.selectedId?.takeUnless { it == recordId },
                linkedId = state.linkedId?.takeUnless { it == recordId },
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
