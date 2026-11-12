package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class MySongsSearchTest : CatalogViewModelTest() {
    private val songs = listOf(
        song("1", "강원도아리랑", difficulty = 2),
        song("2", "구아리랑", difficulty = 3),
        song("3", "도라지타령", difficulty = 2, evaluable = false),
    )
    private val sheets = listOf(sheet("b", "밀양아리랑 연습"), sheet("a", "도라지 고친 악보"))

    @Test
    fun queryFiltersProvidedSongsByTitleTogetherWithLevelAndSort() {
        val state = MySongsUiState(songs = songs, query = " 아리랑 ")
        assertEquals(listOf("강원도아리랑", "구아리랑"), state.visibleSongs.map { it.title }, "앞뒤 공백은 무시하고 제목에 든 곡만 보인다")
        assertEquals(listOf("구아리랑"), state.copy(filter = SongLevelFilter.Intermediate).visibleSongs.map { it.title })
        assertEquals(listOf("구아리랑", "강원도아리랑"), state.copy(sort = SongSortOrder.Descending).visibleSongs.map { it.title })
        assertEquals(listOf("도라지타령"), state.copy(query = "도라지").visibleSongs.map { it.title }, "연습 전용 곡도 검색된다")
    }

    @Test
    fun sameQueryFiltersMySheetsKeepingReceivedOrder() {
        val state = MySongsUiState(songs = songs, sheets = sheets)
        assertEquals(sheets, state.visibleSheets, "검색어가 없으면 내 악보를 받은 순서대로 모두 보인다")
        assertEquals(listOf("a"), state.copy(query = "도라지").visibleSheets.map { it.id })
        val none = state.copy(query = "없는 제목")
        assertTrue(none.visibleSheets.isEmpty())
        assertEquals(2, none.sheetCount, "탭의 악보 개수는 검색과 관계없이 전체 개수다")
    }

    @Test
    fun resetSongFiltersClearsQueryAndLevelButClearQueryKeepsLevel() = runTest {
        val viewModel = keep(
            MySongsViewModel(CatalogSongs(), SampleMySheetRepository(), InstrumentRepository(FakeKeyValueStore()).apply { clear() }, SampleUserRepository()),
        )
        advanceUntilIdle()
        viewModel.selectFilter(SongLevelFilter.Elementary)
        viewModel.changeQuery("아리랑")
        viewModel.clearQuery()
        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SongLevelFilter.Elementary, viewModel.uiState.value.filter)
        viewModel.changeQuery("아리랑")
        viewModel.resetSongFilters()
        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SongLevelFilter.All, viewModel.uiState.value.filter)
    }

    private fun song(id: String, title: String, difficulty: Int, evaluable: Boolean = true) = SongSummary(
        id = id,
        title = title,
        difficulty = difficulty,
        jangdanName = "",
        baseBpm = 60,
        evaluable = evaluable,
    )

    private fun sheet(id: String, title: String) =
        MySheet(id, MySheetOrigin.Imported, title, updatedAt = KoreanDateTime(2026, 10, 17, 19, 0))
}
