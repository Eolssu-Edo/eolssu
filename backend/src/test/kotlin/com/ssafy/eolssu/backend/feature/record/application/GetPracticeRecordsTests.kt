package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetPracticeRecordsTests {
    private val record = RecordSession(
        sessionId = 501, songId = 101, songTitle = "아리랑", noteCount = 6, scoreVersion = 2, startNoteIdx = 2, endNoteIdx = 4,
        bpm = 96, bpmUnit = "DOTTED_QUARTER", status = "DONE", startedAt = LocalDateTime.parse("2026-11-02T03:58:00"),
        recordingDurationMs = 48000, totalScore = 84, savedAt = LocalDateTime.parse("2026-11-02T04:00:00"), memo = null,
    )
    private val summaries = listOf(
        SongRecordSummary(101, "아리랑", 45, LocalDateTime.parse("2026-11-02T04:00:00"), 84),
        SongRecordSummary(102, "도라지타령", 1, LocalDateTime.parse("2026-10-30T04:00:00"), 71),
    )

    /** 정렬·페이지는 DB가 하므로 여기서는 받은 조회 조건만 남기고 정해 둔 한 쪽을 돌려준다. */
    private class Records(private val summaries: List<SongRecordSummary>, private val page: List<RecordSession>) : PracticeRecords {
        val queries = mutableListOf<List<Any>>()

        override fun lockStatus(sessionId: Long, userId: Long): String? = error("not used")

        override fun findSession(sessionId: Long, userId: Long): RecordSession? = error("not used")

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) = error("not used")

        override fun songSummaries(userId: Long) = summaries

        override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> {
            queries += listOf(userId, songId, sort, offset, limit)
            return page
        }

        override fun findRecording(sessionId: Long, userId: Long): RecordingFile? = error("not used")
    }

    private var columnReads = 0
    private val columns = ScoreColumns { _, version ->
        columnReads++
        if (version == 2) listOf(0, 0, 1, 1, 2, 2) else null
    }

    @Test
    fun `opens the most recently saved song by default and pages its records`() {
        val records = Records(summaries, listOf(record, record.copy(sessionId = 502, scoreVersion = 1), record.copy(sessionId = 503)))
        val list = GetPracticeRecords(records, columns).execute(1, RecordListQuery(songId = null, sort = RecordSort.NEWEST, page = 1, size = 20))
        assertEquals(46, list.totalRecordCount)
        assertEquals(summaries, list.songs)
        assertEquals(101L, list.songId)
        assertEquals(listOf(listOf<Any>(1L, 101L, RecordSort.NEWEST, 20L, 20)), records.queries)
        assertEquals(45 to 3, list.totalElements to list.totalPages)
        assertEquals(listOf(ColumnRange(1, 2), null, ColumnRange(1, 2)), list.items.map { it.columns })
        // 같은 악보 버전의 각은 한 번만 읽는다.
        assertEquals(2, columnReads)
    }

    @Test
    fun `answers an empty page without querying when the song has no records or the page is past the end`() {
        val records = Records(summaries, listOf(record))
        val unknown = GetPracticeRecords(records, columns).execute(1, RecordListQuery(songId = 104, sort = RecordSort.OLDEST, page = 0, size = 20))
        assertEquals(104L, unknown.songId)
        assertTrue(unknown.items.isEmpty())
        assertEquals(0 to 0, unknown.totalElements to unknown.totalPages)

        val past = GetPracticeRecords(records, columns).execute(1, RecordListQuery(songId = 102, sort = RecordSort.NEWEST, page = Int.MAX_VALUE, size = 100))
        assertTrue(past.items.isEmpty())
        assertEquals(1 to 1, past.totalElements to past.totalPages)
        assertTrue(records.queries.isEmpty())
    }

    @Test
    fun `member without records has no songs and no selected song`() {
        val list = GetPracticeRecords(Records(emptyList(), emptyList()), columns).execute(1, RecordListQuery(null, RecordSort.NEWEST, 0, 20))
        assertEquals(0, list.totalRecordCount)
        assertTrue(list.songs.isEmpty())
        assertNull(list.songId)
        assertEquals(0 to 0, list.totalElements to list.totalPages)
    }

    @Test
    fun `total pages round up`() {
        for ((total, size, pages) in listOf(Triple(40, 20, 2), Triple(41, 20, 3), Triple(1, 100, 1), Triple(100, 1, 100))) {
            val summary = listOf(SongRecordSummary(101, "아리랑", total, LocalDateTime.parse("2026-11-02T04:00:00"), 84))
            val list = GetPracticeRecords(Records(summary, emptyList()), columns).execute(1, RecordListQuery(null, RecordSort.NEWEST, 0, size))
            assertEquals(pages, list.totalPages, "$total/$size")
        }
    }
}
