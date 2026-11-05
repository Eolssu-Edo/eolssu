package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class LearnCatalogLookupTest : CatalogViewModelTest() {
    private fun progress() = LearnProgressRepository(FakeLessonApi(), FakeKeyValueStore())

    @Test fun unavailableCatalogShowsFailureAndRetryPreservesLearnProgress() = runTest {
        val songs = CatalogSongs().apply { list = { error("offline") } }
        val model = keep(LearnViewModel(songs, progress()))
        runCurrent()
        model.confirm(1)
        runCurrent()
        assertFalse(model.uiState.value.songsLoading)
        assertTrue(model.uiState.value.songsFailed)
        assertTrue(model.uiState.value.songIds.isEmpty())
        songs.list = { SampleSongs }
        model.retrySongs()
        runCurrent()
        assertFalse(model.uiState.value.songsLoading)
        assertFalse(model.uiState.value.songsFailed)
        assertEquals("arirang", model.uiState.value.songIds["아리랑"])
        assertTrue(model.uiState.value.isDone(1))
    }

    @Test fun emptyCatalogIsLoadedNotNetworkFailure() = runTest {
        val songs = CatalogSongs().apply { list = { emptyList() } }
        val model = keep(LearnViewModel(songs, progress()))
        runCurrent()
        assertFalse(model.uiState.value.songsLoading)
        assertFalse(model.uiState.value.songsFailed)
        assertTrue(model.uiState.value.songIds.isEmpty())
    }

    @Test fun retryDoesNotDuplicatePendingCatalogRequest() = runTest {
        val response = CompletableDeferred<Unit>()
        val songs = CatalogSongs().apply { list = { response.await(); emptyList() } }
        val model = keep(LearnViewModel(songs, progress()))
        runCurrent()
        model.retrySongs()
        model.retrySongs()
        runCurrent()
        assertEquals(1, songs.listCalls)
        response.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.songsLoading)
    }
}
