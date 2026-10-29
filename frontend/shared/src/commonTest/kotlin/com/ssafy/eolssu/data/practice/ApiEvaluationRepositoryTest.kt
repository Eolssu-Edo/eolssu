package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
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

    /** 악보 시간축에 지속·쉼이 섞인 예시 곡을 서버 곡 ID "2"로 쓴다. */
    private val song: Song = SampleSongs.first { s -> s.timeline.any { it.note !is ScoreNote.Pitch } }.copy(id = "2")
    private val songs = object : SongRepository {
        override suspend fun getSongs() = listOf(song)

        override suspend fun getSong(songId: String) = song.takeIf { it.id == songId }
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
        val pitches = song.timeline.filter { it.note is ScoreNote.Pitch }
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
            ),
            result.errors,
        )
        // 한 번 받은 결과는 다시 묻지 않는다.
        repository.result("41")
        assertEquals(1, paths.size)
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
