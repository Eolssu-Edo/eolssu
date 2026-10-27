package com.ssafy.eolssu.feature.history

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
 */
data class RecordListUiState(
    val records: List<PracticeRecord> = emptyList(),
    val today: KoreanDateTime = KoreanDateTime(1970, 1, 1, 0, 0),
    val sort: RecordSort = RecordSort.Newest,
    val opened: Set<String>? = null,
    val justSavedId: String? = null,
) {
    /** 곡마다 한 묶음. 곡 순서는 최근에 연습한 곡이 위, 묶음 안은 [sort]를 따른다. */
    val groups: List<RecordGroup>
        get() = records.groupBy { it.songId }.map { (songId, list) ->
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
