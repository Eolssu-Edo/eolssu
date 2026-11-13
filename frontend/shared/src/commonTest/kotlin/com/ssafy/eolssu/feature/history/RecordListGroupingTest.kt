package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordSongSummary
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기록 목록(RECORD-01)의 곡 묶음: 같은 곡은 악기와 관계없이 한 묶음이고, 안에서 날짜별로 연주가 모인다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordListGroupingTest : CatalogViewModelTest() {
    private fun at(day: Int, hour: Int) = KoreanDateTime(2026, 11, day, hour, 0)

    private fun record(id: String, songId: String, title: String, day: Int, hour: Int, score: Int = 70) =
        PracticeRecord(id, songId, title, null, null, at(day, hour), 48, 60, score, emptyList(), "", emptyList())

    private fun summary(songId: String, title: String, count: Int, day: Int, hour: Int, best: Int) =
        RecordSongSummary(songId, title, count, at(day, hour), best, null)

    @Test
    fun sameTitleSongsBecomeOneGroupAcrossInstruments() {
        val state = RecordListUiState(
            summaries = listOf(
                summary("danso-airplane", "비행기", 2, 10, 9, 80),
                summary("doraji", "도라지타령", 1, 11, 9, 60),
                summary("sogeum-airplane", " 비행기 ", 3, 12, 9, 91),
            ),
        )
        assertEquals(listOf("도라지타령", "비행기"), state.groups.map { it.key })
        val airplane = state.groups.last()
        // 묶음 안의 곡은 최근에 연습한 곡이 앞이고, 개수·최고 점수는 곡들을 합친 값이다.
        assertEquals(listOf("sogeum-airplane", "danso-airplane"), airplane.songIds)
        assertEquals(5, airplane.recordCount)
        assertEquals(91, airplane.bestScore)
        // 처음에는 모든 묶음이 접혀 있다.
        assertTrue(state.openGroups.isEmpty())
        assertFalse(state.isSongOpen("danso-airplane"))
        val opened = state.copy(opened = setOf("비행기"))
        assertTrue(opened.isSongOpen("danso-airplane"))
        assertTrue(opened.isSongOpen("sogeum-airplane"))
        assertFalse(opened.isSongOpen("doraji"))
    }

    @Test
    fun groupsFollowTheNameOrderAndTheTitleSearch() {
        val state = RecordListUiState(
            summaries = listOf(
                summary("a", "아리랑", 1, 10, 9, 80),
                summary("b", "비행기", 1, 11, 9, 60),
                summary("c", "도라지타령", 1, 12, 9, 70),
                summary("d", "진도 아리랑", 1, 9, 9, 50),
            ),
        )
        assertEquals(listOf("도라지타령", "비행기", "아리랑", "진도 아리랑"), state.groups.map { it.key })
        assertEquals(listOf("진도 아리랑", "아리랑", "비행기", "도라지타령"), state.copy(nameOrder = SongSortOrder.Descending).groups.map { it.key })

        // 검색은 제목에 검색어가 든 곡만 남기고 앞뒤 공백을 무시한다. 검색해도 묶음을 펼치지 않는다.
        val searched = state.copy(query = " 아리랑 ")
        assertEquals(listOf("아리랑", "진도 아리랑"), searched.groups.map { it.key })
        assertTrue(searched.openGroups.isEmpty())
        assertTrue(state.copy(query = "산조").groups.isEmpty())
    }

    @Test
    fun recordsOfTheGroupAreSplitByDayInSortOrder() {
        val danso = listOf(record("d2", "danso", "비행기", 12, 10, 88), record("d1", "danso", "비행기", 10, 9, 60))
        val sogeum = listOf(record("s2", "sogeum", "비행기", 12, 8, 75), record("s1", "sogeum", "비행기", 11, 20, 70))
        val state = RecordListUiState(
            summaries = listOf(summary("danso", "비행기", 2, 12, 10, 88), summary("sogeum", "비행기", 2, 12, 8, 75)),
            pages = mapOf("danso" to RecordPages(danso, nextPage = null), "sogeum" to RecordPages(sogeum, nextPage = null)),
        )
        val group = state.groups.single()
        val days = state.days(group)
        assertEquals(listOf(12, 11, 10), days.map { it.date.day })
        assertEquals(listOf("d2", "s2"), days.first().records.map { it.id })
        // 곡 묶음을 펼치면 날짜도 처음부터 모두 펼쳐져 있다.
        assertTrue(days.all { state.isDayOpen(group, it.epochDay) })

        // 두 악기의 기록이 시각 순서대로 섞인다.
        assertEquals(listOf("d2", "s2", "s1", "d1"), state.records(group).map { it.id })
    }

    @Test
    fun recordsPastAnUnfinishedSongWaitForItsNextPage() {
        // 단소는 다음 페이지가 남아 11일 20시 뒤의 단소 기록을 아직 모른다. 소금의 10일 기록은 그 사이가 빌 수 있어 숨긴다.
        val danso = listOf(record("d2", "danso", "비행기", 12, 10), record("d1", "danso", "비행기", 11, 20))
        val sogeum = listOf(record("s2", "sogeum", "비행기", 12, 8), record("s1", "sogeum", "비행기", 10, 9))
        val summaries = listOf(summary("danso", "비행기", 3, 12, 10, 70), summary("sogeum", "비행기", 2, 12, 8, 70))
        val state = RecordListUiState(
            summaries = summaries,
            pages = mapOf("danso" to RecordPages(danso, nextPage = 1), "sogeum" to RecordPages(sogeum, nextPage = null)),
        )
        val group = state.groups.single()
        assertEquals(listOf("d2", "s2", "d1"), state.records(group).map { it.id })

        // 한 곡이라도 첫 페이지를 받기 전이면 순서를 정할 수 없어 아무것도 보이지 않는다.
        val waiting = state.copy(pages = mapOf("danso" to RecordPages(danso, nextPage = null), "sogeum" to RecordPages(loading = true)))
        assertTrue(waiting.records(group).isEmpty())
    }

    private class Records(list: List<PracticeRecord>) : PracticeRecordRepository {
        override val records: StateFlow<List<PracticeRecord>> = MutableStateFlow(list)
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String) = error("쓰지 않음")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun refresh(): Boolean = true
    }

    @Test
    fun openingAGroupLoadsEverySongAndDaysToggle() = runTest {
        val records = listOf(
            record("a2", "danso-arirang", "아리랑", 12, 10),
            record("a1", "sogeum-arirang", "아리랑", 11, 9),
            record("b1", "danso-doraji", "도라지타령", 12, 8),
        )
        val model = keep(RecordListViewModel(null, Records(records), now = { 0L }))
        runCurrent()
        val first = model.uiState.value
        assertEquals(listOf("도라지타령", "아리랑"), first.groups.map { it.key })
        // 처음에는 모든 묶음이 접혀 있다.
        assertTrue(first.openGroups.isEmpty())

        // '아리랑' 묶음을 펼치면 두 악기의 곡을 모두 받아 합친다.
        model.toggle("아리랑")
        runCurrent()
        val opened = model.uiState.value
        assertEquals(setOf("danso-arirang", "sogeum-arirang"), opened.pages.keys)
        val arirang = opened.groups.first { it.key == "아리랑" }
        assertEquals(listOf("a2", "a1"), opened.records(arirang).map { it.id })

        // 날짜는 모두 펼쳐진 채로 시작하고, 누른 날짜만 접었다 다시 편다.
        val day11 = at(11, 0).epochDay
        val day12 = at(12, 0).epochDay
        assertTrue(opened.isDayOpen(arirang, day11))
        model.toggleDay("아리랑", day11)
        assertFalse(model.uiState.value.isDayOpen(arirang, day11))
        assertTrue(model.uiState.value.isDayOpen(arirang, day12))
        model.toggleDay("아리랑", day11)
        assertTrue(model.uiState.value.isDayOpen(arirang, day11))

        model.toggle("도라지타령")
        runCurrent()
        assertTrue(model.uiState.value.isOpen("도라지타령"))
        assertEquals(listOf("b1"), model.uiState.value.records(model.uiState.value.groups.first { it.key == "도라지타령" }).map { it.id })

        // 이름 토글은 묶음 순서만 뒤집고, 검색은 제목에 검색어가 든 묶음만 남긴다.
        model.toggleNameOrder()
        assertEquals(listOf("아리랑", "도라지타령"), model.uiState.value.groups.map { it.key })
        model.search("도라지")
        assertEquals(listOf("도라지타령"), model.uiState.value.groups.map { it.key })
        model.search("")
        assertEquals(2, model.uiState.value.groups.size)
    }
}
