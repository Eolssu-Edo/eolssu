package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryStateTest {

    private val records = SamplePracticeRecordRepository().records.value
    private val oct18 = KoreanDateTime(2026, 10, 18, 22, 0)

    @Test
    fun weekdaysAndWeeks() {
        assertEquals(0, KoreanDateTime(2026, 10, 12, 0, 0).dayOfWeek) // 월요일
        assertEquals(6, KoreanDateTime(2026, 10, 18, 0, 0).dayOfWeek) // 일요일
        assertTrue(KoreanDateTime(2026, 10, 12, 0, 0).isSameWeek(oct18))
        assertFalse(KoreanDateTime(2026, 10, 19, 0, 0).isSameWeek(oct18))
    }

    @Test
    fun localFixtureReturnsGroupsAndAWeekIndependentlyOfThePage() = runTest {
        val repo = SamplePracticeRecordRepository()
        val first = checkNotNull(repo.loadListPage(weekOf = oct18))
        val state = RecordListUiState(summaries = first.songs, week = first.week)
        // 곡 묶음은 가나다순이고, 처음에는 가장 최근에 연습한 '아리랑'이 펼쳐진다.
        assertEquals(listOf("도라지타령", "아리랑"), state.groups.map { it.title })
        assertEquals(3, state.groups.last().recordCount)
        assertEquals(79, state.groups.last().bestScore)
        assertTrue(state.isOpen("아리랑"))
        assertFalse(state.isOpen("도라지타령"))
        assertEquals(4 to 79, state.thisWeek)
        val oldest = checkNotNull(repo.loadListPage("arirang", oldest = true))
        assertEquals(KoreanDateTime(2026, 10, 12, 18, 40), oldest.records.first().recordedAt)
    }

    @Test
    fun detailFindsEventBySeconds() {
        val record = records.first { it.id == "sample-2" }
        val state = RecordDetailUiState(record = record, song = SampleSongs.first { it.id == "arirang" })
        // 60BPM 세마치: 정간 하나 1/3초. 3초 = 9정간 → 둘째 각의 첫 음(연주 순서 9)
        assertEquals(9, state.indexAt(3.0))
        assertEquals(0, state.indexAt(-5.0))
        assertEquals(state.eventCount - 1, state.indexAt(999.0))
    }
}
