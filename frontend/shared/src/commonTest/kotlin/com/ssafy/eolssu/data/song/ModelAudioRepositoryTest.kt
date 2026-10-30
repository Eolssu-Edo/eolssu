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
import kotlin.test.assertTrue

class ModelAudioRepositoryTest {
    @Test fun keepsLargeIdsVersionOffsetAndTailWithoutRounding() = runTest {
        val expected = audioFixture().copy(songId = "9007199254740993", startOffsetMs = 235, durationMs = 3550)
        val client = client(expected) { assertEquals("/api/songs/9007199254740993/model-audio", it) }
        try { assertEquals(expected, ApiModelAudioRepository(client).get(expected.songId)) } finally { client.close() }
    }

    @Test fun missingPublishedAudioIsDifferentFromServerFailure() = runTest {
        val missing = createHttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }, ServerAddress.Production)
        try { assertNull(ApiModelAudioRepository(missing).get("42")) } finally { missing.close() }
        val failed = createHttpClient(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) }, ServerAddress.Production)
        try { assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(failed).get("42") } } finally { failed.close() }
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
            try { assertFailsWith<CatalogUnavailable>(item.toString()) { ApiModelAudioRepository(client).get("42") } }
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
                assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get(id) }
            }
            assertEquals(0, requests)
            assertFailsWith<CatalogUnavailable> { ApiModelAudioRepository(client).get("42") }
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
