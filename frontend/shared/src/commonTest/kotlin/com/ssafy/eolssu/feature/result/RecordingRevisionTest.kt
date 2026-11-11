package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.FakeRecordingFiles
import com.ssafy.eolssu.data.practice.FakeUploader
import com.ssafy.eolssu.data.practice.HttpPracticeSessionRepository
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.app.navigation.EolssuDestination
import com.ssafy.eolssu.testing.CatalogEvaluations
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingRevisionTest {
    @Test fun retryKeepsRecordedRevisionAndKey() = runTest {
        val store = FakeKeyValueStore()
        val member = SignedInMember(store).apply { set("A") }
        val uploader = FakeUploader()
        val uploads = RecordingUploads(uploader, store, FakeRecordingFiles("rec/take.m4a"), { 0L }, member)
        val bodies = mutableListOf<String>()
        val keys = mutableListOf<String?>()
        val client = createHttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                bodies += (request.body as TextContent).text
                keys += request.headers["Idempotency-Key"]
                respond(if (bodies.size == 1) "{}" else """{"isSuccess":true,"result":{"sessionId":"41","startedAt":"1970-01-01T00:00:00Z"}}""",
                    if (bodies.size == 1) HttpStatusCode.ServiceUnavailable else HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }), ServerAddress.Production)
        try {
            val session = AnalysisSession(backgroundScope, CatalogEvaluations(), { 0L }, {}, uploads,
                HttpPracticeSessionRepository(client, member))
            session.start(SampleSongs.first().copy(id = "42", scoreVersion = 1), 90,
                take = RecordedTake("rec/take.m4a", "recorded-v1", scoreVersion = 1))
            assertEquals(UploadProblem.CanResend, session.state.value.upload)
            assertEquals(0, uploader.calls.size)
            session.resend()
            runCurrent()
            assertEquals(2, bodies.size)
            bodies.forEach { assertContains(it, "\"expectedScoreVersion\":1") }
            assertEquals(listOf<String?>("recorded-v1", "recorded-v1"), keys)
            assertEquals(1, uploader.calls.size)
        } finally { client.close() }
    }

    @Test fun restoredStaleTakeAndUnknownLegacyTakeAreNotSubmitted() = runTest {
        val store = FakeKeyValueStore()
        val member = SignedInMember(store).apply { set("A") }
        val uploader = FakeUploader()
        val uploads = RecordingUploads(uploader, store, FakeRecordingFiles("rec/take.m4a"), { 0L }, member)
        val versions = mutableListOf<Int>()
        var requests = 0
        val client = createHttpClient(MockEngine { request ->
            requests++
            assertContains((request.body as TextContent).text, "\"expectedScoreVersion\":1")
            respond("{}", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val songs = object : SongRepository by SampleSongRepository() {
            override suspend fun getSong(songId: String) = SampleSongs.first().copy(id = songId,
                scoreVersion = 2).also { versions += 2 }
        }
        try {
            val session = AnalysisSession(backgroundScope, CatalogEvaluations(), { 0L }, {}, uploads,
                HttpPracticeSessionRepository(client, member))
            session.loadSong("42", 90, songs, take = RecordedTake("rec/take.m4a", "unknown"))
            assertEquals(UploadProblem.Rejected, session.state.value.upload)
            assertEquals(0, requests)
            session.loadSong("42", 90, songs, take = RecordedTake("rec/take.m4a", "recorded-v1", 1))
            assertEquals(listOf(2), versions)
            assertEquals(2, session.state.value.song?.scoreVersion)
            assertEquals(UploadProblem.Rejected, session.state.value.upload)
            assertEquals(0, requests)
            assertEquals(0, uploader.calls.size)
            assertContains(EolssuDestination.analysis("42", 90, RecordedTake("rec/take.m4a", "recorded-v1", 1)), "scoreVersion=1")
        } finally { client.close() }
    }

    @Test fun staleWholeTakeIsRejectedBeforeUploadInsteadOfBecomingCurrentRevision() = runTest {
        val store = FakeKeyValueStore()
        val member = SignedInMember(store).apply { set("A") }
        val uploader = FakeUploader()
        val uploads = RecordingUploads(uploader, store, FakeRecordingFiles("rec/take.m4a"), { 0L }, member)
        var body = ""
        val client = createHttpClient(MockEngine { request ->
            body = (request.body as TextContent).text
            // 현재 v2인 서버는 녹음 당시 v1을 명시하면 세션을 만들지 않는다.
            val stale = body.contains("\"expectedScoreVersion\":1")
            respond(if (stale) "{}" else """{"isSuccess":true,"result":{"sessionId":"41","startedAt":"1970-01-01T00:00:00Z"}}""",
                if (stale) HttpStatusCode.Conflict else HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val session = AnalysisSession(backgroundScope, CatalogEvaluations(), { 0L }, {}, uploads,
                HttpPracticeSessionRepository(client, member))
            session.start(SampleSongs.first().copy(id = "42", scoreVersion = 1), 90,
                take = RecordedTake("rec/take.m4a", "recorded-v1"))
            assertEquals(0, uploader.calls.size)
            assertEquals(UploadProblem.Rejected, session.state.value.upload)
            assertContains(body, "\"expectedScoreVersion\":1")
            session.resend()
            assertEquals(0, uploader.calls.size)
        } finally { client.close() }
    }
}
