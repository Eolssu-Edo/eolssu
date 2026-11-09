package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
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

    /** 같은 제목의 단소·소금 악보가 있으면 배우는 악기의 악보를 고른다(S15P31E102-626). */
    @Test fun songStageUsesScoreOfLearningInstrument() = runTest {
        fun song(id: String, instrumentId: String?) = SongSummary(id, "아리랑", 1, "세마치", 60, evaluable = true, instrumentId = instrumentId)
        val summaries = listOf(song("arirang-danso", "i-danso"), song("arirang-sogeum", "i-sogeum"), song("unknown", null).copy(title = "새야새야"))
        val songs = object : SongRepository by CatalogSongs() {
            override suspend fun getSongs(instrumentId: String?) = summaries
        }
        val model = keep(LearnViewModel(songs, progress(), instrumentCodes = { mapOf("i-danso" to "DANSO", "i-sogeum" to "SOGEUM") }))
        runCurrent()
        assertEquals(GuideInstrument.Danso, model.uiState.value.instrument)
        assertEquals("arirang-danso", model.uiState.value.songIds["아리랑"])
        assertEquals("unknown", model.uiState.value.songIds["새야새야"])
        model.selectInstrument(GuideInstrument.Sogeum)
        runCurrent()
        assertEquals("arirang-sogeum", model.uiState.value.songIds["아리랑"])
        model.selectInstrument(GuideInstrument.DaegeumJeongak)
        runCurrent()
        assertEquals(null, model.uiState.value.songIds["아리랑"])
    }
}
