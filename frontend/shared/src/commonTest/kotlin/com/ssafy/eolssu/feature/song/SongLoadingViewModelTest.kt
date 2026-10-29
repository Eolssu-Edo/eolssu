package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.toSummary
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SongLoadingViewModelTest {
    @Test
    fun offlineListRetriesAndPreservesFilterAndSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = CatalogStub()
            repo.offline = true
            val vm = SongListViewModel(repo)
            advanceUntilIdle()
            assertEquals(SongLoadState.Failed, vm.uiState.value.loadState)
            vm.changeQuery("아리")
            vm.selectSong(SampleSongs.first().id)
            repo.offline = false
            vm.retry()
            vm.retry() // A second tap must not start a parallel request.
            advanceUntilIdle()
            assertEquals(2, repo.listRequests)
            assertEquals(SongLoadState.Ready, vm.uiState.value.loadState)
            assertEquals("아리", vm.uiState.value.query)
            assertEquals(SampleSongs.first().id, vm.uiState.value.selectedSong?.id)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun successfulEmptyListIsNotNetworkFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = CatalogStub().apply { songs = emptyList() }
            val vm = SongListViewModel(repo)
            advanceUntilIdle()
            assertEquals(SongLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(emptyList(), vm.uiState.value.visibleSongs)
            assertNull(vm.uiState.value.selectedSong)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun detailDistinguishesOfflineMissingAndRecoveredSong() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = CatalogStub().apply { offline = true }
            val song = SampleSongs.first()
            val vm = SongDetailViewModel(song.id, repo)
            advanceUntilIdle()
            assertEquals(SongLoadState.Failed, vm.uiState.value.loadState)
            repo.offline = false
            repo.songs = emptyList()
            vm.retry()
            advanceUntilIdle()
            assertEquals(SongLoadState.NotFound, vm.uiState.value.loadState)
            repo.songs = listOf(song)
            vm.retry()
            advanceUntilIdle()
            assertEquals(SongLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(song, vm.uiState.value.song)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun unknownDifficultyIsNotSilentlyChangedToIntermediate() {
        val summary = SampleSongs.first().toSummary().copy(difficulty = 5)
        assertNull(summary.level)
        assertEquals(5, summary.difficulty)
    }
}

private class CatalogStub : SongRepository {
    var offline = false
    var songs = SampleSongs
    var listRequests = 0
    override suspend fun getSongs(): List<SongSummary> {
        listRequests++
        check(!offline) { "Isolated offline test" }
        return songs.map { it.toSummary() }
    }
    override suspend fun getSong(songId: String): Song? {
        check(!offline) { "Isolated offline test" }
        return songs.firstOrNull { it.id == songId }
    }
}
