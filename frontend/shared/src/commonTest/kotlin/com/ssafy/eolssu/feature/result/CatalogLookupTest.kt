package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.practice.ApiEvaluationRepository
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogEvaluations
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class CatalogLookupTest : CatalogViewModelTest() {
    @Test fun retryAfterScoresOnlyFetchesSongDetails() = runTest {
        var evaluationRequests = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                evaluationRequests++
                if (evaluationRequests > 1) respond("{}", HttpStatusCode.ServiceUnavailable)
                else respond(
                    """{"isSuccess":true,"result":{"sessionId":"41","songId":"2","scoreVersion":1,"bpm":60,
                        "startedAt":"2026-10-29T00:00:00Z","totalScore":81,"pitchScore":90,"timingScore":70,
                        "durationScore":77,"notes":[]}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        })
        val songs = CatalogSongs().apply { song = { error("악보 연결 실패") } }
        val client = createHttpClient(engine, ServerAddress.Production)
        try {
            val repo = ApiEvaluationRepository(client, songs)
            val model = keep(ResultViewModel("41", repo, songs, SamplePracticeRecordRepository()))
            runCurrent()
            assertEquals(81, model.uiState.value.scorePreview?.total)
            assertEquals(SongLookup.Failed, model.uiState.value.resultLookup)
            assertNull(model.uiState.value.result)
            songs.song = { SampleSongs.first().copy(id = "2", scoreVersion = 1) }

            model.retryResult()
            runCurrent()

            assertEquals(1, evaluationRequests)
            assertEquals(2, songs.songCalls)
            assertEquals(81, model.uiState.value.result?.total)
            assertEquals(SongLookup.Ready, model.uiState.value.songLookup)
        } finally {
            client.close()
        }
    }

    @Test fun resultCannotDisplayCoordinatesOnChangedScoreVersion() = runTest {
        val evaluations = CatalogEvaluations().apply { cached = cached!!.copy(sample = false, scoreVersion = 1) }
        val songs = CatalogSongs().apply { song = { SampleSongs.first().copy(scoreVersion = 2) } }
        val model = keep(ResultViewModel("41", evaluations, songs, SamplePracticeRecordRepository()))
        runCurrent()
        assertEquals(SongLookup.Failed, model.uiState.value.songLookup)
        assertNull(model.uiState.value.song)
        songs.song = { SampleSongs.first().copy(scoreVersion = 1) }
        model.retrySong()
        runCurrent()
        assertEquals(SongLookup.Ready, model.uiState.value.songLookup)
    }

    @Test fun resultLookupFailureDoesNotEscapeAndRetriesWithoutInventingScores() = runTest {
        val evaluations = CatalogEvaluations().apply { resultFailure = com.ssafy.eolssu.data.song.CatalogUnavailable() }
        val model = keep(ResultViewModel("41", evaluations, CatalogSongs(), SamplePracticeRecordRepository()))
        runCurrent()
        assertEquals(SongLookup.Failed, model.uiState.value.resultLookup)
        assertNull(model.uiState.value.result)
        evaluations.resultFailure = null
        model.retryResult()
        runCurrent()
        assertEquals(SongLookup.Ready, model.uiState.value.resultLookup)
        assertEquals(evaluations.cached, model.uiState.value.result)
    }

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
