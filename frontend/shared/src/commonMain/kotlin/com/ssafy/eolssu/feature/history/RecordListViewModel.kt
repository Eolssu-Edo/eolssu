package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RECORD-01. 화면을 열면 서버에서 목록을 받는다(REC-02). 받지 못하면 '다시 시도'로 다시 받는다.
 *
 * 기록 목록에는 악기가 없어, 악기 필터를 위해 [songs]의 곡 목록(CAT-01)으로 곡마다 악기를 찾는다.
 * 공개 목록에 없는 곡(숨긴 곡 등)은 곡 상세로 한 번만 더 찾고, 그래도 모르면 '전체'에서만 보인다.
 */
class RecordListViewModel(
    justSavedId: String?,
    private val recordRepository: PracticeRecordRepository,
    now: () -> Long,
    member: SignedInMember? = null,
    private val songs: SongRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordListUiState(
            today = KoreanDateTime.fromEpochMillis(now()),
            justSavedId = justSavedId,
            recordsAvailable = recordRepository.available,
        ),
    )
    val uiState: StateFlow<RecordListUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        val current = _uiState.value
        _uiState.value = RecordListUiState(today = current.today, recordsAvailable = recordRepository.available, songInstruments = current.songInstruments)
    }

    init {
        accountScope.launch { recordRepository.records.collect { list -> _uiState.update { it.copy(records = list) } } }
        if (recordRepository.available) retry()
        songs?.let { repo -> viewModelScope.launch { resolveInstruments(repo) } }
    }

    private suspend fun resolveInstruments(repo: SongRepository) {
        val byCatalogId = orEmpty { repo.getInstruments() }
            .mapNotNull { c -> Instrument.entries.firstOrNull { it.serverCode == c.code }?.let { c.instrumentId to it } }
            .toMap()
        fun instrumentOf(instrumentId: String?, code: String?): Instrument? =
            instrumentId?.let(byCatalogId::get) ?: Instrument.entries.firstOrNull { it.serverCode == code }

        val listed = orEmpty { repo.getSongs() }
            .mapNotNull { song -> instrumentOf(song.instrumentId, song.instrumentCode)?.let { song.id to it } }
            .toMap()
        _uiState.update { it.copy(songInstruments = it.songInstruments + listed) }

        val tried = listed.keys.toMutableSet()
        _uiState.map { state -> state.records.map { it.songId }.toSet() }.distinctUntilChanged().collect { songIds ->
            for (songId in songIds - tried) {
                tried += songId
                val song = try { repo.getSong(songId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: continue
                val instrument = instrumentOf(song.instrumentId, song.score.source?.instrument) ?: continue
                _uiState.update { it.copy(songInstruments = it.songInstruments + (songId to instrument)) }
            }
        }
    }

    private suspend fun <T> orEmpty(load: suspend () -> List<T>): List<T> =
        try { load() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    fun retry() {
        if (!accountScope.isActive) return
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        accountScope.launch {
            val ok = recordRepository.refresh()
            _uiState.update { it.copy(loading = false, loadFailed = !ok) }
        }
    }

    fun toggle(songId: String) = _uiState.update { it.toggle(songId) }

    fun changeSort(sort: RecordSort) = _uiState.update { it.copy(sort = sort) }

    /** 악기를 바꾸면 펼침 상태를 처음으로 되돌려, 그 악기에서 가장 최근에 연습한 곡이 펼쳐진다. */
    fun changeInstrument(instrument: Instrument?) = _uiState.update {
        if (it.instrument == instrument) it else it.copy(instrument = instrument, opened = null)
    }

    /** 두 칸 배치에서 오른쪽에 볼 기록을 고른다. */
    fun select(recordId: String) = _uiState.update { it.select(recordId) }

    fun show(recordId: String) = _uiState.update { it.show(recordId) }
}
