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
    val newestRecord: PracticeRecord? = null,
    val newestLoadFailed: Boolean = false,
) {
    val groups: List<RecordSongSummary>
        get() = instrument?.let { selected -> summaries.filter { songInstruments[it.songId] == selected } } ?: summaries

    val showLoadStatus: Boolean get() = recordsAvailable && (loadFailed || summaries.isEmpty() && loading)

    val openSongs: Set<String>
        get() = opened ?: groups.firstOrNull()?.let { setOf(it.songId) }.orEmpty()

    private val firstSongId: String? get() = groups.firstOrNull()?.songId

    val newestLookupSongId: String?
        get() {
            if (selectedId != null || linkedId != null || sort != RecordSort.Oldest) return null
            val songId = firstSongId ?: return null
            val page = pages[songId] ?: return null
            return songId.takeIf { page.nextPage != null && page.records.isNotEmpty() && newestRecord?.songId != songId }
        }

    val selectedRecordId: String?
        get() {
            (selectedId ?: linkedId)?.let { return it }
            val songId = firstSongId ?: return null
            if (newestLookupSongId != null) return null
            return newestRecord?.takeIf { it.songId == songId }?.id
                ?: pages[songId]?.records?.let { records ->
                    if (sort == RecordSort.Oldest) records.lastOrNull()?.id else records.firstOrNull()?.id
                }
        }

    fun recordForDetail(recordId: String): PracticeRecord? =
        newestRecord?.takeIf { it.id == recordId }
            ?: pages.values.firstNotNullOfOrNull { page -> page.records.firstOrNull { it.id == recordId } }

    fun isOpen(songId: String): Boolean = songId in openSongs

    val thisWeek: Pair<Int, Int>?
        get() = week?.let { summary -> summary.bestScore?.let { summary.recordCount to it } }
}
