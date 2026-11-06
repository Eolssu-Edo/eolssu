package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.data.practice.DailyPractice
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.GrowthSummary
import com.ssafy.eolssu.data.practice.ScoreTrend
import com.ssafy.eolssu.data.practice.TrendPlay
import com.ssafy.eolssu.data.practice.TrendScope
import com.ssafy.eolssu.data.practice.TrendSong
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogUnavailable
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 연습 기록지 ViewModel의 ‘점수 변화’ 요청: 늘 기본 평가(`FULL`)만 받고, 악기·곡 고르기에 맞춰 다시 받는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class GrowthViewModelTrendTest {

    private val danso = TrendSong("1", "아리랑", fullPlayCount = 3, sectionPlayCount = 1)
    private val reEvalOnly = TrendSong("2", "도라지타령", fullPlayCount = 0, sectionPlayCount = 2)
    private val sogeum = TrendSong("3", "밀양아리랑", fullPlayCount = 1, sectionPlayCount = 0)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun requestsOnlyBasicEvaluations() {
        val growth = FakeGrowth(serverSong = "1", songs = listOf(danso, reEvalOnly, sogeum))
        val vm = GrowthViewModel(growth, FakeSongs(), now = { 0L })

        vm.selectTrendSong("3")

        assertEquals(listOf<String?>(null, "3"), growth.asked.map { it.first })
        assertEquals(setOf(TrendScope.Full), growth.asked.map { it.second }.toSet())
        assertEquals("3", vm.uiState.value.scoreTrend.songId)
    }

    @Test
    fun serverSongWithOnlyReEvaluationsMovesToLatestBasicSong() {
        val growth = FakeGrowth(serverSong = "2", songs = listOf(reEvalOnly, sogeum, danso))
        val vm = GrowthViewModel(growth, FakeSongs(), now = { 0L })

        assertEquals(listOf<String?>(null, "3"), growth.asked.map { it.first })
        assertEquals("3", vm.uiState.value.scoreTrend.songId)
        assertEquals(listOf(70), vm.uiState.value.scoreTrend.view?.points?.map { it.total })
    }

    @Test
    fun reEvaluationOnlySongCannotBePicked() {
        val growth = FakeGrowth(serverSong = "1", songs = listOf(danso, reEvalOnly))
        val vm = GrowthViewModel(growth, FakeSongs(), now = { 0L })

        vm.selectTrendSong("2")

        assertEquals(listOf<String?>(null), growth.asked.map { it.first })
        assertEquals("1", vm.uiState.value.scoreTrend.songId)
    }

    @Test
    fun choosingInstrumentLoadsItsLatestBasicSong() {
        val growth = FakeGrowth(serverSong = "1", songs = listOf(danso, reEvalOnly, sogeum))
        val vm = GrowthViewModel(growth, FakeSongs(), now = { 0L })

        vm.selectTrendInstrument("20")
        vm.selectTrendInstrument("20")

        assertEquals(listOf<String?>(null, "3"), growth.asked.map { it.first })
        assertEquals("20", vm.uiState.value.scoreTrend.selectedInstrument?.instrumentId)
    }

    @Test
    fun catalogFailureLeavesSongPickerWithoutInstruments() {
        val growth = FakeGrowth(serverSong = "1", songs = listOf(danso, sogeum))
        val vm = GrowthViewModel(growth, FakeSongs(fail = true), now = { 0L })

        val trend = vm.uiState.value.scoreTrend
        assertNull(trend.catalog)
        assertEquals(emptyList(), trend.instruments)
        assertEquals(listOf(danso, sogeum), trend.instrumentSongs)
    }

    /** 곡마다 점수 하나(아리랑 80, 밀양아리랑 70). 재평가만 있는 곡은 FULL 점수가 없다. */
    private class FakeGrowth(private val serverSong: String, private val songs: List<TrendSong>) : GrowthRepository {
        val asked = mutableListOf<Pair<String?, TrendScope>>()

        override suspend fun summary(fromDay: Long, toDay: Long): GrowthSummary? = null

        override suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>? = null

        override suspend fun scoreTrend(songId: String?, scope: TrendScope, limit: Int): ScoreTrend {
            asked += songId to scope
            val id = songId ?: serverSong
            val score = mapOf("1" to 80, "3" to 70)[id]
            val plays = listOfNotNull(score?.let { TrendPlay("s$id", 0L, 60, 0, 3, it) })
            return ScoreTrend(id, scope, plays, comparable = false, songs = songs)
        }
    }

    private class FakeSongs(private val fail: Boolean = false) : SongRepository {
        override suspend fun getInstruments(): List<CatalogInstrument> {
            if (fail) throw CatalogUnavailable()
            return listOf(CatalogInstrument("10", "DANSO", "단소"), CatalogInstrument("20", "SOGEUM", "소금"))
        }

        override suspend fun getSongs(instrumentId: String?): List<SongSummary> = listOf(
            SongSummary("1", "아리랑", 1, "세마치", 60, true, "10"),
            SongSummary("2", "도라지타령", 1, "세마치", 60, true, "10"),
            SongSummary("3", "밀양아리랑", 1, "세마치", 60, true, "20"),
        )

        override suspend fun getSong(songId: String): Song? = null
    }
}
