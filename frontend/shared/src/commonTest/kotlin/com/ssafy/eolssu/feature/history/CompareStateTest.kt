package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.RecordComparison
import com.ssafy.eolssu.data.practice.SessionCheck
import com.ssafy.eolssu.data.song.SampleSongs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareStateTest {
    private fun errors(vararg cells: Int) = cells.map { ScoreError(it / 9, it % 9, EvaluationMetric.Pitch) }

    @Test
    fun marksSplitPreviousCurrentAndBoth() {
        val marks = compareMarks(previous = errors(3, 20, 22), current = errors(20, 37))
        assertEquals(CompareMark.Fixed, marks[ScorePosition(0, 3)])
        assertEquals(CompareMark.Still, marks[ScorePosition(2, 2)])
        assertEquals(CompareMark.New, marks[ScorePosition(4, 1)])
        assertEquals(4, marks.size)

        val counts = CompareCounts.of(marks)
        assertEquals(3, counts.previous)
        assertEquals(2, counts.current)
        assertEquals(1, counts.still)
    }

    private fun record(id: String, at: KoreanDateTime, score: Int, cells: List<Int>) = PracticeRecord(
        id, "arirang", "아리랑", null, null, at, 48, 60, score, emptyList(), "", errors(*cells.toIntArray()),
    )

    private val today = KoreanDateTime(2026, 10, 20, 15, 20)
    private val current = record("now", today, 84, listOf(20, 22, 29, 30, 37, 44))
    private val latest = record("prev-1", KoreanDateTime(2026, 10, 13, 19, 5), 68, emptyList())
    private val earlier = record("prev-2", KoreanDateTime(2026, 10, 13, 8, 20), 63, emptyList())
    private val comparison = RecordComparison(SampleSongs.first(), current, listOf(latest, earlier), today)

    @Test
    fun comparisonStartsWithLatestPreviousAndWaitsForItsErrors() {
        val state = CompareUiState(loading = false, comparison = comparison)
        assertEquals("prev-1", state.previous?.id)
        assertEquals(16, state.delta)
        // 이전 기록의 틀린 정간을 받기 전에는 표시하지 않는다.
        assertTrue(state.marks.isEmpty())
        assertNull(state.check)

        val check = SessionCheck(false, "속도가 달라요.", null, 6, null, 0, null, null)
        val loaded = state.copy(previousDetail = PreviousRecord(latest.copy(errors = errors(3, 11, 20, 22, 24, 25, 29, 30, 31, 33, 40, 47)), CheckLoad.Loaded(check)))
        assertEquals(CompareCounts(previous = 12, current = 6, still = 4), loaded.counts)
        assertEquals(2, loaded.marks.values.count { it == CompareMark.New })
        assertEquals(CheckLoad.Loaded(check), loaded.check)
        assertEquals(ScorePosition(2, 2), loaded.listenFrom)

        // 다른 이전 기록을 고르면 받은 상세는 쓰지 않는다.
        val switched = loaded.copy(previousId = "prev-2")
        assertTrue(switched.marks.isEmpty())
        assertNull(switched.check)
    }

    @Test
    fun calendarCoversTwoWeeksEndingOnCurrentRecord() {
        val state = CompareUiState(loading = false, comparison = comparison)
        val calendar = state.calendar
        assertEquals(14, calendar.size)
        assertEquals(KoreanDateTime(2026, 10, 7, 0, 0), calendar.first().date)
        assertEquals(2, calendar.dayOfWeekIndex())
        assertTrue(calendar.last().today)
        assertEquals(2, calendar.first { it.date.day == 13 }.records.size)
        assertEquals(state.previous?.recordedAt?.epochDay, state.shownDay)
    }

    private fun List<CompareDay>.dayOfWeekIndex() = first().date.dayOfWeek

    @Test
    fun previousInFindsTheLatestEarlierWholePerformanceOfTheSameSong() {
        fun record(id: String, day: Int, hour: Int, song: String = "arirang", section: String? = null) = PracticeRecord(
            id, song, "아리랑", section, null, KoreanDateTime(2026, 10, day, hour, 0), 48, 60, 70, emptyList(), "", emptyList(),
        )
        val now = record("now", 20, 15)
        val records = listOf(now, record("later", 21, 9), record("section", 19, 9, section = "3–4각"), record("other", 19, 10, song = "doraji"), record("old", 13, 19), record("older", 10, 21))
        assertEquals("old", now.previousIn(records)?.id)
        assertNull(record("first", 1, 1).previousIn(records))
    }
}
