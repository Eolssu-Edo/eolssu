package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** SHT-12 생성 요청과 SHT-13 상태 조회의 요청 형식·응답별 처리. 서버는 MockEngine으로 대신한다. */
class ApiSheetAudioRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    /** [reply]가 null이면 연결 실패다. */
    private fun client(reply: (HttpRequestData) -> Pair<HttpStatusCode, String>?) = createHttpClient(
        MockEngine { request ->
            requests += request
            bodies += (request.body as? TextContent)?.text.orEmpty()
            val (status, body) = reply(request) ?: error("연결 실패")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        ServerAddress.Production,
    )

    private fun failure(code: String) = """{"httpStatus":"X","isSuccess":false,"message":"","code":0,"result":{"errorCode":"$code"}}"""

    private fun audioJson(
        status: String,
        outdated: Boolean = false,
        files: Boolean = status == "DONE",
        failureCode: String? = null,
        sheetId: String = "12",
        audioUrl: String = "https://r2.example/sheet-audio/12.m4a",
        httpCode: Int = 200,
    ): String {
        val audio = if (files) {
            """{"url":"$audioUrl","urlExpiresAt":"2026-11-04T03:15:00Z","contentType":"audio/mp4","byteSize":291840,""" +
                """"sha256":"${"9f".repeat(32)}","durationMs":18550,"fileName":"아리랑 (내 편곡).m4a"}"""
        } else "null"
        val midi = if (files) {
            """{"url":"https://r2.example/sheet-audio/12.mid","urlExpiresAt":"2026-11-04T03:15:00Z","contentType":"audio/midi",""" +
                """"byteSize":2048,"fileName":"아리랑 (내 편곡).mid"}"""
        } else "null"
        val poll = if (status == "QUEUED" || status == "RUNNING") "3000" else "null"
        val failure = failureCode?.let { "\"$it\"" } ?: "null"
        return """{"httpStatus":"OK","isSuccess":true,"message":"","code":$httpCode,"result":{"sheetId":"$sheetId","status":"$status",""" +
            """"scoreVersion":"2","currentScoreVersion":"${if (outdated) 3 else 2}","outdated":$outdated,"instrumentId":"1","withJangdan":true,"bpm":60,""" +
            """"failureCode":$failure,"pollAfterMs":$poll,"audio":$audio,"midi":$midi}}"""
    }

    private val expiresAt = Instant.parse("2026-11-04T03:15:00Z").toEpochMilliseconds()

    @Test fun requestSendsSettingsAsJsonAndAcceptsQueuedAudio() = runTest {
        val client = client { HttpStatusCode.Accepted to audioJson("QUEUED", httpCode = 202) }
        try {
            val result = ApiSheetAudioRepository(client).request("12", SheetAudioSettings(instrumentId = "1", withJangdan = true, bpm = 60))
            val accepted = assertIs<SheetAudioRequest.Accepted>(result)
            assertEquals(SheetAudioStatus.Queued, accepted.audio.status)
            assertEquals(3000L, accepted.audio.pollAfterMs)
            assertNull(accepted.audio.audio)
            assertTrue(accepted.audio.status.inProgress)
            assertEquals(HttpMethod.Post, requests.single().method)
            assertEquals("/api/users/me/sheets/12/audio", requests.single().url.encodedPath)
            assertEquals("""{"instrumentId":"1","withJangdan":true,"bpm":60}""", bodies.single())
        } finally { client.close() }
    }

    @Test fun absentSettingsAreOmittedSoTheServerDefaultsApply() = runTest {
        val client = client { HttpStatusCode.Accepted to audioJson("QUEUED", httpCode = 202) }
        try {
            ApiSheetAudioRepository(client).request("12")
            assertEquals("""{"withJangdan":false}""", bodies.single())
        } finally { client.close() }
    }

    @Test fun existingAudioWithTheSameSettingsComesBackDoneOn200() = runTest {
        val client = client { HttpStatusCode.OK to audioJson("DONE") }
        try {
            val accepted = assertIs<SheetAudioRequest.Accepted>(ApiSheetAudioRepository(client).request("12"))
            assertEquals(SheetAudioStatus.Done, accepted.audio.status)
            val audio = accepted.audio.audio ?: error("audio")
            assertEquals("https://r2.example/sheet-audio/12.m4a", audio.url)
            assertEquals(expiresAt, audio.urlExpiresAt)
            assertEquals(18550L, audio.durationMs)
            assertEquals("아리랑 (내 편곡).m4a", audio.fileName)
            assertEquals("audio/mp4", audio.contentType)
            assertNull(accepted.audio.pollAfterMs)
            assertNull(accepted.audio.failureCode)
        } finally { client.close() }
    }

    @Test fun requestErrorsMapToDistinctOutcomes() = runTest {
        val cases = listOf(
            (HttpStatusCode.BadRequest to failure("EXPORT_TOO_LONG")) to SheetAudioRequest.TooLong,
            (HttpStatusCode.BadRequest to failure("INVALID_REQUEST")) to SheetAudioRequest.InvalidRequest,
            (HttpStatusCode.NotFound to failure("RESOURCE_NOT_FOUND")) to SheetAudioRequest.NotFound,
            (HttpStatusCode.Conflict to failure("STATE_CONFLICT")) to SheetAudioRequest.NotReady,
            (HttpStatusCode.Conflict to failure("AUDIO_IN_PROGRESS")) to SheetAudioRequest.InProgress,
            (HttpStatusCode.TooManyRequests to failure("TOO_MANY_REQUESTS")) to SheetAudioRequest.TooManyRequests,
            (HttpStatusCode.ServiceUnavailable to failure("AUDIO_UNAVAILABLE")) to SheetAudioRequest.Unavailable,
            (HttpStatusCode.OK to "<html>proxy</html>") to SheetAudioRequest.Unavailable,
            null to SheetAudioRequest.Unavailable,
        )
        for ((reply, expected) in cases) {
            val client = client { reply }
            try { assertEquals(expected, ApiSheetAudioRepository(client).request("12"), "응답 $reply") }
            finally { client.close() }
        }
    }

    @Test fun statusKeepsPreviousFilesWhileOutdatedOrFailed() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            HttpStatusCode.OK to audioJson("FAILED", outdated = true, files = true, failureCode = "TOO_LARGE")
        }
        try {
            val found = assertIs<SheetAudioLookup.Found>(ApiSheetAudioRepository(client).status("12"))
            assertEquals(SheetAudioStatus.Failed, found.audio.status)
            assertEquals(SheetAudioFailure.TooLarge, found.audio.failureCode)
            assertTrue(found.audio.outdated)
            assertEquals("3", found.audio.currentScoreVersion)
            assertEquals(expiresAt, assertNotNull(found.audio.audio).urlExpiresAt)
            assertEquals("/api/users/me/sheets/12/audio", requests.single().url.encodedPath)
        } finally { client.close() }
    }

    @Test fun missingAudioIsNoneAndOtherStatusFailuresAreUnavailable() = runTest {
        val cases = listOf(
            (HttpStatusCode.NotFound to failure("RESOURCE_NOT_FOUND")) to SheetAudioLookup.None,
            (HttpStatusCode.ServiceUnavailable to failure("AUDIO_UNAVAILABLE")) to SheetAudioLookup.Unavailable,
            null to SheetAudioLookup.Unavailable,
        )
        for ((reply, expected) in cases) {
            val client = client { reply }
            try { assertEquals(expected, ApiSheetAudioRepository(client).status("12"), "응답 $reply") }
            finally { client.close() }
        }
    }

    @Test fun expiredDownloadHasNoFileAndCanBeRequestedAgain() = runTest {
        val client = client { HttpStatusCode.OK to audioJson("FAILED", files = false, failureCode = "AUDIO_EXPIRED") }
        try {
            val result = assertIs<SheetAudioLookup.Found>(ApiSheetAudioRepository(client).status("12")).audio
            assertEquals(SheetAudioFailure.Expired, result.failureCode)
            assertNull(result.audio)
        } finally { client.close() }
    }

    @Test fun malformedAudioIsRejectedInsteadOfPlayed() = runTest {
        val bad = listOf(
            audioJson("DONE", audioUrl = "http://r2.example/plain.m4a"),
            audioJson("DONE", files = false),
            audioJson("EXPORTING"),
            audioJson("DONE", sheetId = "13"),
            audioJson("DONE").replace("\"urlExpiresAt\":\"2026-11-04T03:15:00Z\"", "\"urlExpiresAt\":\"tomorrow\""),
        )
        for (body in bad) {
            val client = client { HttpStatusCode.OK to body }
            try {
                assertEquals(SheetAudioLookup.Unavailable, ApiSheetAudioRepository(client).status("12"), body)
                assertEquals(SheetAudioRequest.Unavailable, ApiSheetAudioRepository(client).request("12"), body)
            } finally { client.close() }
        }
    }

    @Test fun debugDownloadMustUseTheConfiguredLoopbackOrigin() = runTest {
        val server = ServerAddress("http://127.0.0.1:18084")
        val cases = listOf(
            "http://127.0.0.1:18084/file.m4a" to true,
            "http://127.0.0.1:18085/file.m4a" to false,
            "http://127.0.0.1:18084@wrong.example/file.m4a" to false,
        )
        for ((url, allowed) in cases) {
            val client = client { HttpStatusCode.OK to audioJson("DONE", audioUrl = url) }
            try {
                val response = ApiSheetAudioRepository(client, server = server).status("12")
                assertEquals(allowed, response is SheetAudioLookup.Found)
            } finally { client.close() }
        }
    }

    @Test fun sampleSheetIdsNeverReachTheServer() = runTest {
        val client = client { HttpStatusCode.OK to audioJson("DONE") }
        try {
            assertEquals(SheetAudioRequest.NotFound, ApiSheetAudioRepository(client).request("sample-arirang"))
            assertEquals(SheetAudioLookup.None, ApiSheetAudioRepository(client).status("sample-arirang"))
            assertTrue(requests.isEmpty())
        } finally { client.close() }
    }

    @Test fun expiryMarginTreatsSoonToExpireUrlsAsExpired() {
        val file = SheetAudioFile("https://r2.example/a.m4a", urlExpiresAt = 1_000_000, contentType = "audio/mp4", byteSize = 1, fileName = "a.m4a")
        assertTrue(file.expiresWithin(now = 990_000, marginMs = 30_000))
        assertTrue(file.expiresWithin(now = 1_000_000, marginMs = 0))
        assertFalse(file.expiresWithin(now = 900_000, marginMs = 30_000))
    }
}
