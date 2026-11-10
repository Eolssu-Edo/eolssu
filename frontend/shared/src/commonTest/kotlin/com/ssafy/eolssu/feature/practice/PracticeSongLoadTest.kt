package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSongLoadTest {
    @Test fun waitsForRealSongWithoutRecordingPracticeTimeOrDuplicatingRequests() {
        val response = CompletableDeferred<Song?>()
        var requests = 0
        val repository = repository { requests++; response.await() }
        loadTest(repository) { vm, meter, _ ->
            assertTrue(vm.uiState.value.isSongLoading)
            vm.onScreenShown()
            runCurrent()
            vm.retryLoadSong()
            vm.retryLoadSong()
            runCurrent()
            assertEquals(1, requests)
            assertFalse(meter.isOpen)
            assertNull(vm.uiState.value.song)
            response.complete(smallScoreSong())
            runCurrent()
            assertEquals("db-42", vm.uiState.value.song?.id)
            assertFalse(vm.uiState.value.isSongLoading)
            assertNull(vm.uiState.value.songError)
            assertTrue(meter.isOpen)
            vm.retryLoadSong()
            runCurrent()
            assertEquals(1, requests)
            vm.onScreenHidden()
            assertFalse(meter.isOpen)
        }
    }

    @Test fun networkOrScoreValidationFailureCanBeRetried() {
        var requests = 0
        loadTest(repository { if (++requests == 1) error("invalid response") else smallScoreSong() }) { vm, _, _ ->
            runCurrent()
            assertEquals(PracticeSongError.Failed, vm.uiState.value.songError)
            assertFalse(vm.uiState.value.isSongLoading)
            assertNull(vm.uiState.value.song)
            vm.retryLoadSong()
            assertTrue(vm.uiState.value.isSongLoading)
            assertNull(vm.uiState.value.songError)
            runCurrent()
            assertEquals("db-42", vm.uiState.value.song?.id)
            assertEquals(2, requests)
        }
    }

    @Test fun notFoundHasAnExplicitStateAndBackRemainsAvailable() {
        loadTest(repository { null }) { vm, meter, _ ->
            runCurrent()
            assertEquals(PracticeSongError.NotFound, vm.uiState.value.songError)
            assertTrue(vm.onBack())
            assertFalse(meter.isOpen)
        }
    }

    @Test fun leavingBeforeLoadCompletesDoesNotStartPracticeTimeLater() {
        val response = CompletableDeferred<Song?>()
        loadTest(repository { response.await() }) { vm, meter, _ ->
            vm.onScreenShown()
            runCurrent()
            vm.onScreenHidden()
            response.complete(smallScoreSong())
            runCurrent()
            assertFalse(meter.isOpen)
        }
    }

    @Test fun cancellationDoesNotBecomeARetryableServerError() {
        val response = CompletableDeferred<Song?>()
        var cancelled = false
        loadTest(repository {
            try { response.await() } catch (e: CancellationException) { cancelled = true; throw e }
        }) { vm, _, store ->
            runCurrent()
            store.clear()
            runCurrent()
            assertTrue(cancelled)
            assertNull(vm.uiState.value.songError)
            assertNull(vm.uiState.value.song)
        }
    }

    @Test fun aPersonalSheetIdIsNeverUsedToLoadAPublishedSong() {
        var catalogRequests = 0
        val personal = smallScoreSong().copy(title = "내 악보")
        var loadedSheetId: String? = null
        loadTest(repository { catalogRequests++; smallScoreSong() }, mine = true, loadPersonalSheet = { id ->
            loadedSheetId = id
            personal
        }) { vm, meter, _ ->
            vm.onScreenShown()
            runCurrent()
            assertEquals(0, catalogRequests)
            assertEquals("db-42", loadedSheetId)
            assertEquals(personal, vm.uiState.value.song)
            assertTrue(vm.uiState.value.solo)
            assertFalse(meter.isOpen)
        }
    }

    @Test fun personalTempoIsSeparateFromPublishedSongWithTheSameId() {
        val song = smallScoreSong()
        val settings = com.ssafy.eolssu.data.practice.PracticeSettings(bpmBySong = mapOf(song.id to 48))
        val personal = PracticeUiState(song = song, mine = true, settings = settings)
        assertEquals(song.baseBpm, personal.bpm)
        val saved = settings.applyTempo(personal.draft, song, mine = true)
        assertEquals(song.baseBpm, saved.bpmBySheet[song.id])
        assertEquals(48, personal.copy(mine = false, settings = saved).bpm)
    }

    private fun repository(load: suspend () -> Song?): SongRepository = object : SongRepository by SampleSongRepository() {
        override suspend fun getSong(songId: String): Song? = load()
    }

    private fun loadTest(
        repository: SongRepository,
        mine: Boolean = false,
        loadPersonalSheet: (suspend (String) -> Song?)? = null,
        block: suspend TestScope.(PracticeViewModel, PracticeTimeMeter, ViewModelStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val meter = PracticeTimeMeter(now = { 0L })
        try {
            val settings = FakeKeyValueStore()
            val vm = PracticeViewModel("db-42", false, repository, PracticeSettingsStore(settings), meter,
                PracticeTimeQueue(settings, null, com.ssafy.eolssu.core.network.SignedInMember(settings), backgroundScope), mine = mine, loadPersonalSheet = loadPersonalSheet)
            store.put("practice", vm)
            block(vm, meter, store)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
