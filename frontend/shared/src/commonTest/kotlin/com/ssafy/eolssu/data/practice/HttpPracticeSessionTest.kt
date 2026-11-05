package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 연습 세션 생성(PRA-01)과 녹음 업로드(PRA-02)의 요청 형식과 응답별 처리. 서버는 MockEngine으로 대신한다. */
class HttpPracticeSessionTest {
    private val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    /** [reply]가 null이면 연결 실패다. */
    private fun client(reply: (path: String) -> Pair<HttpStatusCode, String>?) = createHttpClient(
        MockEngine { request ->
            requests += request
            bodies += request.body.text()
            val (status, body) = reply(request.url.encodedPath) ?: error("연결 실패")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        ServerAddress.Production,
    )

    private suspend fun OutgoingContent.text(): String = when (this) {
        is TextContent -> text
        is OutgoingContent.WriteChannelContent -> {
            val channel = io.ktor.utils.io.ByteChannel()
            writeTo(channel)
            channel.close()
            channel.readRemaining().readString()
        }
        else -> ""
    }

    private val created = """{"httpStatus":"CREATED","isSuccess":true,"code":201,"result":{"sessionId":"41","songId":"2",
        |"status":"RECORDING","bpm":60,"bpmUnit":"DOTTED_QUARTER","startNoteIdx":0,"endNoteIdx":10,
        |"startedAt":"2026-10-28T05:00:00.123456Z"}}""".trimMargin()

    @Test
    fun createSendsKeyAndBodyAndReadsServerStartTime() = runTest {
        val start = HttpPracticeSessionRepository(client { HttpStatusCode.Created to created }, member).create("2", 60, "take-1")

        assertEquals(SessionStart.Created("41", 1_793_163_600_123, "A"), start)
        val request = requests.single()
        assertEquals("/api/practice-sessions", request.url.encodedPath)
        assertEquals("take-1", request.headers["Idempotency-Key"])
        assertEquals("""{"songId":"2","bpm":60}""", bodies.single())
    }

    @Test
    fun sectionSessionSendsNoteRangeAndParent() = runTest {
        HttpPracticeSessionRepository(client { HttpStatusCode.Created to created }, member)
            .create("2", 48, "take-2", SessionSection(startNoteIdx = 12, endNoteIdx = 27, parentSessionId = "30"))

        assertEquals("""{"songId":"2","bpm":48,"startNoteIdx":12,"endNoteIdx":27,"parentSessionId":"30"}""", bodies.single())
    }

    @Test
    fun createSortsFailures() = runTest {
        fun repo(status: HttpStatusCode?) = HttpPracticeSessionRepository(client { status?.let { it to "{}" } }, member)
        assertEquals(SessionStart.Rejected, repo(HttpStatusCode.NotFound).create("2", 60, "k"))
        assertEquals(SessionStart.Rejected, repo(HttpStatusCode.BadRequest).create("2", 60, "k"))
        assertEquals(SessionStart.Rejected, repo(HttpStatusCode.Conflict).create("2", 60, "k"))
        assertEquals(SessionStart.Unreachable, repo(HttpStatusCode.ServiceUnavailable).create("2", 60, "k"))
        assertEquals(SessionStart.Unreachable, repo(null).create("2", 60, "k"))
        // 201인데 본문을 읽지 못하면 같은 키로 다시 만들어 받는다.
        assertEquals(SessionStart.Unreachable, repo(HttpStatusCode.Created).create("2", 60, "k"))
    }

    @Test
    fun createKeepsTheOwnerUntilItsResponseIsBoundToTheSession() = runTest {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine {
            entered.complete(Unit)
            response.await()
            respond(created, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val creating = async { HttpPracticeSessionRepository(client, member).create("2", 60, "take-1") }
        entered.await()
        val changing = async { member.changing { member.set("B") } }
        yield()
        assertFalse(changing.isCompleted)
        assertEquals("A", member.id)

        response.complete(Unit)
        assertEquals(SessionStart.Created("41", 1_793_163_600_123, "A"), creating.await())
        changing.await()
        assertEquals("B", member.id)
        client.close()
    }

    @Test
    fun createWithoutKnownMemberDoesNotCreateAnOwnerlessSession() = runTest {
        member.changing { member.set(null) }
        val repository = HttpPracticeSessionRepository(client { HttpStatusCode.Created to created }, member)
        assertEquals(SessionStart.Unreachable, repository.create("2", 60, "take-1"))
        assertTrue(requests.isEmpty())
    }

    private fun uploader(status: HttpStatusCode?) = HttpRecordingUploader(
        client { status?.let { it to "{}" } },
        fileSize = { 3 },
        openFile = { Buffer().apply { write(byteArrayOf(1, 2, 3)) } },
    )

    @Test
    fun uploadSendsFileAsAudioPart() = runTest {
        assertEquals(UploadOutcome.Uploaded, uploader(HttpStatusCode.Accepted).upload("41", "kept/practice-1.m4a"))

        val request = requests.single()
        assertEquals("/api/practice-sessions/41/recording", request.url.encodedPath)
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        val body = bodies.single()
        assertContains(body, "name=\"audio\"; filename=\"recording.m4a\"")
        assertContains(body, "Content-Type: audio/mp4")
    }

    @Test
    fun uploadSortsResponsesAsSpecified() = runTest {
        val expected = mapOf(
            HttpStatusCode.Accepted to UploadOutcome.Uploaded,
            HttpStatusCode.Conflict to UploadOutcome.Uploaded,
            HttpStatusCode.Gone to UploadOutcome.Expired,
            HttpStatusCode.NotFound to UploadOutcome.Rejected,
            HttpStatusCode.PayloadTooLarge to UploadOutcome.Rejected,
            HttpStatusCode.UnsupportedMediaType to UploadOutcome.Rejected,
            HttpStatusCode.UnprocessableEntity to UploadOutcome.Rejected,
            HttpStatusCode.ServiceUnavailable to UploadOutcome.Unreachable,
            HttpStatusCode.InternalServerError to UploadOutcome.Unreachable,
        )
        expected.forEach { (status, outcome) -> assertEquals(outcome, uploader(status).upload("41", "p"), "$status") }
        assertEquals(UploadOutcome.Unreachable, uploader(null).upload("41", "p"))
    }

    @Test
    fun uploadRetriedAfterTokenRefreshSendsWholeFileAgain() = runTest {
        var refreshed = false
        val uploader = HttpRecordingUploader(
            client { path ->
                when {
                    path == "/api/auth/refresh" -> (HttpStatusCode.OK to "{}").also { refreshed = true }
                    refreshed -> HttpStatusCode.Accepted to "{}"
                    else -> HttpStatusCode.Unauthorized to "{}"
                }
            },
            fileSize = { 3 },
            openFile = { Buffer().apply { write("abc".encodeToByteArray()) } },
        )
        val files = FakeRecordingFiles("rec/practice-1.m4a")
        val uploads = RecordingUploads(uploader, FakeKeyValueStore(), files, { 0L }, member)
        assertEquals(SendResult.Sent, uploads.send(PendingRecording("41", "rec/practice-1.m4a", 0L, memberId = "A")))
        val uploadBodies = bodies.filterIndexed { i, _ -> requests[i].url.encodedPath.endsWith("/recording") }
        assertEquals(2, uploadBodies.size)
        uploadBodies.forEach { assertContains(it, "abc") }
    }
}
