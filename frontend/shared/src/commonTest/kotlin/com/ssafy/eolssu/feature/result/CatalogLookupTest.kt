package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogEvaluations
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class CatalogLookupTest : CatalogViewModelTest() {
    @Test fun analysisLookupFailureAndMissingNeverRequestAnalysisAndCanRetry() = runTest {
        val songs = CatalogSongs().apply { song = { error("offline") } }
        val evaluations = CatalogEvaluations()
        val session = AnalysisSession(backgroundScope, evaluations, { 0 }, {})
        session.loadSong("arirang", 60, songs)
        assertEquals(SongLookup.Failed, session.state.value.songLookup)
        assertNull(session.state.value.failure)
        assertNull(session.state.value.evaluationId)
        assertEquals(0, evaluations.requests)
        songs.song = { null }
        session.loadSong("arirang", 60, songs)
        assertEquals(SongLookup.Missing, session.state.value.songLookup)
        assertEquals(0, evaluations.requests)
        songs.song = { SampleSongs.first() }
        session.loadSong("arirang", 60, songs)
        runCurrent()
        assertEquals(SongLookup.Ready, session.state.value.songLookup)
        assertEquals(1, evaluations.requests)
        assertEquals(AnalysisFailure.NoSound, session.state.value.failure)
        assertNull(session.state.value.evaluationId)
    }

    @Test fun analysisLookupCancellationPropagatesWithoutStartingAnalysis() = runTest {
        val songs = CatalogSongs().apply { song = { throw CancellationException("left screen") } }
        val evaluations = CatalogEvaluations()
        val session = AnalysisSession(backgroundScope, evaluations, { 0 }, {})
        assertFailsWith<CancellationException> { session.loadSong("arirang", 60, songs) }
        assertEquals(0, evaluations.requests)
        assertNull(session.state.value.failure)
        assertNull(session.state.value.evaluationId)
    }

    @Test fun resultFailureDoesNotLoseEvaluationAndRetryLoadsSong() = runTest {
        val songs = CatalogSongs().apply { song = { error("offline") } }
        val evaluations = CatalogEvaluations()
        val records = SamplePracticeRecordRepository()
        val model = keep(ResultViewModel("result", evaluations, songs, records))
        runCurrent()
        assertEquals(SongLookup.Failed, model.uiState.value.songLookup)
        assertEquals(evaluations.cached, model.uiState.value.result)
        model.save()
        runCurrent()
        assertEquals(4, records.records.value.size)
        songs.song = { SampleSongs.first() }
        model.retrySong()
        runCurrent()
        assertEquals(SongLookup.Ready, model.uiState.value.songLookup)
        assertEquals(2, songs.songCalls)
        assertNull(model.uiState.value.savedRecord)
    }

    @Test fun resultMissingSongIsDistinctFromTransportFailure() = runTest {
        val songs = CatalogSongs().apply { song = { null } }
        val model = keep(ResultViewModel("result", CatalogEvaluations(), songs, SamplePracticeRecordRepository()))
        runCurrent()
        assertEquals(SongLookup.Missing, model.uiState.value.songLookup)
        assertNull(model.uiState.value.song)
    }

    @Test fun resultRetryWhileLoadingDoesNotDuplicateRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val songs = CatalogSongs().apply { song = { gate.await(); SampleSongs.first() } }
        val model = keep(ResultViewModel("result", CatalogEvaluations(), songs, SamplePracticeRecordRepository()))
        runCurrent()
        model.retrySong()
        model.retrySong()
        runCurrent()
        assertEquals(1, songs.songCalls)
        assertEquals(SongLookup.Loading, model.uiState.value.songLookup)
        gate.complete(Unit)
        runCurrent()
        assertEquals(SongLookup.Ready, model.uiState.value.songLookup)
    }

    @Test fun missingEvaluationDoesNotLookupOrInventResult() = runTest {
        val songs = CatalogSongs()
        val model = keep(ResultViewModel("absent", CatalogEvaluations().apply { cached = null }, songs, SamplePracticeRecordRepository()))
        runCurrent()
        model.retrySong()
        assertEquals(0, songs.songCalls)
        assertNull(model.uiState.value.result)
    }
}
