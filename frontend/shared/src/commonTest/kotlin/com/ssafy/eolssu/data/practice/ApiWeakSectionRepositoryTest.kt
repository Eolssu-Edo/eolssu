package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.ApiSongRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.revisionScoreResponse
import com.ssafy.eolssu.data.song.revisionSongResponse
import com.ssafy.eolssu.testing.CatalogEvaluations
import com.ssafy.eolssu.testing.CatalogSongs
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 취약 구간·비교 API(EVA-03·04)와 기록·평가 저장소를 묶는 저장소. 서버는 MockEngine으로 대신한다. */
class ApiWeakSectionRepositoryTest {
    @Test
    fun coldSelectionChecksPublicationWithoutDownloadingAnotherScore() = runTest {
        for (publishedVersion in listOf(1, 2)) {
            val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
            val catalog = mutableListOf<Pair<String, String?>>()
            val client = createHttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                val requestedVersion = request.url.parameters["scoreVersion"]
                val version = requestedVersion?.toInt() ?: publishedVersion
                if (path.startsWith("/api/songs/")) catalog += path to requestedVersion
                val result = when (path) {
                    "/api/practice-sessions/50/evaluation" -> evaluationResponse("50", 1)
                    "/api/songs/42" -> revisionSongResponse(version)
                    "/api/songs/42/score" -> revisionScoreResponse(version)
                    else -> error(path)
                }
                respond(ok(result).second, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try {
                val songs = ApiSongRepository(client, member)
                val evaluations = ApiEvaluationRepository(client, songs, member = member)
                val repository = ApiWeakSectionRepository(client, evaluations, songs, records, member)
                val source = checkNotNull(repository.source("50"))
                assertEquals(publishedVersion == 1, repository.isCurrent(source))
                assertEquals(listOf("/api/songs/42" to "1", "/api/songs/42/score" to "1", "/api/songs/42" to null), catalog)
            } finally { client.close() }
        }
    }

    @Test
    fun sourceLoadsItsEvaluationAndScoreBundleOnce() = runTest {
        val paths = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            val result = when (path) {
                "/api/practice-sessions/50/evaluation" -> evaluationResponse("50", 1)
                "/api/songs/42" -> revisionSongResponse(1)
                "/api/songs/42/score" -> revisionScoreResponse(1)
                else -> error(path)
            }
            respond(ok(result).second, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client)
            val repo = ApiWeakSectionRepository(client, ApiEvaluationRepository(client, songs), songs, records)
            val source = checkNotNull(repo.source("50"))
            assertEquals(1, source.song.scoreVersion)
            assertEquals(1, source.evaluation.errors.size)
            assertEquals(listOf("/api/practice-sessions/50/evaluation", "/api/songs/42", "/api/songs/42/score"), paths)
        } finally { client.close() }
    }

    @Test
    fun selectionAndLoopReuseOnlyTheScoreAndLookUpEachEvaluation() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        val paths = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            val version = request.url.parameters["scoreVersion"]?.toInt() ?: 1
            val result = when (path) {
                "/api/practice-sessions/50/evaluation" -> {
                    if (member.id != "A") return@MockEngine respond("{}", HttpStatusCode.NotFound)
                    evaluationResponse("50", 1)
                }
                "/api/practice-sessions/51/evaluation" -> evaluationResponse("51", 2)
                "/api/songs/42" -> revisionSongResponse(version)
                "/api/songs/42/score" -> revisionScoreResponse(version)
                else -> error(path)
            }
            respond(ok(result).second, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client, member)
            val evaluations = ApiEvaluationRepository(client, songs, member = member)
            val weak = ApiWeakSectionRepository(client, evaluations, songs, records, member)
            val detail = checkNotNull(songs.getSong("42"))
            assertSame(detail, songs.retainedSong("42"))
            assertSame(detail, checkNotNull(weak.source("50")).song)
            assertSame(detail, checkNotNull(weak.source("50")).song)
            assertEquals(listOf("/api/songs/42", "/api/songs/42/score",
                "/api/practice-sessions/50/evaluation", "/api/practice-sessions/50/evaluation"), paths)

            val otherSession = checkNotNull(weak.source("51"))
            assertEquals("51", otherSession.evaluation.id)
            assertEquals(2, otherSession.song.scoreVersion)
            assertNull(songs.retainedSong("42"), "과거 판본 조회는 게시 판본 확인이 아니다")
            assertEquals(listOf("/api/practice-sessions/51/evaluation", "/api/songs/42", "/api/songs/42/score"), paths.takeLast(3))

            member.set("B")
            assertNull(weak.source("50"))
            assertEquals("/api/practice-sessions/50/evaluation", paths.last())
            assertEquals(2, checkNotNull(weak.source("51")).song.scoreVersion)
            assertEquals(listOf("/api/practice-sessions/51/evaluation", "/api/songs/42", "/api/songs/42/score"), paths.takeLast(3))
        } finally { client.close() }
    }

    @Test fun historicalErrorsSectionResultAndComparisonUseTheSavedRevision() = runTest {
        val old = SampleSongs.first().copy(scoreVersion = 1)
        val current = old.copy(scoreVersion = 2)
        val asked = mutableListOf<Int?>()
        val songs = object : SongRepository by SampleSongRepository() {
            override suspend fun getSong(songId: String) = current.also { asked += null }
            override suspend fun getSong(songId: String, scoreVersion: Int) = old.also { asked += scoreVersion }
        }
        val evaluations = CatalogEvaluations().apply { cached = cached!!.copy(songId = old.id, scoreVersion = 1) }
        val originalRecords = records
        val historicalRecords = object : PracticeRecordRepository by originalRecords {
            override suspend fun detail(recordId: String) = originalRecords.detail(recordId)?.copy(scoreVersion = 1)
        }
        val client = createHttpClient(MockEngine {
            respond(comparison.second, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repo = ApiWeakSectionRepository(client, evaluations, songs, historicalRecords)
            val source = checkNotNull(repo.source("50"))
            assertEquals(1, source.song.scoreVersion)
            assertEquals(1, repo.sectionResult("50")?.song?.scoreVersion)
            assertEquals(1, repo.comparison("now")?.song?.scoreVersion)
            assertEquals(listOf<Int?>(1, 1, 1), asked)
        } finally { client.close() }
    }


    private val requests = mutableListOf<HttpRequestData>()
    private var reply: (path: String) -> Pair<HttpStatusCode, String>? = { null }

    private val evaluations = CatalogEvaluations().apply {
        cached = EvaluationResult(
            id = "50", songId = "arirang", total = 81, feedback = "",
            metrics = listOf(MetricScore(EvaluationMetric.Pitch, 85), MetricScore(EvaluationMetric.Rhythm, 78), MetricScore(EvaluationMetric.Length, 80)),
            errors = listOf(ScoreError(1, 0, EvaluationMetric.Pitch)), bpm = 48, recordedSeconds = 12,
            recordedAt = KoreanDateTime(2026, 11, 5, 10, 0), sample = false,
        )
    }

    private fun record(id: String, day: Int, hour: Int, song: String = "arirang", section: String? = null, errors: List<ScoreError> = emptyList()) = PracticeRecord(
        id, song, "아리랑", section, null, KoreanDateTime(2026, 11, day, hour, 0), 48, 60, 70, emptyList(), "", errors,
    )

    private val records = object : PracticeRecordRepository {
        val list = MutableStateFlow(
            listOf(
                record("now", 20, 15).let { current ->
                    current.copy(recordedAtMillis = current.recordedAtMillis + 1_000)
                },
                record("same-minute", 20, 15),
                record("later", 21, 9), record("same-day", 20, 9), record("section", 19, 9, section = "3–4각"),
                record("other", 19, 10, song = "doraji"), record("two-weeks", 7, 8), record("too-old", 6, 23),
            ),
        )
        override val records: StateFlow<List<PracticeRecord>> = list
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String) = error("unused")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun detail(recordId: String): PracticeRecord? =
            list.value.firstOrNull { it.id == recordId }?.copy(errors = listOf(ScoreError(2, 1, EvaluationMetric.Rhythm)))
    }

    private val repository = ApiWeakSectionRepository(
        createHttpClient(
            MockEngine { request ->
                requests += request
                val (status, body) = reply(request.url.encodedPath) ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
        evaluations, CatalogSongs(), records,
    )

    private fun ok(result: String) = HttpStatusCode.OK to """{"httpStatus":"OK","isSuccess":true,"message":"성공","code":200,"result":$result}"""

    private val comparison = ok(
        """
        {"baselineSessionId":"30","errorComparable":true,"scoreComparable":true,"bpmChanged":true,"notComparableReasons":[],"startNoteIdx":12,"endNoteIdx":27,"startColumnIdx":2,"endColumnIdx":3,
         "before":{"errorCount":9},"after":{"totalScore":81,"pitchScore":85,"timingScore":78,"durationScore":80,"errorCount":4,"bpm":48,"bpmUnit":"QUARTER"},
         "delta":{"errorCount":-5},"correctedNoteIndices":[12,14,15,20,21,22],"remainingErrors":[{"noteIdx":13,"verdict":"PITCH"}]}
        """.trimIndent(),
    )

    @Test
    fun weakSectionsReadsRecommendedColumnsAndEmptyList() = runTest {
        reply = { ok("""{"sessionId":"30","totalErrorCellCount":14,"items":[{"startNoteIdx":12,"endNoteIdx":27,"startColumnIdx":2,"endColumnIdx":3,"errorCount":9,"dominantError":"PITCH","reason":"..."}]}""") }
        assertEquals(listOf(WeakSectionPick(2, 3, 9)), repository.weakSections("30"))
        assertEquals("/api/practice-sessions/30/weak-sections", requests.single().url.encodedPath)
        assertEquals("1", requests.single().url.parameters["limit"])

        reply = { ok("""{"sessionId":"30","totalErrorCellCount":0,"items":[]}""") }
        assertEquals(emptyList(), repository.weakSections("30"))

        reply = { HttpStatusCode.Conflict to """{"httpStatus":"CONFLICT","isSuccess":false,"code":409,"result":{"errorCode":"STATE_CONFLICT"}}""" }
        assertNull(repository.weakSections("30"))
    }

    @Test
    fun sectionResultUsesThisSessionScoresAndComparesWithParent() = runTest {
        reply = { comparison }
        val result = checkNotNull(repository.sectionResult("50"))

        assertEquals(81, result.total)
        assertEquals(48, result.bpm)
        assertEquals(3, result.metrics.size)
        val check = assertIs<CheckLoad.Loaded>(result.check).check
        assertEquals(SessionCheck(true, null, 9, 4, -5, 6, 2, 3, scoreComparable = true, bpmChanged = true), check)
        val request = requests.single()
        assertEquals("/api/practice-sessions/50/comparison", request.url.encodedPath)
        assertNull(request.url.parameters["baselineSessionId"], "기준 세션은 서버가 세션을 만들 때 받은 parentSessionId를 쓴다")
    }

    @Test
    fun notComparableAndRejectedComparisonsAreKeptApart() = runTest {
        reply = {
            ok(
                """{"baselineSessionId":"30","comparable":false,"reason":"채점 모델이 달라요.","startColumnIdx":2,"endColumnIdx":3,
                   "before":{"errorCount":9},"after":{"errorCount":4},"delta":null,"correctedNoteIndices":[],"remainingErrors":[]}""",
            )
        }
        val notComparable = assertIs<CheckLoad.Loaded>(checkNotNull(repository.sectionResult("50")).check).check
        assertEquals(false, notComparable.comparable)
        assertEquals("채점 모델이 달라요.", notComparable.reason)
        assertNull(notComparable.deltaErrors)

        reply = { HttpStatusCode.BadRequest to """{"httpStatus":"BAD_REQUEST","isSuccess":false,"code":400,"result":{"errorCode":"INVALID_REQUEST"}}""" }
        assertEquals(CheckLoad.Rejected, checkNotNull(repository.sectionResult("50")).check)

        reply = { null }
        assertEquals(CheckLoad.Failed, checkNotNull(repository.sectionResult("50")).check)
    }

    @Test
    fun missingEvaluationMeansNoResult() = runTest {
        evaluations.cached = null
        assertNull(repository.sectionResult("50"))
        assertNull(repository.source("50"))
        evaluations.resultFailure = IllegalStateException("offline")
        assertNull(repository.source("50"))
    }

    @Test
    fun modelChangeKeepsErrorComparisonButDoesNotInventScoreDelta() = runTest {
        reply = { ok("""{"baselineSessionId":"30","errorComparable":true,"scoreComparable":false,"notComparableReasons":["MODEL_CHANGED"],"before":{"errorCount":9},"after":{"errorCount":4},"delta":{"errorCount":-5,"totalScore":null},"correctedNoteIndices":[12]}""") }
        val check = assertIs<CheckLoad.Loaded>(checkNotNull(repository.sectionResult("50")).check).check
        assertEquals(true, check.comparable)
        assertEquals(false, check.scoreComparable)
        assertEquals(-5, check.deltaErrors)
        assertNull(check.totalScoreDelta)
        reply = { ok("""{"errorComparable":true,"after":{}}""") }
        assertEquals(CheckLoad.Failed, checkNotNull(repository.sectionResult("50")).check)
    }

    @Test
    fun comparisonListsAllEarlierWholeSongRecordsWithoutPeriodLimit() = runTest {
        val comparison = checkNotNull(repository.comparison("now"))
        // 2주 제한 없이 이번 기록 이전의 같은 곡 전체 연주를 모두 담는다. 이후 기록·구간·다른 곡은 뺀다(S15P31E102-753).
        assertEquals(listOf("same-minute", "same-day", "two-weeks", "too-old"), comparison.previous.map { it.id })
        assertEquals(KoreanDateTime(2026, 11, 20, 15, 0), comparison.today)
        assertEquals(listOf(ScoreError(2, 1, EvaluationMetric.Rhythm)), comparison.current.errors)
    }

    @Test
    fun previousRecordComesWithServerCheckAgainstThatRecord() = runTest {
        reply = { comparison }
        val previous = checkNotNull(repository.previous("now", "same-day"))
        assertEquals("same-day", previous.record.id)
        assertEquals(listOf(ScoreError(2, 1, EvaluationMetric.Rhythm)), previous.record.errors)
        assertIs<CheckLoad.Loaded>(previous.check)
        assertEquals("/api/practice-sessions/now/comparison", requests.single().url.encodedPath)
        assertEquals("same-day", requests.single().url.parameters["baselineSessionId"])
    }
}

private fun evaluationResponse(session: String, version: Int) =
    """{"sessionId":"$session","songId":"42","scoreVersion":$version,"bpm":60,"startedAt":"2026-10-29T00:00:00Z","totalScore":80,"pitchScore":80,"timingScore":80,"durationScore":80,"notes":[{"noteIdx":1,"verdict":"PITCH"}]}"""
