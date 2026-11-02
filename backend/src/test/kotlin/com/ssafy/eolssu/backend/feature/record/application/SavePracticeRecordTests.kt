package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SavePracticeRecordTests {
    private val clock = Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneOffset.UTC)
    private val done = RecordSession(
        sessionId = 501, songId = 101, songTitle = "아리랑", noteCount = 6, scoreVersion = 2, startNoteIdx = 2, endNoteIdx = 4,
        bpm = 96, bpmUnit = "DOTTED_QUARTER", status = "DONE", startedAt = LocalDateTime.parse("2026-11-02T03:58:00"),
        recordingDurationMs = 48000, totalScore = 84, savedAt = null, memo = null,
    )

    /** 저장은 DB 조건부 갱신이 맡으므로 여기서는 호출 인자만 남기고 저장 결과는 정해 둔다. */
    private class Records(var session: RecordSession?, val afterSave: RecordSession?) : PracticeRecords {
        val saves = mutableListOf<Pair<LocalDateTime, MemoChange>>()

        override fun lockStatus(sessionId: Long, userId: Long) = session?.takeIf { it.sessionId == sessionId && userId == 1L }?.status

        override fun findSession(sessionId: Long, userId: Long) = if (saves.isEmpty()) session else afterSave

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) {
            saves += savedAt to memo
        }

        override fun songSummaries(userId: Long): List<SongRecordSummary> = error("not used")

        override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> = error("not used")

        override fun findRecording(sessionId: Long, userId: Long): RecordingFile? = error("not used")
    }

    // 음표 0~5가 각 0,0,1,1,2,2에 있다.
    private val columns = ScoreColumns { songId, version -> if (songId == 101L && version == 2) listOf(0, 0, 1, 1, 2, 2) else null }

    @Test
    fun `saves a finished session and returns it with the column range`() {
        val saved = done.copy(savedAt = LocalDateTime.parse("2026-11-02T04:00:00"), memo = "박자가 밀림")
        val records = Records(done, saved)
        val outcome = assertIs<SaveRecordOutcome.Saved>(SavePracticeRecord(records, columns, clock).execute(1, 501, MemoChange.Replace("박자가 밀림")))
        assertEquals(saved, outcome.record)
        assertEquals(ColumnRange(1, 2), outcome.columns)
        assertEquals(listOf<Pair<LocalDateTime, MemoChange>>(LocalDateTime.parse("2026-11-02T04:00:00") to MemoChange.Replace("박자가 밀림")), records.saves)
    }

    @Test
    fun `refuses sessions that are not finished and hides missing ones without saving`() {
        for (status in listOf("RECORDING", "PENDING", "FAILED")) {
            val records = Records(done.copy(status = status), null)
            assertEquals(SaveRecordOutcome.NotDone, SavePracticeRecord(records, columns, clock).execute(1, 501, MemoChange.Keep))
            assertTrue(records.saves.isEmpty())
        }
        val records = Records(done, null)
        assertEquals(SaveRecordOutcome.NotFound, SavePracticeRecord(records, columns, clock).execute(2, 501, MemoChange.Keep))
        assertEquals(SaveRecordOutcome.NotFound, SavePracticeRecord(records, columns, clock).execute(1, 999, MemoChange.Keep))
        assertTrue(records.saves.isEmpty())
    }

    @Test
    fun `leaves columns empty when the score version changed or the range is outside the score`() {
        val changed = done.copy(scoreVersion = 1, savedAt = LocalDateTime.parse("2026-11-02T04:00:00"))
        assertNull(assertIs<SaveRecordOutcome.Saved>(SavePracticeRecord(Records(done, changed), columns, clock).execute(1, 501, MemoChange.Keep)).columns)
        val outside = done.copy(endNoteIdx = 6, savedAt = LocalDateTime.parse("2026-11-02T04:00:00"))
        assertNull(assertIs<SaveRecordOutcome.Saved>(SavePracticeRecord(Records(done, outside), columns, clock).execute(1, 501, MemoChange.Keep)).columns)
    }
}
