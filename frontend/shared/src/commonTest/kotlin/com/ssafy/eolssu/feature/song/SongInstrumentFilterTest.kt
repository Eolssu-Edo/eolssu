package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.toSummary
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SongInstrumentFilterTest : CatalogViewModelTest() {
    @Test fun evaluationListChangesServerFilterClearsSelectionAndRetainsSearch() = runTest {
        val repo = FilterSongs()
        val model = keep(SongListViewModel(repo))
        advanceUntilIdle()
        assertEquals(repo.instruments, model.uiState.value.instruments)
        model.changeQuery("필터")
        model.selectFilter(SongLevelFilter.Intro)
        model.pickSong("71")
        repo.delayedInstrumentId = "83"
        repo.gate = CompletableDeferred()
        model.selectInstrument("83")
        runCurrent()
        assertEquals(emptyList(), model.uiState.value.songs)
        assertNull(model.uiState.value.selectedSong)
        assertFalse(model.uiState.value.songPicked)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(null, "83"), repo.requests)
        assertEquals("83", model.uiState.value.instrumentId)
        assertEquals(listOf("73"), model.uiState.value.visibleSongs.map { it.id })
        assertEquals("필터", model.uiState.value.query)
        assertEquals(SongLevelFilter.Intro, model.uiState.value.filter)
        model.selectInstrument("83") // no duplicate request
        model.selectInstrument("unknown")
        advanceUntilIdle()
        assertEquals(2, repo.requests.size)
        model.resetFilters()
        advanceUntilIdle()
        assertNull(model.uiState.value.instrumentId)
        assertEquals("", model.uiState.value.query)
        assertEquals(SongLevelFilter.All, model.uiState.value.filter)
        assertEquals(4, model.uiState.value.songs.size)
    }

    @Test fun practiceListRetainsNonScorableSongsAndRetryKeepsInstrument() = runTest {
        val repo = FilterSongs()
        val model = keep(MySongsViewModel(repo, SampleMySheetRepository(now = { 0L })))
        advanceUntilIdle()
        assertEquals(repo.instruments, model.uiState.value.instruments)
        repo.delayedInstrumentId = "94"
        repo.gate = CompletableDeferred()
        model.selectInstrument("94")
        runCurrent()
        assertTrue(model.uiState.value.songs.isEmpty())
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("74"), model.uiState.value.songs.map { it.id })
        assertFalse(model.uiState.value.songs.single().evaluable)
        repo.fail = true
        model.retry()
        advanceUntilIdle()
        assertEquals(SongLoadState.Failed, model.uiState.value.loadState)
        assertEquals("94", model.uiState.value.instrumentId)
        repo.fail = false
        model.retry()
        model.retry()
        advanceUntilIdle()
        assertEquals(listOf(null, "94", "94", "94"), repo.requests)
        assertEquals(SongLoadState.Ready, model.uiState.value.loadState)
        model.selectTab(MySongsTab.Sheets)
        model.selectTab(MySongsTab.Songs)
        assertEquals("94", model.uiState.value.instrumentId)
    }

    @Test fun cancelledResponseCannotReplaceNewInstrumentInEitherList() = runTest {
        val repo = FilterSongs()
        val evaluation = keep(SongListViewModel(repo))
        val practice = keep(MySongsViewModel(repo, SampleMySheetRepository(now = { 0L })))
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        evaluation.selectInstrument("62")
        practice.selectInstrument("62")
        runCurrent()
        evaluation.selectInstrument("83")
        practice.selectInstrument("83")
        runCurrent()
        assertEquals("73", evaluation.uiState.value.songs.single().id)
        assertEquals("73", practice.uiState.value.songs.single().id)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("83", evaluation.uiState.value.instrumentId)
        assertEquals("73", evaluation.uiState.value.songs.single().id)
        assertEquals("83", practice.uiState.value.instrumentId)
        assertEquals("73", practice.uiState.value.songs.single().id)
    }
}

private class FilterSongs : SongRepository {
    val instruments = listOf(
        CatalogInstrument("51", "DANSO", "단소"), CatalogInstrument("62", "SOGEUM", "소금"),
        CatalogInstrument("83", "JEONGAK_DAEGEUM", "정악대금"), CatalogInstrument("94", "SANJO_DAEGEUM", "산조대금"),
    )
    val requests = mutableListOf<String?>()
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    var delayedInstrumentId = "62"
    override suspend fun getInstruments() = instruments
    override suspend fun getSongs(instrumentId: String?): List<SongSummary> {
        requests += instrumentId
        if (instrumentId == delayedInstrumentId) gate?.let { withContext(NonCancellable) { it.await() } }
        check(!fail)
        return instruments.mapIndexed { index, instrument ->
            SampleSongs.first().toSummary().copy(id = "${71 + index}", title = "필터 곡", difficulty = 1,
                instrumentId = instrument.instrumentId, evaluable = index != 3)
        }.filter { instrumentId == null || it.instrumentId == instrumentId }
    }
    override suspend fun getSong(songId: String) = null
}
