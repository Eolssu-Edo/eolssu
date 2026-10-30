package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordCatalogLookupTest : CatalogViewModelTest() {
    @Test fun failedLookupStopsLoadingAndRetryRecoversWithoutPlayback() = runTest {
        val songs = CatalogSongs().apply { song = { error("offline") } }
        val model = keep(RecordDetailViewModel("sample-1", SamplePracticeRecordRepository(), songs))
        runCurrent()
        assertEquals(RecordSongLookup.Failed, model.uiState.value.songLookup)
        model.togglePlay()
        assertFalse(model.uiState.value.playing)
        songs.song = { SampleSongs.first() }
        model.retrySong()
        runCurrent()
        assertEquals(RecordSongLookup.Ready, model.uiState.value.songLookup)
        assertEquals("sample-1", model.uiState.value.record?.id)
        assertFalse(model.uiState.value.playing)
    }

    @Test fun missingSongAndMissingRecordDoNotStartPlayback() = runTest {
        val songs = CatalogSongs().apply { song = { null } }
        val model = keep(RecordDetailViewModel("sample-1", SamplePracticeRecordRepository(), songs))
        runCurrent()
        assertEquals(RecordSongLookup.Missing, model.uiState.value.songLookup)
        model.togglePlay()
        assertFalse(model.uiState.value.playing)
        model.show("missing-record")
        model.retrySong()
        runCurrent()
        assertNull(model.uiState.value.record)
        assertNull(model.uiState.value.song)
        assertEquals(1, songs.songCalls)
    }

    @Test fun delayedCancelledResponseCannotReplaceNewlySelectedRecord() = runTest {
        val oldResponse = CompletableDeferred<Unit>()
        val songs = CatalogSongs().apply {
            song = { id ->
                if (id == "arirang") withContext(NonCancellable) { oldResponse.await() }
                SampleSongs.first { it.id == id }
            }
        }
        val model = keep(RecordDetailViewModel("sample-1", SamplePracticeRecordRepository(), songs))
        runCurrent()
        model.show("sample-3")
        runCurrent()
        assertEquals("doraji", model.uiState.value.song?.id)
        oldResponse.complete(Unit)
        runCurrent()
        assertEquals("sample-3", model.uiState.value.record?.id)
        assertEquals("doraji", model.uiState.value.song?.id)
        assertEquals(RecordSongLookup.Ready, model.uiState.value.songLookup)
    }

    @Test fun delayedErrorCannotReplaceNewlySelectedRecordState() = runTest {
        val oldResponse = CompletableDeferred<Unit>()
        val songs = CatalogSongs().apply {
            song = { id ->
                if (id == "arirang") {
                    withContext(NonCancellable) { oldResponse.await(); error("old request") }
                }
                SampleSongs.first { it.id == id }
            }
        }
        val model = keep(RecordDetailViewModel("sample-1", SamplePracticeRecordRepository(), songs))
        runCurrent()
        model.show("sample-3")
        runCurrent()
        oldResponse.complete(Unit)
        runCurrent()
        assertEquals("sample-3", model.uiState.value.record?.id)
        assertEquals(RecordSongLookup.Ready, model.uiState.value.songLookup)
    }
}
