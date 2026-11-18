package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertNull(state.delta, "서버가 비교 가능 여부와 구간 점수 차이를 주기 전에는 전체 점수를 빼지 않는다")
        // 이전 기록의 틀린 정간을 받기 전에는 표시하지 않는다.
        assertTrue(state.marks.isEmpty())
        assertNull(state.check)

        val check = SessionCheck(true, null, 12, 6, -6, 8, null, null,
            scoreComparable = true, totalScoreDelta = 9, bpmChanged = true)
        val loaded = state.copy(previousDetail = PreviousRecord(latest.copy(errors = errors(3, 11, 20, 22, 24, 25, 29, 30, 31, 33, 40, 47)), CheckLoad.Loaded(check)))
        assertEquals(CompareCounts(previous = 12, current = 6, still = 4), loaded.counts)
        assertEquals(2, loaded.marks.values.count { it == CompareMark.New })
        assertEquals(CheckLoad.Loaded(check), loaded.check)
        assertEquals(9, loaded.delta, "화면 기록의 84-68이 아니라 서버의 비교 구간 점수 차이를 쓴다")
        val incompatible = loaded.copy(previousDetail = loaded.previousDetail?.copy(check = CheckLoad.Loaded(check.copy(comparable = false, scoreComparable = false, totalScoreDelta = null))))
        assertTrue(incompatible.marks.isEmpty())
        assertNull(incompatible.delta)

        // 다른 이전 기록을 고르면 받은 상세는 쓰지 않는다.
        val switched = loaded.copy(previousId = "prev-2")
        assertTrue(switched.marks.isEmpty())
        assertNull(switched.check)
    }

    @Test
    fun monthCalendarStartsOnSundayAndMarksCurrentAndLaterDays() {
        val pick = assertNotNull(CompareUiState(loading = false, comparison = comparison).pick)
        assertEquals("prev-1", pick.selectedId)
        // 처음에는 비교 중인 기록의 날(10월 13일)을 보인다.
        assertEquals(KoreanDateTime(2026, 10, 13, 0, 0).epochDay, pick.initialDay)
        val october = pick.month(CompareMonth(2026, 10))
        // 2026-10-01은 목요일이라 월요일부터 세 칸(월·화·수)을 비운다.
        assertEquals(3, october.takeWhile { it == null }.size)
        val days = october.filterNotNull()
        assertEquals(31, days.size)
        assertEquals(listOf("prev-1", "prev-2"), days.first { it.date.day == 13 }.records.map { it.id })
        assertTrue(days.first { it.date.day == 20 }.current)
        assertTrue(days.first { it.date.day == 21 }.after)
        assertFalse(days.first { it.date.day == 19 }.after)
        assertTrue(pick.recordsOn(KoreanDateTime(2026, 10, 14, 0, 0).epochDay).isEmpty())
    }

    @Test
    fun monthRangeRunsFromFirstEarlierRecordToCurrentRecord() {
        val old = record("old", KoreanDateTime(2026, 7, 30, 21, 0), 52, emptyList())
        val pick = ComparePick(current, listOf(latest, earlier, old), selectedId = null)
        assertEquals(CompareMonth(2026, 7), pick.firstMonth)
        assertEquals(CompareMonth(2026, 10), pick.lastMonth)
        // 비교 중인 기록이 없으면 바로 전 기록의 날을 보인다.
        assertEquals(latest.recordedAt.epochDay, pick.initialDay)
        assertEquals(CompareMonth(2027, 1), CompareMonth(2026, 12).plus(1))
        assertEquals(CompareMonth(2025, 12), CompareMonth(2026, 1).plus(-1))
        assertEquals(28, CompareMonth(2026, 2).length)
        // 이전 기록이 없으면 이번 기록의 달·날만.
        val alone = ComparePick(current, emptyList(), selectedId = null)
        assertEquals(alone.lastMonth, alone.firstMonth)
        assertEquals(current.recordedAt.epochDay, alone.initialDay)
    }

    @Test
    fun scoreChipUsesServerDeltaOrElseBothRecordsTotals() {
        fun loaded(delta: Int?) = CheckLoad.Loaded(SessionCheck(true, null, 3, 2, -1, 1, null, null, scoreComparable = delta != null, totalScoreDelta = delta))
        assertEquals(ScoreChange.Pending, scoreChange(null, 70, 82))
        assertEquals(ScoreChange.Up(12), scoreChange(loaded(12), 70, 70), "서버 차이가 있으면 그 값을 쓴다")
        assertEquals(ScoreChange.Down(4), scoreChange(loaded(-4), 70, 70))
        assertEquals(ScoreChange.Same, scoreChange(loaded(0), 70, 71))
        // 서버가 총점 차이를 주지 않아도(채점 방식·판본이 다름, 400, 받지 못함) 1점이라도 다르면 두 기록 총점 차이를 보인다.
        assertEquals(ScoreChange.Up(1), scoreChange(loaded(null), 70, 71))
        assertEquals(ScoreChange.Down(4), scoreChange(CheckLoad.Rejected, 72, 68))
        assertEquals(ScoreChange.Same, scoreChange(CheckLoad.Failed, 70, 70))
        assertEquals(ScoreChange.Unavailable, scoreChange(loaded(null), null, 70))
    }

    @Test
    fun metricChangesUseBothRecordsScores() {
        fun metrics(pitch: Int, rhythm: Int, length: Int) = listOf(
            MetricScore(EvaluationMetric.Pitch, pitch), MetricScore(EvaluationMetric.Rhythm, rhythm), MetricScore(EvaluationMetric.Length, length),
        )
        val now = current.copy(metrics = metrics(85, 78, 83))
        val state = CompareUiState(loading = false, comparison = comparison.copy(current = now))
        // 목록의 이전 기록에는 항목 점수가 없어 상세를 받기 전에는 줄이 없다.
        assertTrue(state.metricChanges.isEmpty())
        val check = SessionCheck(true, null, 3, 2, -1, 1, null, null, scoreComparable = false, totalScoreDelta = null)
        val loaded = state.copy(previousDetail = PreviousRecord(latest.copy(metrics = metrics(74, 62, 85)), CheckLoad.Loaded(check)))
        assertEquals(
            listOf(
                MetricChange(EvaluationMetric.Pitch, 74, 85),
                MetricChange(EvaluationMetric.Rhythm, 62, 78),
                MetricChange(EvaluationMetric.Length, 85, 83),
            ),
            loaded.metricChanges,
        )
        assertEquals(listOf(11, 16, -2), loaded.metricChanges.map { it.delta }, "총점을 서버가 견주지 않아도 항목 차이는 보인다")
        // 총점도 두 기록의 점수 차이(84 − 68)로 보인다.
        assertEquals(ScoreChange.Up(16), loaded.scoreChange)
    }

    @Test
    fun candidatesHaveNoPeriodLimitButExcludeLaterRecords() {
        fun record(id: String, month: Int, day: Int, section: String? = null) = PracticeRecord(
            id, "arirang", "아리랑", section, null, KoreanDateTime(2026, month, day, 9, 0), 48, 60, 70, emptyList(), "", emptyList(),
        )
        val now = record("now", 11, 9)
        val records = listOf(now, record("later", 11, 10), record("week", 11, 2), record("months", 7, 1), record("section", 10, 1, section = "3–4각"))
        assertEquals(listOf("week", "months"), now.compareCandidates(records).map { it.id })
    }

    @Test
    fun previousInFindsTheLatestEarlierWholePerformanceOfTheSameSong() {
        fun record(id: String, day: Int, hour: Int, song: String = "arirang", section: String? = null) = PracticeRecord(
            id, song, "아리랑", section, null, KoreanDateTime(2026, 10, day, hour, 0), 48, 60, 70, emptyList(), "", emptyList(),
        )
        val now = record("now", 20, 15)
        val records = listOf(now, record("later", 21, 9), record("section", 19, 9, section = "3–4각"), record("other", 19, 10, song = "doraji"), record("old", 13, 19), record("older", 10, 21))
        assertEquals("old", now.previousIn(records)?.id)
        val earlierInSameMinute = now.copy(id = "earlier", recordedAtMillis = now.recordedAtMillis - 1)
        val laterInSameMinute = now.copy(id = "later-in-minute", recordedAtMillis = now.recordedAtMillis + 1)
        assertEquals("earlier", now.previousIn(records + laterInSameMinute + earlierInSameMinute)?.id)
        assertNull(record("first", 1, 1).previousIn(records))
    }
}
