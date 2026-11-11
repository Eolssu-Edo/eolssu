package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelAudioRepositoryTest {
    @Test fun resolvesOnlyTheMatchingServerPlaybackPathAndKeepsLegacyTiming() = runTest {
        val expected = audioFixture().copy(audioUrl = "/api/songs/42/model-audio/play")
        val client = client(expected)
        try {
            val audio = assertNotNull(ApiModelAudioRepository(client).get(audioSong()))
            assertEquals(ServerAddress.Production.baseUrl.trimEnd('/') + expected.audioUrl, audio.audioUrl)
            assertNull(audio.performance)
        } finally { client.close() }
    }

    @Test fun presentButUnavailableOrMismatchedPerformanceNeverFallsBackToWrittenTiming() = runTest {
        val metadata = previewAudio().copy(performanceUrl = "/api/songs/42/model-audio/performance", sourceCommonSha256 = SourceHash)
        for ((body, status) in listOf("{}" to HttpStatusCode.NotFound, "{}" to HttpStatusCode.OK,
            previewJson().replace(SourceHash, "c".repeat(64)) to HttpStatusCode.OK)) {
            val client = createHttpClient(MockEngine {
                if (it.url.encodedPath.endsWith("/performance")) respond(body, status)
                else respond("""{"isSuccess":true,"result":${Json.encodeToString(metadata)}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try { assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get(previewSong()) } }
            finally { client.close() }
        }
    }

    @Test fun rejectsIncompleteOrForeignPerformanceReferencesBeforeFetching() = runTest {
        val audio = audioFixture()
        for (invalid in listOf(audio.copy(performanceUrl = "/api/songs/42/model-audio/performance"),
            audio.copy(sourceCommonSha256 = SourceHash),
            audio.copy(performanceUrl = "https://foreign.example/performance", sourceCommonSha256 = SourceHash),
            audio.copy(performanceUrl = "/api/songs/43/model-audio/performance", sourceCommonSha256 = SourceHash),
            audio.copy(audioUrl = "/api/songs/43/model-audio/play"))) {
            var calls = 0
            val client = client(invalid) { calls++ }
            try { assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get(audioSong()) }; assertEquals(1, calls) }
            finally { client.close() }
        }
    }

    @Test fun fetchesPublishedPerformanceFromSameServerAndBindsItToTheScore() = runTest {
        val requests = mutableListOf<String>()
        val data = Json.encodeToString(previewAudio()).dropLast(1) +
            ""","performanceUrl":"/api/songs/42/model-audio/performance","sourceCommonSha256":"$SourceHash"}"""
        val client = createHttpClient(MockEngine {
            requests += it.url.encodedPath
            respond(if (it.url.encodedPath.endsWith("/performance")) previewJson() else """{"isSuccess":true,"result":$data}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val audio = assertNotNull(ApiModelAudioRepository(client).get(previewSong()))
            assertNotNull(audio.performance)
            assertEquals(6500.0, audio.performance.durationMs)
            assertEquals(listOf("/api/songs/42/model-audio", "/api/songs/42/model-audio/performance"), requests)
        } finally { client.close() }
    }

    @Test fun reusesVerifiedPerformanceAcrossVisitsAndChecksPublishedAudioEachTime() = runTest {
        val requests = mutableListOf<String>()
        val original = publishedPreview()
        var metadata = original
        var timeline = previewJson()
        var metadataStatus = HttpStatusCode.OK
        val client = createHttpClient(MockEngine {
            requests += it.url.encodedPath
            if (it.url.encodedPath.endsWith("/performance")) respond(timeline)
            else respond(
                """{"isSuccess":true,"result":${Json.encodeToString(metadata)}}""",
                metadataStatus,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }, ServerAddress.Production)
        try {
            val repository = ApiModelAudioRepository(client)
            val first = assertNotNull(repository.get(previewSong()))
            val next = assertNotNull(repository.get(previewSong()))
            assertSame(first, next)
            assertEquals(listOf(MetadataPath, TimelinePath, MetadataPath), requests)
            assertEquals(ServerAddress.Production.baseUrl.trimEnd('/') + PlayPath, next.audioUrl)

            val newAudioHash = "d".repeat(64)
            metadata = original.copy(sha256 = newAudioHash)
            timeline = previewJson().replace(original.sha256, newAudioHash)
            val changedAudio = assertNotNull(repository.get(previewSong()))
            assertEquals(newAudioHash, changedAudio.performance?.audioSha256)
            assertEquals(listOf(MetadataPath, TimelinePath), requests.takeLast(2))

            metadata = metadata.copy(scoreVersion = 2)
            timeline = timeline.replace(""""scoreVersion":1""", """"scoreVersion":2""")
            val oldScore = previewSong()
            val newScore = oldScore.copy(scoreVersion = 2, score = oldScore.score.copy(
                source = oldScore.score.source?.copy(scoreVersion = 2),
            ))
            assertFailsWith<CatalogUnavailable> { repository.get(oldScore) }
            val changedScore = assertNotNull(repository.get(newScore))
            assertEquals(2, changedScore.scoreVersion)
            assertEquals(listOf(MetadataPath, MetadataPath, TimelinePath), requests.takeLast(3))

            metadataStatus = HttpStatusCode.NotFound
            assertNull(repository.get(newScore))
            metadataStatus = HttpStatusCode.ServiceUnavailable
            assertFailsWith<CatalogUnavailable> { repository.get(newScore) }
            assertEquals(listOf(MetadataPath, MetadataPath), requests.takeLast(2))
        } finally { client.close() }
    }

    @Test fun timelineFailureIsRetriedAndLegacyDirectUrlsAreReadFresh() = runTest {
        var metadata = publishedPreview()
        var timelineStatus = HttpStatusCode.ServiceUnavailable
        var timelineCalls = 0
        val client = createHttpClient(MockEngine {
            if (it.url.encodedPath == TimelinePath) {
                timelineCalls++
                respond(previewJson(), timelineStatus)
            } else {
                respond("""{"isSuccess":true,"result":${Json.encodeToString(metadata)}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }, ServerAddress.Production)
        try {
            val repository = ApiModelAudioRepository(client)
            assertFailsWith<CatalogUnavailable> { repository.get(previewSong()) }
            timelineStatus = HttpStatusCode.OK
            assertNotNull(repository.get(previewSong())?.performance)
            assertEquals(2, timelineCalls)

            metadata = metadata.copy(audioUrl = "https://example.com/model.m4a?signature=fixture-first")
            assertEquals(metadata.audioUrl, repository.get(previewSong())?.audioUrl)
            metadata = metadata.copy(audioUrl = "https://example.com/model.m4a?signature=fixture-second")
            assertEquals(metadata.audioUrl, repository.get(previewSong())?.audioUrl)
            assertNotNull(repository.get(previewSong())?.performance)
            assertEquals(5, timelineCalls)
        } finally { client.close() }
    }

    @Test fun keepsLargeIdsVersionOffsetAndTailWithoutRounding() = runTest {
        val expected = audioFixture().copy(songId = "9007199254740993", startOffsetMs = 235, durationMs = 3550)
        val client = client(expected) { assertEquals("/api/songs/9007199254740993/model-audio", it) }
        try { assertEquals(expected, ApiModelAudioRepository(client).get(audioSong(expected.songId))) } finally { client.close() }
    }

    @Test fun missingPublishedAudioIsDifferentFromServerFailure() = runTest {
        val missing = createHttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }, ServerAddress.Production)
        try { assertNull(ApiModelAudioRepository(missing).get(audioSong())) } finally { missing.close() }
        val failed = createHttpClient(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) }, ServerAddress.Production)
        try { assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(failed).get(audioSong()) } } finally { failed.close() }
    }

    @Test fun rejectsUntrustedOrInvalidMetadata() = runTest {
        val audio = audioFixture()
        val invalid = listOf(audio.copy(songId = "43"), audio.copy(scoreVersion = 0), audio.copy(bpm = 29),
            audio.copy(bpmUnit = "BEAT"), audio.copy(durationMs = 0), audio.copy(startOffsetMs = -1),
            audio.copy(startOffsetMs = audio.durationMs), audio.copy(contentType = "text/html"), audio.copy(sha256 = "A".repeat(64))) +
            listOf("http://example.com/a.m4a", "file:///a.m4a", "https://user:password@example.com/a", "https://example.com/a#fragment",
                "//example.com/a", "https://example.com/a\n").map { audio.copy(audioUrl = it) }
        for (item in invalid) {
            val client = client(item)
            try { assertFailsWith<CatalogUnavailable>(item.toString()) { ApiModelAudioRepository(client).get(audioSong()) } }
            finally { client.close() }
        }
    }

    @Test fun malformedEnvelopeAndNoncanonicalIdsNeverBecomePlayback() = runTest {
        var requests = 0
        val client = createHttpClient(MockEngine {
            requests++
            respond("""{"isSuccess":false,"result":${Json.encodeToString(audioFixture())}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            for (id in listOf("../1", "0", "01", "+1", "9223372036854775808")) {
                assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get(audioSong(id)) }
            }
            assertEquals(0, requests)
            assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get(audioSong()) }
        } finally { client.close() }
    }

    @Test fun requiresSameSongVersionTempoAndBeatDefinition() {
        val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
        val audio = audioFixture()
        assertTrue(audio.matches(song))
        assertFalse(audio.copy(scoreVersion = 2).matches(song))
        assertFalse(audio.copy(bpm = 120).matches(song))
        assertFalse(audio.copy(bpmUnit = "QUARTER").matches(song))
        assertFalse(audio.matches(song.copy(id = "43")))
    }

    private fun client(audio: ModelAudio, request: (String) -> Unit = {}) = createHttpClient(MockEngine {
        request(it.url.encodedPath)
        respond("""{"isSuccess":true,"result":${Json.encodeToString(audio)}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }, ServerAddress.Production)
}

internal fun audioFixture() = ModelAudio("42", 1, "https://example.com/model.m4a", 60, "DOTTED_QUARTER", 3550, 0, "audio/mp4", "a".repeat(64))

private fun audioSong(id: String = "42") = smallScoreSong().copy(id = id, scoreVersion = 1)

private const val MetadataPath = "/api/songs/42/model-audio"
private const val TimelinePath = "$MetadataPath/performance"
private const val PlayPath = "$MetadataPath/play"

private fun publishedPreview() = previewAudio().copy(
    audioUrl = PlayPath,
    performanceUrl = TimelinePath,
    sourceCommonSha256 = SourceHash,
)
