package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.model.sortedByTitle
import com.ssafy.eolssu.data.practice.RecordSongSummary
import com.ssafy.eolssu.data.practice.RecordWeek

data class RecordPages(
    val records: List<PracticeRecord> = emptyList(),
    val nextPage: Int? = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    /** 첫 페이지를 받았다. 받기 전 자리는 기록이 없고 다음 페이지가 0이다. */
    val loaded: Boolean get() = records.isNotEmpty() || nextPage == null
}

/**
 * 화면의 곡 묶음. 서버는 악기마다 다른 곡 ID로 묶어 주므로, 제목이 같은 곡을 악기와 관계없이 하나로 모은다.
 * [songs]는 최근에 연습한 곡이 앞이다.
 */
data class RecordSongGroup(val key: String, val title: String, val songs: List<RecordSongSummary>) {
    val songIds: List<String> get() = songs.map { it.songId }
    val recordCount: Int get() = songs.sumOf { it.recordCount }
    val bestScore: Int get() = songs.maxOf { it.bestScore }
    val newestSongId: String get() = songs.first().songId
}

/** 곡 묶음 안에서 같은 날 연주한 기록. [records]는 정렬 순서를 따른다. */
data class RecordDay(val date: KoreanDateTime, val records: List<PracticeRecord>) {
    val epochDay: Long get() = date.epochDay
}

data class RecordListUiState(
    val today: KoreanDateTime = KoreanDateTime(1970, 1, 1, 0, 0),
    /** 곡 묶음의 이름 순서. 묶음 안의 날짜·연주는 늘 최신순이다. */
    val nameOrder: SongSortOrder = SongSortOrder.Ascending,
    /** 곡 제목 검색어. 앞뒤 공백은 무시한다. */
    val query: String = "",
    /** 펼친 곡 묶음의 [RecordSongGroup.key]. 처음에는 모두 접혀 있다. */
    val opened: Set<String> = emptySet(),
    /** 곡 묶음별로 직접 접은 날짜([RecordDay.epochDay]). 나머지 날짜는 모두 펼쳐져 있다. */
    val closedDays: Map<String, Set<Long>> = emptyMap(),
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
    /** 악기 필터를 곡 ID마다 적용한 뒤 제목이 같은 곡을 합치고, 검색어가 든 곡만 [nameOrder]로 늘어놓는다. */
    val groups: List<RecordSongGroup>
        get() {
            val visible = instrument?.let { selected -> summaries.filter { songInstruments[it.songId] == selected } } ?: summaries
            val q = query.trim()
            return visible.groupBy(::groupKey)
                .map { (key, songs) -> RecordSongGroup(key, songs.first().title, songs.sortedByDescending { it.lastStartedAt.minuteKey }) }
                .filter { q.isEmpty() || it.title.contains(q) }
                .sortedByTitle(nameOrder) { it.title }
        }

    val showLoadStatus: Boolean get() = recordsAvailable && (loadFailed || summaries.isEmpty() && loading)

    val openGroups: Set<String> get() = opened

    /** 두 칸 배치에서 오른쪽에 보일 기록: 고른 기록, 없으면 방금 저장했거나 상세에서 연 기록. 처음에는 고른 기록이 없다. */
    val selectedRecordId: String? get() = selectedId ?: linkedId

    fun recordForDetail(recordId: String): PracticeRecord? =
        pages.values.firstNotNullOfOrNull { page -> page.records.firstOrNull { it.id == recordId } }

    fun isOpen(groupKey: String): Boolean = groupKey in openGroups

    fun groupOf(songId: String): RecordSongGroup? = groups.firstOrNull { songId in it.songIds }

    /** [songId]가 든 곡 묶음이 펼쳐져 있다. */
    fun isSongOpen(songId: String): Boolean = groupOf(songId)?.let { isOpen(it.key) } == true

    /**
     * 곡 묶음의 기록을 최신순으로 합친다. 곡마다 페이지를 따로 받으므로, 다음 페이지가 남은 곡의 마지막 기록보다
     * 오래된 기록은 그 사이 기록이 빠질 수 있어 더 받은 뒤에 보인다. 첫 페이지를 받지 못한 곡이 있으면 비어 있다.
     */
    fun records(group: RecordSongGroup): List<PracticeRecord> {
        val loaded = group.songIds.mapNotNull { id -> pages[id]?.takeIf { it.loaded } }
        if (loaded.size < group.songIds.size) return emptyList()
        val ordered = loaded.flatMap { it.records }.distinctBy { it.id }
            .sortedWith(compareByDescending<PracticeRecord> { it.recordedAtMillis }.thenByDescending { it.id })
        val edge = loaded.filter { it.nextPage != null }.maxOfOrNull { it.records.last().recordedAtMillis } ?: return ordered
        return ordered.filter { it.recordedAtMillis >= edge }
    }

    fun days(group: RecordSongGroup): List<RecordDay> =
        records(group).groupBy { it.recordedAt.epochDay }.map { (_, records) -> RecordDay(records.first().recordedAt, records) }

    /** 직접 펼친 날짜가 없으면 맨 위 날짜와 방금 저장·상세에서 연 기록의 날짜를 펼친다. */
    /** 곡 묶음을 펼치면 날짜도 모두 펼쳐져 있고, 직접 접은 날짜만 닫힌다. 더 받아 새로 생긴 날짜도 펼쳐진다. */
    fun isDayOpen(group: RecordSongGroup, epochDay: Long): Boolean = epochDay !in closedDays[group.key].orEmpty()

    val thisWeek: Pair<Int, Int>?
        get() = week?.let { summary -> summary.bestScore?.let { summary.recordCount to it } }
}

/** 제목 앞뒤 공백만 정리한다. 제목이 비어 있으면 곡 ID로 따로 둔다. */
private fun groupKey(song: RecordSongSummary): String = song.title.trim().ifEmpty { "#${song.songId}" }

private val KoreanDateTime.minuteKey: Long get() = (epochDay * 24 + hour) * 60 + minute
