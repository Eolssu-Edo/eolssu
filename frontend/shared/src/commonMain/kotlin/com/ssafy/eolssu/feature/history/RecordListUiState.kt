package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.RecordSongSummary
import com.ssafy.eolssu.data.practice.RecordWeek

enum class RecordSort { Newest, Oldest }

data class RecordPages(
    val records: List<PracticeRecord> = emptyList(),
    val nextPage: Int? = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

data class RecordListUiState(
    val today: KoreanDateTime = KoreanDateTime(1970, 1, 1, 0, 0),
    val sort: RecordSort = RecordSort.Newest,
    val opened: Set<String>? = null,
    val justSavedId: String? = null,
    val selectedId: String? = null,
    val linkedId: String? = null,
    val recordsAvailable: Boolean = true,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val instrument: Instrument? = null,
    val songInstruments: Map<String, Instrument> = emptyMap(),
    val summaries: List<RecordSongSummary> = emptyList(),
    val pages: Map<String, RecordPages> = emptyMap(),
    val totalRecordCount: Int = 0,
    val week: RecordWeek? = null,
) {
    val groups: List<RecordSongSummary>
        get() = instrument?.let { selected -> summaries.filter { songInstruments[it.songId] == selected } } ?: summaries

    val showLoadStatus: Boolean get() = recordsAvailable && summaries.isEmpty() && (loading || loadFailed)

    val openSongs: Set<String>
        get() = opened ?: groups.firstOrNull()?.let { setOf(it.songId) }.orEmpty()

    val selectedRecordId: String?
        get() = selectedId ?: linkedId ?: groups.asSequence()
            .mapNotNull { pages[it.songId]?.records?.maxByOrNull { record -> record.recordedAtMillis }?.id }
            .firstOrNull()

    fun isOpen(songId: String): Boolean = songId in openSongs

    val thisWeek: Pair<Int, Int>?
        get() = week?.let { summary -> summary.bestScore?.let { summary.recordCount to it } }
}
