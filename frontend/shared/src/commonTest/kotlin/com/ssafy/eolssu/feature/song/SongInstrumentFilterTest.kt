package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
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
    @Test fun unresolvedOrClearedInstrumentNeverRequestsAllSongs() = runTest {
        val repo = FilterSongs()
        val selected = InstrumentRepository(FakeKeyValueStore()).apply { clear() }
        val evaluation = keep(SongListViewModel(repo, selected, SampleUserRepository()))
        val practice = keep(MySongsViewModel(repo, SampleMySheetRepository(), selected, SampleUserRepository()))
        advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
        selected.select(Instrument.DaegeumSanjo)
        advanceUntilIdle()
        assertEquals(listOf<String?>("94", "94"), repo.requests)
        assertEquals("74", practice.uiState.value.songs.single().id)
        assertTrue(evaluation.uiState.value.visibleSongs.isEmpty())
        selected.clear()
        advanceUntilIdle()
        assertTrue(evaluation.uiState.value.songs.isEmpty())
        assertTrue(practice.uiState.value.songs.isEmpty())
        assertNull(evaluation.uiState.value.selectedSong)
        evaluation.retry()
        practice.retry()
        advanceUntilIdle()
        assertEquals(listOf<String?>("94", "94"), repo.requests)
    }

    @Test fun unavailableInstrumentCanRetryWithoutUnfilteredFallback() = runTest {
        val repo = FilterSongs().apply { missingInstrument = true }
        val selected = InstrumentRepository(FakeKeyValueStore())
        val evaluation = keep(SongListViewModel(repo, selected, SampleUserRepository()))
        val practice = keep(MySongsViewModel(repo, SampleMySheetRepository(), selected, SampleUserRepository()))
        advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
        assertEquals(SongLoadState.Failed, evaluation.uiState.value.loadState)
        assertEquals(SongLoadState.Failed, practice.uiState.value.loadState)
        repo.missingInstrument = false
        evaluation.retry()
        practice.retry()
        advanceUntilIdle()
        assertEquals(listOf<String?>("51", "51"), repo.requests)
    }

    @Test fun evaluationListChangesServerFilterClearsSelectionAndRetainsSearch() = runTest {
        val repo = FilterSongs()
        val selected = InstrumentRepository(FakeKeyValueStore())
        val model = keep(SongListViewModel(repo, selected, SampleUserRepository()))
        advanceUntilIdle()
        assertEquals(listOf<String?>("51"), repo.requests)
        model.changeQuery("필터")
        model.selectFilter(SongLevelFilter.Intro)
        model.pickSong("71")
        repo.delayedInstrumentId = "83"
        repo.gate = CompletableDeferred()
        selected.select(Instrument.DaegeumJeongak)
        runCurrent()
        assertEquals(emptyList(), model.uiState.value.songs)
        assertNull(model.uiState.value.selectedSong)
        assertFalse(model.uiState.value.songPicked)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf<String?>("51", "83"), repo.requests)
        assertEquals("83", model.uiState.value.instrumentId)
        assertEquals(listOf("73"), model.uiState.value.visibleSongs.map { it.id })
        assertEquals("필터", model.uiState.value.query)
        assertEquals(SongLevelFilter.Intro, model.uiState.value.filter)
        selected.select(Instrument.DaegeumJeongak) // no duplicate request
        advanceUntilIdle()
        assertEquals(2, repo.requests.size)
        model.resetFilters()
        advanceUntilIdle()
        assertEquals("83", model.uiState.value.instrumentId)
        assertEquals("", model.uiState.value.query)
        assertEquals(SongLevelFilter.All, model.uiState.value.filter)
        assertEquals(1, model.uiState.value.songs.size)
        assertEquals(2, repo.requests.size)
    }

    @Test fun practiceListRetainsNonScorableSongsAndRetryKeepsInstrument() = runTest {
        val repo = FilterSongs()
        val selected = InstrumentRepository(FakeKeyValueStore())
        val model = keep(MySongsViewModel(repo, SampleMySheetRepository(), selected, SampleUserRepository()))
        advanceUntilIdle()
        assertEquals(listOf<String?>("51"), repo.requests)
        model.selectFilter(SongLevelFilter.Intro)
        assertEquals(listOf<String?>("51"), repo.requests, "단계 선택은 추가 서버 요청 없이 처리한다")
        repo.delayedInstrumentId = "94"
        repo.gate = CompletableDeferred()
        selected.select(Instrument.DaegeumSanjo)
        runCurrent()
        assertTrue(model.uiState.value.songs.isEmpty())
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("74"), model.uiState.value.songs.map { it.id })
        assertFalse(model.uiState.value.songs.single().evaluable)
        assertEquals(SongLevelFilter.Intro, model.uiState.value.filter)
        assertEquals(listOf("74"), model.uiState.value.visibleSongs.map { it.id })
        repo.fail = true
        model.retry()
        advanceUntilIdle()
        assertEquals(SongLoadState.Failed, model.uiState.value.loadState)
        assertEquals("94", model.uiState.value.instrumentId)
        repo.fail = false
        repo.gate = CompletableDeferred()
        model.retry()
        model.retry()
        runCurrent()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf<String?>("51", "94", "94", "94"), repo.requests)
        assertEquals(SongLoadState.Ready, model.uiState.value.loadState)
        assertEquals(SongLevelFilter.Intro, model.uiState.value.filter)
        model.selectTab(MySongsTab.Sheets)
        model.selectTab(MySongsTab.Songs)
        assertEquals("94", model.uiState.value.instrumentId)
        assertEquals(SongLevelFilter.Intro, model.uiState.value.filter)
        model.selectFilter(SongLevelFilter.Intermediate)
        assertTrue(model.uiState.value.visibleSongs.isEmpty())
        model.selectFilter(SongLevelFilter.All)
        assertEquals(listOf("74"), model.uiState.value.visibleSongs.map { it.id })
        assertEquals(listOf<String?>("51", "94", "94", "94"), repo.requests)
    }

    @Test fun cancelledResponseCannotReplaceNewInstrumentInEitherList() = runTest {
        val repo = FilterSongs()
        val selected = InstrumentRepository(FakeKeyValueStore())
        val evaluation = keep(SongListViewModel(repo, selected, SampleUserRepository()))
        val practice = keep(MySongsViewModel(repo, SampleMySheetRepository(), selected, SampleUserRepository()))
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        selected.select(Instrument.Sogeum)
        runCurrent()
        selected.select(Instrument.DaegeumJeongak)
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
    var missingInstrument = false
    var gate: CompletableDeferred<Unit>? = null
    var delayedInstrumentId = "62"
    override suspend fun getInstruments() = if (missingInstrument) emptyList() else instruments
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
