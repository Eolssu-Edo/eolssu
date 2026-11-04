package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.data.practice.SampleWeakSectionRepository
import kotlinx.coroutines.test.runTest
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

    @Test
    fun sampleComparisonMatchesWireframeCounts() = runTest {
        val comparison = SampleWeakSectionRepository().comparison("record")
        val state = CompareUiState(loading = false, comparison = comparison)
        // 가장 최근 이전 기록(10월 13일 오후 7:05, 68점)과 비교한다.
        assertEquals(68, state.previous?.score)
        assertEquals(16, state.delta)
        assertEquals(CompareCounts(previous = 12, current = 6, still = 4), state.counts)
        assertEquals(2, state.marks.values.count { it == CompareMark.New })
        assertEquals(ScorePosition(2, 2), state.listenFrom)

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
