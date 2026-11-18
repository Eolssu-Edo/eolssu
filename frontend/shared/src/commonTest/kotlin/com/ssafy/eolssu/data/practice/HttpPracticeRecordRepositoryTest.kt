package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.ApiAuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/** 연습 기록 API(REC-01~04, `docs/specs/practice-records.md`)를 앱 기록으로 바꾸는지. 서버는 MockEngine으로 대신한다. */
class HttpPracticeRecordRepositoryTest {
    private class Sent(val method: HttpMethod, val path: String, val query: String, val body: String?)

    private val sent = mutableListOf<Sent>()

    private fun repository(
        evaluation: EvaluationResult? = null,
        member: SignedInMember? = null,
        timeSource: TimeSource = TimeSource.Monotonic,
        reply: suspend (Sent) -> Pair<HttpStatusCode, String>?,
    ) = repository(client(reply = reply), evaluation, member, timeSource)

    private fun client(
        dispatcher: CoroutineDispatcher? = null,
        reply: suspend (Sent) -> Pair<HttpStatusCode, String>?,
    ) = createHttpClient(
        MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val call = Sent(request.method, request.url.encodedPath, request.url.encodedQuery, (request.body as? TextContent)?.text)
                sent += call
                val (status, body) = reply(call) ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }),
        ServerAddress.Production,
    )

    private fun repository(
        client: HttpClient,
        evaluation: EvaluationResult? = null,
        member: SignedInMember? = null,
        timeSource: TimeSource = TimeSource.Monotonic,
    ) = HttpPracticeRecordRepository(
        client,
        object : EvaluationRepository {
            override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int) = error("쓰지 않음")
            override suspend fun analysisStatus(analysisId: String) = error("쓰지 않음")
            override suspend fun retryAnalysis(analysisId: String) = error("쓰지 않음")
            override suspend fun result(evaluationId: String) = evaluation
        },
        member,
        timeSource,
    )

    private fun ok(result: String) = HttpStatusCode.OK to """{"isSuccess":true,"code":200,"result":$result}"""

    private fun item(id: String, songId: String, title: String, startedAt: String, full: Boolean = true, isNew: Boolean = false) =
        """{"sessionId":"$id","songId":"$songId","title":"$title","isFullSong":$full,"startColumnIdx":${if (full) "0" else "2"},
        |"endColumnIdx":${if (full) "9" else "3"},"recordingDurationMs":48400,"bpm":60,"bpmUnit":"DOTTED_QUARTER","totalScore":84,
        |"startNoteIdx":0,"endNoteIdx":10,"startedAt":"$startedAt","recordingAvailable":true,"memo":null,"isNew":$isNew}""".trimMargin()

    @Test
    fun detailOpensWithoutListCache() = runTest {
        val repo = repository(evaluation = result.copy(scoreVersion = 1)) {
            ok("""{"sessionId":"41","songId":"1","songTitle":"당시 제목","scoreVersion":1,"status":"DONE",
                "bpm":60,"startedAt":"2026-10-20T06:20:00Z","recordingAvailable":true,"memo":"보존할 메모"}""")
        }
        val detail = checkNotNull(repo.detail("41"))
        assertEquals("당시 제목", detail.songTitle)
        assertEquals(84, detail.score)
        assertEquals("보존할 메모", detail.memo)
        assertTrue(detail.recordingAvailable)
        assertEquals(listOf("/api/practice-sessions/41"), sent.map { it.path })
    }

    @Test
    fun pagedWholeSongKeepsItsSectionInDetailOnlyForTheSelectingAccount() = runTest {
        val member = SignedInMember(FakeKeyValueStore())
        member.set("first")
        val session = member.session.value
        val repo = repository(evaluation = result, member = member) { call ->
            when (call.path) {
                "/api/users/me/practice-records" -> ok("""{"songs":[{"songId":"1"}],"songId":"1",
                    |"items":[${item("41", "1", "아리랑", "2026-10-20T06:20:00Z")}],"totalPages":1}""".trimMargin())
                "/api/practice-sessions/41" -> ok("""{"sessionId":"41","songId":"1","status":"DONE"}""")
                else -> null
            }
        }
        val row = checkNotNull(repo.loadListPage()).records.single()
        assertTrue(repo.records.value.isEmpty())
        assertNull(repo.detailPreview(row.id))

        repo.rememberDetailPreview(row, session)
        assertEquals(row, repo.detailPreview(row.id))
        assertNull(checkNotNull(repo.detail(row.id)).section)

        member.set("first")
        assertNull(repo.detailPreview(row.id))
        repo.rememberDetailPreview(row, session)
        assertNull(repo.detailPreview(row.id))
    }

    @Test
    fun refreshMergesEverySongAndPageNewestFirst() = runTest {
        val repo = repository { call ->
            val q = call.query
            when {
                "songId=" !in q -> ok("""{"totalRecordCount":3,"songs":[{"songId":"1"},{"songId":"2"}],"songId":"1",
                    |"items":[${item("11", "1", "아리랑", "2026-10-20T06:20:00Z", isNew = true)}],"totalPages":2}""".trimMargin())
                "songId=1" in q && "page=1" in q -> ok("""{"songs":[],"songId":"1",
                    |"items":[${item("10", "1", "아리랑", "2026-10-12T09:40:00Z", full = false)}],"totalPages":2}""".trimMargin())
                "songId=2" in q -> ok("""{"songs":[],"songId":"2","items":[${item("20", "2", "도라지타령", "2026-10-15T12:10:00Z")}],"totalPages":1}""")
                else -> null
            }
        }

        assertTrue(repo.refresh())

        val records = repo.records.value
        assertEquals(listOf("11", "20", "10"), records.map { it.id })
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), records[0].recordedAt)
        assertEquals(null, records[0].section)
        assertEquals("3–4각", records[2].section)
        assertEquals(48, records[0].recordedSeconds)
        assertTrue(records[0].isNew)
        assertTrue(records[0].recordingAvailable)
        assertTrue(sent.all { it.path == "/api/users/me/practice-records" && "size=100" in it.query && "sort=NEWEST" in it.query })
    }

    @Test
    fun refreshFailureKeepsPreviousRecords() = runTest {
        var fail = false
        val repo = repository {
            if (fail) HttpStatusCode.ServiceUnavailable to "{}"
            else ok("""{"songs":[{"songId":"1"}],"songId":"1","items":[${item("11", "1", "아리랑", "2026-10-20T06:20:00Z")}],"totalPages":1}""")
        }
        assertTrue(repo.refresh())
        fail = true
        assertFalse(repo.refresh())
        assertEquals(listOf("11"), repo.records.value.map { it.id })
    }

    @Test
    fun recentPracticeUsesOneSmallListPageWhenDedicatedEndpointIsUnavailable() = runTest {
        val latest = item("9000", "2", "도라지타령", "2026-11-12T01:00:00Z", full = false)
        val groups = """[{"songId":"1","recordCount":101},{"songId":"2","recordCount":1}]"""
        val repo = repository { call ->
            when {
                call.path == RECENT_PATH -> HttpStatusCode.NotFound to "{}"
                "weekOf=2026-11-12" in call.query ->
                    ok("""{"songs":[{"songId":"2","recordCount":1},{"songId":"1","recordCount":101}],
                        "songId":"2","items":[$latest],"totalPages":1,"week":{"recordCount":4,"bestScore":84}}""")
                "songId=2" in call.query ->
                    ok("""{"songs":$groups,"songId":"2","items":[$latest],"totalPages":1}""")
                "page=1" in call.query ->
                    ok("""{"songs":$groups,"songId":"1","items":[${item("100", "1", "아리랑", "2026-11-08T14:58:00Z")}],"totalPages":2}""")
                else -> {
                    val older = (0 until 100).joinToString(",") { index ->
                        val startedAt = if (index < 3) "2026-11-08T15:00:00Z" else "2026-11-08T14:59:00Z"
                        item((1000 - index).toString(), "1", "아리랑", startedAt)
                    }
                    ok("""{"songs":$groups,"songId":"1","items":[$older],"totalPages":2}""")
                }
            }
        }

        val recent = checkNotNull(repo.loadRecentPractice(KoreanDateTime(2026, 11, 12, 10, 0)))

        assertEquals(2, sent.size)
        assertEquals(RECENT_PATH, sent.first().path)
        val call = sent.last()
        assertEquals(HttpMethod.Get, call.method)
        assertEquals("/api/users/me/practice-records", call.path)
        assertEquals(setOf("sort=NEWEST", "page=0", "size=1", "groupOrder=LAST_STARTED", "weekOf=2026-11-12"), call.query.split('&').toSet())
        assertEquals("9000", recent.latest?.id)
        assertEquals("도라지타령", recent.latest?.songTitle)
        assertEquals("3–4각", recent.latest?.section)
        assertEquals(84, recent.latest?.score)
        assertEquals(RecordWeek(4, 84), recent.week)
        assertTrue(repo.records.value.isEmpty())
    }

    private val recentDay = KoreanDateTime(2026, 11, 12, 10, 0)

    private fun recentResponse() = ok("""{"latest":${item("41", "1", "아리랑", "2026-11-12T01:00:00Z")},
        "week":{"recordCount":1,"bestScore":84}}""")

    @Test
    fun recentPracticeUsesOnlyTheDedicatedEndpoint() = runTest {
        val repo = repository { recentResponse() }

        val recent = checkNotNull(repo.loadRecentPractice(recentDay))

        val call = sent.single()
        assertEquals(HttpMethod.Get, call.method)
        assertEquals(RECENT_PATH, call.path)
        assertEquals("weekOf=2026-11-12", call.query)
        assertEquals("41", recent.latest?.id)
        assertEquals("아리랑", recent.latest?.songTitle)
        assertEquals(RecordWeek(1, 84), recent.week)
        assertTrue(repo.records.value.isEmpty())
    }

    @Test
    fun recentPracticeUsesAllRecordsWhenOldServerOmitsWeek() = runTest {
        val repo = repository { call ->
            if (call.path == RECENT_PATH) {
                HttpStatusCode.NotFound to "{}"
            } else if ("songId=2" in call.query) {
                ok("""{"songs":[{"songId":"1"},{"songId":"2"}],"songId":"2",
                    "items":[${item("42", "2", "도라지타령", "2026-11-12T01:00:00Z")}],"totalPages":1}""")
            } else {
                ok("""{"songs":[{"songId":"1"},{"songId":"2"}],"songId":"1",
                    "items":[${item("41", "1", "아리랑", "2026-11-11T01:00:00Z")}],"totalPages":1}""")
            }
        }

        val recent = checkNotNull(repo.loadRecentPractice(recentDay))

        assertEquals("42", recent.latest?.id)
        assertEquals(RecordWeek(2, 84), recent.week)
        assertEquals(4, sent.size)
        assertTrue("size=1" in sent[1].query.split('&'))
        assertTrue(sent.drop(2).all { "size=100" in it.query.split('&') })
    }

    @Test
    fun recentPracticeHandlesAnEmptyWeekResponseInOneRequest() = runTest {
        val repo = repository {
            ok("""{"latest":null,"week":{"recordCount":0,"bestScore":null}}""")
        }

        assertEquals(RecentPracticeSummary(null, RecordWeek(0, null)), repo.loadRecentPractice(recentDay))
        assertEquals(1, sent.size)
    }

    @Test
    fun recentPracticeKeepsCachedSummaryAfterFailedRefreshOnlyForTheSameWeekAndLogin() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).also { it.set("first") }
        val time = TestTimeSource()
        var fail = false
        val repo = repository(member = member, timeSource = time) {
            if (fail) HttpStatusCode.ServiceUnavailable to "{}" else recentResponse()
        }
        val original = checkNotNull(repo.loadRecentPractice(recentDay))
        fail = true
        time += 30.seconds

        assertNull(repo.loadRecentPractice(recentDay))
        assertEquals(2, sent.size)
        assertEquals(original, repo.cachedRecentPractice(KoreanDateTime(2026, 11, 15, 23, 59)))
        assertNull(repo.cachedRecentPractice(KoreanDateTime(2026, 11, 16, 0, 0)))

        member.set("first")
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    @Test
    fun homePrefetchAndMyPageShareOneRequestAndReuseTheFreshResponse() = runTest {
        val requested = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val time = TestTimeSource()
        val member = SignedInMember(FakeKeyValueStore()).also { it.set("member") }
        val client = client(StandardTestDispatcher(testScheduler)) {
            requested.complete(Unit)
            reply.await()
            recentResponse()
        }
        val repo = repository(client, member = member, timeSource = time)
        val prefetch = async { repo.prefetchRecentPractice(recentDay) }
        requested.await()
        val myPage = async(start = CoroutineStart.UNDISPATCHED) { repo.loadRecentPractice(recentDay) }
        reply.complete(Unit)
        prefetch.await()
        val summary = checkNotNull(myPage.await())
        assertEquals(listOf(RECENT_PATH), sent.map { it.path })

        time += 29.seconds
        assertEquals(summary, repo.loadRecentPractice(recentDay))
        assertEquals(1, sent.size)

        time += 1.seconds
        assertEquals(summary, repo.loadRecentPractice(recentDay))
        assertEquals(2, sent.size)
    }

    @Test
    fun homePrefetchDoesNotLoadLegacyPages() = runTest {
        val client = client(StandardTestDispatcher(testScheduler)) { HttpStatusCode.NotFound to "{}" }
        val repo = repository(client)

        repo.prefetchRecentPractice(recentDay)

        assertEquals(RECENT_PATH, sent.single().path)
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun homePrefetchReleasesTheAuthenticationLockAt300msIncludingItsQueueWait() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).also { it.set("member") }
        val client = client(StandardTestDispatcher(testScheduler)) { call ->
            when (call.path) {
                RECENT_PATH -> awaitCancellation()
                PROFILE_PATH -> ok("{}")
                else -> error("예상하지 않은 요청")
            }
        }
        val repo = repository(client, member = member)
        launch { member.changing { delay(200) } }
        runCurrent()
        val prefetch = launch { repo.prefetchRecentPractice(recentDay) }
        runCurrent()

        advanceTimeBy(199)
        runCurrent()
        assertTrue(sent.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(RECENT_PATH), sent.map { it.path })

        val profile = async { member.inSession { client.sendOrNull { get(PROFILE_PATH) }?.status } }
        advanceTimeBy(99)
        runCurrent()
        assertFalse(profile.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(prefetch.isCompleted, "인증 잠금 대기를 포함해 300ms에 선조회가 끝나야 한다")
        assertTrue(profile.isCompleted)
        assertEquals(HttpStatusCode.OK, profile.await())
        assertEquals(listOf(RECENT_PATH, PROFILE_PATH), sent.map { it.path })
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    @Test
    fun homePrefetch401LeavesAuthenticationRefreshToMyPage() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).also { it.set("member") }
        var refreshed = false
        val client = client(StandardTestDispatcher(testScheduler)) { call ->
            when (call.path) {
                RECENT_PATH -> if (refreshed) recentResponse() else HttpStatusCode.Unauthorized to "{}"
                PROFILE_PATH -> ok("{}")
                REFRESH_PATH -> {
                    refreshed = true
                    HttpStatusCode.NoContent to ""
                }
                else -> error("예상하지 않은 요청")
            }
        }
        val repo = repository(client, member = member)

        repo.prefetchRecentPractice(recentDay)

        assertEquals(listOf(RECENT_PATH), sent.map { it.path })
        assertNull(repo.cachedRecentPractice(recentDay))
        assertEquals(HttpStatusCode.OK, member.inSession { client.sendOrNull { get(PROFILE_PATH) }?.status })
        assertEquals("41", repo.loadRecentPractice(recentDay)?.latest?.id)
        assertEquals(listOf(RECENT_PATH, PROFILE_PATH, RECENT_PATH, REFRESH_PATH, RECENT_PATH), sent.map { it.path })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun logoutCancelsPrefetchAndPreventsMyPageFromStartingAnotherRequest() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).also { it.set("member") }
        val client = client(StandardTestDispatcher(testScheduler)) { call ->
            when (call.path) {
                RECENT_PATH -> awaitCancellation()
                "/api/auth/sign-out" -> HttpStatusCode.NoContent to ""
                else -> error("예상하지 않은 요청")
            }
        }
        val repo = repository(client, member = member)
        val auth = ApiAuthRepository(client, cookies = null, member = member, scope = this)
        var signingOut = false
        val prefetch = launch { repo.prefetchRecentPractice(recentDay) }
        runCurrent()
        val myPage = launch { repo.loadRecentPractice(recentDay) { !signingOut } }
        runCurrent()
        assertEquals(listOf(RECENT_PATH), sent.map { it.path })
        assertFalse(myPage.isCompleted)

        signingOut = true
        prefetch.cancel()
        runCurrent()
        assertTrue(myPage.isCompleted)
        assertEquals(listOf(RECENT_PATH), sent.map { it.path })

        auth.logOut()
        runCurrent()

        assertTrue(prefetch.isCompleted)
        assertEquals(0L, testScheduler.currentTime)
        assertNull(member.id)
        assertEquals(listOf(RECENT_PATH, "/api/auth/sign-out"), sent.map { it.path })
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    @Test
    fun saveAndDeleteInvalidateTheRecentPracticeSummary() = runTest {
        val repo = repository { call ->
            when (call.method) {
                HttpMethod.Get -> recentResponse()
                HttpMethod.Put -> ok(saved)
                HttpMethod.Delete -> ok("""{"sessionId":"41","deleted":true}""")
                else -> error("예상하지 않은 요청")
            }
        }
        checkNotNull(repo.loadRecentPractice(recentDay))
        repo.saveEvaluation(result, "아리랑")
        assertNull(repo.cachedRecentPractice(recentDay))

        checkNotNull(repo.loadRecentPractice(recentDay))
        assertEquals(RecordDeleteOutcome.Deleted, repo.delete("41"))
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    @Test
    fun recentPracticeDoesNotRestoreAResponseStartedBeforeDelete() = runTest {
        val requested = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val repo = repository { call ->
            if (call.method == HttpMethod.Delete) {
                ok("""{"sessionId":"41","deleted":true}""")
            } else {
                requested.complete(Unit)
                reply.await()
                recentResponse()
            }
        }
        val pending = async { repo.loadRecentPractice(recentDay) }
        requested.await()
        try {
            assertEquals(RecordDeleteOutcome.Deleted, repo.delete("41"))
        } finally {
            reply.complete(Unit)
        }

        assertNull(pending.await())
        assertNull(repo.cachedRecentPractice(recentDay))
    }

    private val result = EvaluationResult(
        id = "41", songId = "1", total = 84, feedback = "",
        metrics = listOf(MetricScore(EvaluationMetric.Pitch, 88), MetricScore(EvaluationMetric.Rhythm, 80), MetricScore(EvaluationMetric.Length, 84)),
        errors = listOf(ScoreError(0, 1, EvaluationMetric.Pitch)), bpm = 60, recordedSeconds = 48,
        recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), sample = false,
    )

    private val saved = """{"sessionId":"41","savedAt":"2026-10-20T06:22:00Z","memo":null,"songId":"1","songTitle":"아리랑",
        |"startColumnIdx":0,"endColumnIdx":9,"isFullSong":true,"startedAt":"2026-10-20T06:20:00Z","recordingDurationMs":48400,
        |"bpm":60,"bpmUnit":"DOTTED_QUARTER","totalScore":84}""".trimMargin()

    @Test
    fun saveSendsEmptyBodyThenMemoUpdateSendsMemo() = runTest {
        val repo = repository { call -> ok(if (call.body?.contains("memo") == true) saved.replace("\"memo\":null", "\"memo\":\"박자 밀림\"") else saved) }

        val record = repo.saveEvaluation(result, "아리랑")
        repo.updateMemo("41", "박자 밀림")

        assertEquals(HttpMethod.Put, sent[0].method)
        assertEquals("/api/practice-sessions/41/record", sent[0].path)
        assertEquals("{}", sent[0].body)
        assertEquals("""{"memo":"박자 밀림"}""", sent[1].body)
        assertEquals(result.metrics, record.metrics)
        assertEquals(result.errors, record.errors)
        assertEquals("박자 밀림", repo.records.value.single().memo)
    }

    @Test
    fun saveWithoutOkThrowsSoResultShowsSaveFailed() = runTest {
        assertFailsWith<IllegalStateException> { repository { HttpStatusCode.Conflict to "{}" }.saveEvaluation(result, "아리랑") }
        assertFailsWith<IllegalStateException> { repository { null }.saveEvaluation(result, "아리랑") }
    }

    @Test
    fun detailFillsScoresAndMemoAndClearsNew() = runTest {
        val withUnitErrors = result.copy(eventErrors = mapOf(2 to listOf(ScoreErrorKind.Pitch, ScoreErrorKind.Rhythm)))
        val repo = repository(evaluation = withUnitErrors) { call ->
            when (call.path) {
                "/api/users/me/practice-records" -> ok("""{"songs":[{"songId":"1"}],"songId":"1",
                    |"items":[${item("41", "1", "아리랑", "2026-10-20T06:20:00Z", isNew = true)}],"totalPages":1}""".trimMargin())
                "/api/practice-sessions/41" -> ok("""{"sessionId":"41","songId":"1","status":"DONE","recordingAvailable":false,"memo":"다시 듣기","savedAt":"2026-10-20T06:22:00Z"}""")
                else -> null
            }
        }
        repo.refresh()

        val detail = repo.detail("41")!!

        assertEquals(result.metrics, detail.metrics)
        assertEquals(result.errors, detail.errors)
        assertEquals(mapOf(2 to listOf(ScoreErrorKind.Pitch, ScoreErrorKind.Rhythm)), detail.eventErrors)
        assertEquals("다시 듣기", detail.memo)
        assertFalse(detail.recordingAvailable)
        assertFalse(repo.records.value.single().isNew)
    }

    @Test
    fun deletedRecordDetailIsNullAndLeavesTheList() = runTest {
        var listed = true
        val repo = repository { call ->
            if (call.path == "/api/users/me/practice-records" && listed) {
                ok("""{"songs":[{"songId":"1"}],"songId":"1","items":[${item("41", "1", "아리랑", "2026-10-20T06:20:00Z")}],"totalPages":1}""")
            } else {
                HttpStatusCode.NotFound to "{}"
            }
        }
        repo.refresh()
        listed = false
        assertNull(repo.detail("41"))
        assertTrue(repo.records.value.isEmpty())
    }

    @Test
    fun playbackMapsReadyMissingBusyAndNoResponse() = runTest {
        val ready = repository {
            ok("""{"sessionId":"41","playbackUrl":"https://r2.example/x.m4a?sig","urlExpiresAt":"2026-10-20T06:35:00Z","durationMs":48400,"contentType":"audio/mp4"}""")
        }.playback("41")
        assertIs<RecordPlayback.Ready>(ready)
        assertEquals("https://r2.example/x.m4a?sig", ready.url)
        assertEquals(Instant.parse("2026-10-20T06:35:00Z").toEpochMilliseconds(), ready.expiresAtMillis)
        assertEquals(RecordPlayback.Unavailable, repository { HttpStatusCode.NotFound to "{}" }.playback("41"))
        assertEquals(RecordPlayback.Busy, repository { HttpStatusCode.ServiceUnavailable to "{}" }.playback("41"))
        assertEquals(RecordPlayback.Failed, repository { null }.playback("41"))
        assertEquals("/api/practice-sessions/41/recording", sent.first().path)
    }

    @Test
    fun deleteTreatsRepeatedNotFoundAsDeletedAndBusyAsRetry() = runTest {
        assertEquals(RecordDeleteOutcome.Deleted, repository { ok("""{"sessionId":"41","deleted":true}""") }.delete("41"))
        assertEquals(RecordDeleteOutcome.Deleted, repository { HttpStatusCode.NotFound to "{}" }.delete("41"))
        assertEquals(RecordDeleteOutcome.Busy, repository { HttpStatusCode.ServiceUnavailable to "{}" }.delete("41"))
        assertEquals(RecordDeleteOutcome.Failed, repository { null }.delete("41"))
        assertEquals(HttpMethod.Delete, sent.first().method)
        assertEquals("/api/practice-sessions/41/record", sent.first().path)
    }

    private companion object {
        const val RECENT_PATH = "/api/users/me/practice-records/recent"
        const val PROFILE_PATH = "/api/users/me"
        const val REFRESH_PATH = "/api/auth/refresh"
    }
}
