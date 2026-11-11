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
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 연습 기록 API(REC-01~04, `docs/specs/practice-records.md`)를 앱 기록으로 바꾸는지. 서버는 MockEngine으로 대신한다. */
class HttpPracticeRecordRepositoryTest {
    private class Sent(val method: HttpMethod, val path: String, val query: String, val body: String?)

    private val sent = mutableListOf<Sent>()

    private fun repository(
        evaluation: EvaluationResult? = null,
        member: SignedInMember? = null,
        reply: (Sent) -> Pair<HttpStatusCode, String>?,
    ) = HttpPracticeRecordRepository(
        createHttpClient(
            MockEngine { request ->
                val call = Sent(request.method, request.url.encodedPath, request.url.encodedQuery, (request.body as? TextContent)?.text)
                sent += call
                val (status, body) = reply(call) ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
        object : EvaluationRepository {
            override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int) = error("쓰지 않음")
            override suspend fun analysisStatus(analysisId: String) = error("쓰지 않음")
            override suspend fun retryAnalysis(analysisId: String) = error("쓰지 않음")
            override suspend fun result(evaluationId: String) = evaluation
        },
        member,
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
}
