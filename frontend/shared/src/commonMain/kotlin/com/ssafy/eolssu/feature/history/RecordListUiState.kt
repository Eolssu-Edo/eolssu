package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord

/** 기록 목록 정렬. */
enum class RecordSort { Newest, Oldest }

/** 곡 하나의 녹음 묶음(아코디언). */
data class RecordGroup(
    val songId: String,
    val songTitle: String,
    val records: List<PracticeRecord>,
    val latest: KoreanDateTime,
    val best: Int,
)

/**
 * RECORD-01 상태.
 *
 * @property records 최신순 기록.
 * @property opened 펼친 곡. null이면 처음 상태로, 가장 최근에 연습한 곡만 펼친다.
 * @property justSavedId 이번에 결과 화면에서 저장해 '방금 저장'을 붙일 기록.
 * @property selectedId 두 칸 배치(태블릿 가로)에서 사용자가 고른 기록. 고르기 전에는 null.
 * @property loading 서버에서 목록을 받는 중.
 * @property loadFailed 목록을 받지 못함. 이미 받은 기록이 있으면 그대로 보인다.
 * @property instrument 고른 악기. null이면 모든 악기의 기록을 보인다.
 * @property songInstruments 곡 ID별 악기. 기록 목록 API에 악기가 없어 곡 정보에서 채운다. 모르는 곡은 '전체'에서만 보인다.
 */
data class RecordListUiState(
    val records: List<PracticeRecord> = emptyList(),
    val today: KoreanDateTime = KoreanDateTime(1970, 1, 1, 0, 0),
    val sort: RecordSort = RecordSort.Newest,
    val opened: Set<String>? = null,
    val justSavedId: String? = null,
    val selectedId: String? = null,
    val recordsAvailable: Boolean = true,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val instrument: Instrument? = null,
    val songInstruments: Map<String, Instrument> = emptyMap(),
) {
    /** 고른 악기의 기록(최신순). 악기를 고르지 않았으면 모든 기록. */
    val visibleRecords: List<PracticeRecord>
        get() = instrument?.let { selected -> records.filter { songInstruments[it.songId] == selected } } ?: records

    /** 받은 기록이 없는데 받는 중이거나 받지 못해, 빈 목록 대신 그 상태를 보일 때. */
    val showLoadStatus: Boolean get() = recordsAvailable && records.isEmpty() && (loading || loadFailed)

    /**
     * 두 칸 배치에서 오른쪽에 상세를 보여 줄 기록. 고른 기록 → 방금 저장한 기록 → 가장 최근 기록 순으로,
     * 지금 목록에 있는 것을 쓴다. 기록이 없으면 null.
     */
    val selectedRecordId: String?
        get() = listOfNotNull(selectedId, justSavedId).firstOrNull { id -> visibleRecords.any { it.id == id } } ?: visibleRecords.firstOrNull()?.id

    fun select(recordId: String): RecordListUiState = copy(selectedId = recordId)

    /** 기록 상세 경로로 바로 들어왔을 때: 그 기록을 고르고 그 곡 묶음을 펼친다. 기록을 아직 받지 않았으면 고르기만 한다. */
    fun show(recordId: String): RecordListUiState {
        val songId = records.firstOrNull { it.id == recordId }?.songId ?: return select(recordId)
        val selected = select(recordId)
        return if (selected.isOpen(songId)) selected else selected.toggle(songId)
    }

    /** 고른 악기의 곡마다 한 묶음. 곡 순서는 최근에 연습한 곡이 위, 묶음 안은 [sort]를 따른다. */
    val groups: List<RecordGroup>
        get() = visibleRecords.groupBy { it.songId }.map { (songId, list) ->
            RecordGroup(
                songId = songId,
                songTitle = list.first().songTitle,
                records = if (sort == RecordSort.Newest) list else list.reversed(),
                latest = list.first().recordedAt,
                best = list.maxOf { it.score },
            )
        }

    fun isOpen(songId: String): Boolean = opened?.contains(songId) ?: (groups.firstOrNull()?.songId == songId)

    /** 이번 주(월~일) 기록 수와 최고 점수. 기록이 없으면 null. */
    val thisWeek: Pair<Int, Int>?
        get() = records.filter { it.recordedAt.isSameWeek(today) }.takeIf { it.isNotEmpty() }?.let { it.size to it.maxOf { r -> r.score } }

    fun toggle(songId: String): RecordListUiState {
        val current = opened ?: groups.firstOrNull()?.let { setOf(it.songId) } ?: emptySet()
        return copy(opened = if (songId in current) current - songId else current + songId)
    }
}
