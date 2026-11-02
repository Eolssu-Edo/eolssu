package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import com.ssafy.eolssu.backend.feature.record.domain.rangeOf
import org.springframework.transaction.annotation.Transactional

/**
 * 저장한 연습 기록을 곡별로 묶어 보여 준다(REC-02). 곡 묶음은 모두, 펼친 곡의 기록은 페이지로 준다.
 * 곡을 고르지 않으면 가장 최근에 저장한 곡을 펼친다. 조회만 하며 NEW 표시(`viewed_at`)는 바꾸지 않는다.
 */
open class GetPracticeRecords(
    private val records: PracticeRecords,
    private val columns: ScoreColumns,
) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, query: RecordListQuery): RecordList {
        val songs = records.songSummaries(userId)
        val songId = query.songId ?: songs.firstOrNull()?.songId
        val total = songs.firstOrNull { it.songId == songId }?.recordCount ?: 0
        // 큰 page에서도 넘치지 않게 Long으로 센다. 끝을 지난 페이지는 조회하지 않는다.
        val offset = query.page.toLong() * query.size
        val page = if (songId == null || offset >= total) emptyList() else records.songRecords(userId, songId, query.sort, offset, query.size)
        // 악보 버전이 달라 각이 없는 결과(null)도 기억한다. getOrPut은 null을 없는 값으로 보고 다시 읽는다.
        val columnsByScore = mutableMapOf<Pair<Long, Int>, List<Int>?>()
        val cached = ScoreColumns { id, version ->
            val key = id to version
            if (key in columnsByScore) columnsByScore[key] else columns.columns(id, version).also { columnsByScore[key] = it }
        }
        return RecordList(
            totalRecordCount = songs.sumOf { it.recordCount },
            songs = songs,
            songId = songId,
            items = page.map { RecordListItem(it, cached.rangeOf(it)) },
            totalElements = total,
            totalPages = if (total == 0) 0 else (total + query.size - 1) / query.size,
        )
    }
}

data class RecordListQuery(val songId: Long?, val sort: RecordSort, val page: Int, val size: Int)

data class RecordListItem(val record: RecordSession, val columns: ColumnRange?)

data class RecordList(
    val totalRecordCount: Int,
    val songs: List<SongRecordSummary>,
    /** 펼친 곡. 기록이 하나도 없으면 null. */
    val songId: Long?,
    val items: List<RecordListItem>,
    /** 펼친 곡의 기록 수. */
    val totalElements: Int,
    val totalPages: Int,
)
