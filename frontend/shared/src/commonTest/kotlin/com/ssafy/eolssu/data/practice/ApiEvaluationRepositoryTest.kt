package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.toSummary
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 분석 상태(PRA-03)와 평가 결과(평가 조회 API) 응답을 앱 모델로 바꾸는지. 서버는 MockEngine으로 대신한다. */
class ApiEvaluationRepositoryTest {
    private val paths = mutableListOf<String>()
    private var songReads = 0

    @Test fun changedScoreVersionAndNewMemberCannotReuseOldResult() = runTest {
        var version = 2
        var allowed = true
        val repo = repository {
            if (!allowed) HttpStatusCode.NotFound to "{}"
            else HttpStatusCode.OK to """{"isSuccess":true,"result":{"sessionId":"41","songId":"2","scoreVersion":$version,
                "bpm":60,"startedAt":"2026-10-29T00:00:00Z","totalScore":80,"pitchScore":80,"timingScore":80,"durationScore":80,"notes":[]}}"""
        }
        assertNull(repo.result("41"))
        version = 1
        assertEquals(80, repo.result("41")?.total)
        allowed = false
        assertNull(repo.result("41"))
        assertEquals(3, paths.size)
    }

    @Test fun screenScoresArriveBeforeSongLookupAndMatchingSongIsReused() = runTest {
        val repo = repository {
            HttpStatusCode.OK to """{"isSuccess":true,"result":{"sessionId":"41","songId":"2","scoreVersion":1,
                "bpm":60,"startedAt":"2026-10-29T00:00:00Z","totalScore":80,"pitchScore":90,"timingScore":70,
                "durationScore":80,"notes":[{"noteIdx":0,"verdict":"PITCH"}]}}"""
        }
        val (result, loadedSong) = checkNotNull(repo.resultForScreen("41", song) { scores ->
            assertEquals(80, scores.total)
            assertEquals(listOf(90, 70, 80), scores.metrics.map { it.score })
            assertTrue(scores.errors.isEmpty())
            assertEquals(0, songReads)
        })
        assertEquals(song, loadedSong)
        assertEquals(1, result.errors.size)
        assertEquals(0, songReads)

        val staleSong = song.copy(scoreVersion = 2)
        val refreshed = repo.resultForScreen("41", staleSong) { assertEquals(0, songReads) }
        assertEquals(song, refreshed?.second)
        assertEquals(1, songReads)

        val denied = repository { HttpStatusCode.NotFound to "{}" }
        assertNull(denied.resultForScreen("41", song) { error("서버가 거절한 점수는 표시하지 않는다") })
        assertEquals(1, songReads)
    }

    /** 악보 시간축에 지속·쉼이 섞인 예시 곡을 서버 곡 ID "2"로 쓴다. */
    private val song: Song = SampleSongs.first { s -> s.timeline.any { it.note !is ScoreNote.Pitch } }.let { base ->
        var noteIndex = 0
        base.copy(id = "2", scoreVersion = 1, score = base.score.copy(events = base.timeline.map { event ->
            if (event.note is ScoreNote.Pitch) event.copy(note = ScoreNote.SourcePitch("황", 0), noteIndex = noteIndex++)
            else event
        }))
    }
    private val songs = object : SongRepository {
        override suspend fun getInstruments() = emptyList<com.ssafy.eolssu.data.song.CatalogInstrument>()
        override suspend fun getSongs(instrumentId: String?) = listOf(song.toSummary())

        override suspend fun getSong(songId: String): Song? {
            songReads++
            return song.takeIf { it.id == songId }
        }
    }

    private fun repository(reply: (path: String) -> Pair<HttpStatusCode, String>?) = ApiEvaluationRepository(
        createHttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                val (status, body) = reply(request.url.encodedPath) ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
        songs,
        fallback = SampleEvaluationRepository(now = { 0L }, analysisMillis = 0),
    )

    private fun session(status: String, failReason: String? = null) = HttpStatusCode.OK to
        """{"isSuccess":true,"code":200,"result":{"sessionId":"41","songId":"2","bpm":60,"bpmUnit":"DOTTED_QUARTER",
        |"startNoteIdx":0,"endNoteIdx":10,"status":"$status","evaluationId":null,"failReason":${failReason?.let { "\"$it\"" }},
        |"recordingAvailable":true,"startedAt":"2026-10-28T05:00:00Z","endedAt":null,"retryable":false}}""".trimMargin()

    @Test
    fun statusFollowsSessionState() = runTest {
        assertEquals(AnalysisStatus.Pending, repository { session("PENDING") }.analysisStatus("41"))
        assertEquals(AnalysisStatus.Pending, repository { session("RECORDING") }.analysisStatus("41"))
        assertEquals(AnalysisStatus.Done("41"), repository { session("DONE") }.analysisStatus("41"))
        assertEquals(
            AnalysisStatus.Failed(AnalysisFailure.NoSound),
            repository { session("FAILED", "NO_SOUND") }.analysisStatus("41"),
        )
        assertEquals("/api/practice-sessions/41", paths.first())
    }

    @Test
    fun statusWithoutOkResponseThrowsSoScreenCountsNoResponse() = runTest {
        assertFailsWith<IllegalStateException> { repository { HttpStatusCode.ServiceUnavailable to "{}" }.analysisStatus("41") }
        assertFailsWith<IllegalStateException> { repository { null }.analysisStatus("41") }
    }

    @Test
    fun resultKeepsServerScoresAndPlacesWrongNotesOnScore() = runTest {
        val pitches = song.timeline.filter { it.noteIndex != null }
        val evaluation = """{"isSuccess":true,"code":200,"result":{"sessionId":"41","evaluationId":"7","songId":"2","scoreVersion":1,
            |"bpm":60,"bpmUnit":"DOTTED_QUARTER","startedAt":"2026-10-20T06:20:00Z","endedAt":"2026-10-20T06:21:00Z",
            |"evaluatedAt":"2026-10-20T06:21:05Z","totalScore":81,"pitchScore":90,"timingScore":70,"durationScore":77,
            |"notes":[{"noteIdx":0,"verdict":"CORRECT"},{"noteIdx":1,"verdict":"PITCH","pitchErrorCent":60},
            |{"noteIdx":2,"verdict":"TIMING","onsetErrorMs":300},{"noteIdx":3,"verdict":"MISSED"}],
            |"recordingDurationMs":48600}}""".trimMargin()
        val repository = repository { HttpStatusCode.OK to evaluation }

        val result = repository.result("41")!!

        assertEquals("/api/practice-sessions/41/evaluation", paths.single())
        assertEquals(81, result.total)
        assertEquals(1, result.scoreVersion)
        assertEquals(
            listOf(
                MetricScore(EvaluationMetric.Pitch, 90),
                MetricScore(EvaluationMetric.Rhythm, 70),
                MetricScore(EvaluationMetric.Length, 77),
            ),
            result.metrics,
        )
        assertEquals("", result.feedback)
        assertEquals(false, result.sample)
        assertEquals(49, result.recordedSeconds)
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), result.recordedAt)
        assertEquals(
            listOf(
                ScoreError(pitches[1].column, pitches[1].row, EvaluationMetric.Pitch),
                ScoreError(pitches[2].column, pitches[2].row, EvaluationMetric.Rhythm),
                ScoreError(pitches[3].column, pitches[3].row, ScoreErrorKind.Missed),
            ),
            result.errors,
        )
        // 매번 서버 소유권을 확인하므로 다른 회원의 캐시 결과를 돌려주지 않는다.
        repository.result("41")
        assertEquals(2, paths.size)
    }

    @Test
    fun retrySendsNewKeyEachPressAndSortsResponses() = runTest {
        val keys = mutableListOf<String?>()
        var next = 0
        fun repository(status: HttpStatusCode?) = ApiEvaluationRepository(
            createHttpClient(
                MockEngine { request ->
                    paths += request.url.encodedPath
                    keys += request.headers["Idempotency-Key"]
                    respond("{}", status ?: error("연결 실패"), headersOf(HttpHeaders.ContentType, "application/json"))
                },
                ServerAddress.Production,
            ),
            songs,
            fallback = SampleEvaluationRepository(now = { 0L }),
            newKey = { "retry-${next++}" },
        )
        assertEquals(RetryOutcome.Started, repository(HttpStatusCode.Accepted).retryAnalysis("41"))
        assertEquals(RetryOutcome.Conflict, repository(HttpStatusCode.Conflict).retryAnalysis("41"))
        assertEquals(RetryOutcome.Busy, repository(HttpStatusCode.ServiceUnavailable).retryAnalysis("41"))
        assertEquals(RetryOutcome.NotFound, repository(HttpStatusCode.NotFound).retryAnalysis("41"))
        assertFailsWith<IllegalStateException> { repository(null).retryAnalysis("41") }
        assertEquals("/api/practice-sessions/41/retry", paths.first())
        assertEquals(listOf("retry-0", "retry-1", "retry-2", "retry-3"), keys.take(4))
    }

    @Test
    fun resultNotReadyIsNull() = runTest {
        assertNull(repository { HttpStatusCode.Conflict to "{}" }.result("41"))
        assertNull(repository { null }.result("41"))
    }

    @Test
    fun sampleIdsGoToFallbackWithoutServer() = runTest {
        val repository = repository { error("서버를 부르면 안 된다") }
        val id = repository.requestAnalysis(SampleSongs.first().id, 60, 48)
        val done = assertIs<AnalysisStatus.Done>(repository.analysisStatus(id))
        assertTrue(repository.result(done.evaluationId)!!.sample)
        assertTrue(paths.isEmpty())
    }
}
