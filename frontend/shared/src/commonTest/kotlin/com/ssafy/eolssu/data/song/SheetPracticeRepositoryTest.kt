package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetPracticeRepositoryTest {
    @Test fun loadsCommittedPersonalScoreAndOnlyAcceptsAudioFromItsVersion() = runTest {
        val original = smallScoreData()
        val score = Json.encodeToJsonElement(original)
        val requests = mutableListOf<String>()
        val hash = "a".repeat(64)
        var audioVersion = "1"
        var currentVersion = "1"
        val client = createHttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            val path = request.url.encodedPath
            requests += path
            val result = when (path) {
                "/api/users/me/sheets/42/score" -> """{"sheetId":"42","title":"내 악보","jangdanId":"9","bpm":60,"scoreVersion":"1","reviewStatus":"EVALUABLE","conversionStatus":"DONE","scoreData":$score,"draft":{"title":"미확정","bpm":100,"score":$score,"baseScoreVersion":"1","draftRevision":4}}"""
                "/api/instruments" -> """{"items":[{"instrumentId":"2","code":"DANSO","name":"단소"}]}"""
                "/api/jangdans/9" -> """{"jangdanId":"9","name":"세마치","defaultBpm":60,"bpmUnit":"DOTTED_QUARTER","pattern":{"beats":["덩","","","쿵","","","덕","",""],"group":[3,3,3]}}"""
                "/api/users/me/sheets/42/audio" -> """{"sheetId":"42","status":"DONE","scoreVersion":"$audioVersion","currentScoreVersion":"$currentVersion","outdated":false,"instrumentId":"2","withJangdan":true,"bpm":72,"audio":{"url":"https://audio.example/score.m4a","urlExpiresAt":"2026-11-10T03:00:00Z","contentType":"audio/mp4","byteSize":123,"fileName":"score.m4a","sha256":"$hash","durationMs":3200}}"""
                else -> error("Unexpected request $path")
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repository = SheetPracticeRepository(ApiSheetDocumentRepository(client), ApiSheetAudioRepository(client),
                ApiReferenceCatalogRepository(client), InstrumentCatalog(client), now = { 0L })
            val song = assertNotNull(repository.load("42"))
            assertEquals("42", song.id)
            assertEquals("내 악보", song.title)
            assertEquals(60, song.baseBpm)
            assertEquals("2", song.instrumentId)
            assertEquals(12L, song.jangdanPlayback?.cycleTicks)
            assertEquals(original.events.map { it.id }, song.timeline.map { it.id })
            assertFalse(song.evaluable)

            val audio = assertNotNull(repository.get(song))
            assertEquals(72, audio.bpm)
            assertTrue(audio.matches(song))
            assertTrue(audio.sheetPlayback?.withJangdan == true)

            currentVersion = "2"
            assertNull(repository.get(song))
            audioVersion = "2"
            assertNull(repository.get(song))
            assertTrue(requests.none { it.startsWith("/api/songs") })
        } finally {
            client.close()
        }
    }

    @Test fun directsUnreviewedScoreBackToEditingWithoutLoadingCatalog() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/users/me/sheets/42/score", request.url.encodedPath)
            respond("""{"isSuccess":true,"code":200,"result":{"sheetId":"42","title":"검수 필요","bpm":60,"scoreVersion":"1","reviewStatus":"NEEDS_REVIEW","conversionStatus":"DONE"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repository = SheetPracticeRepository(ApiSheetDocumentRepository(client), ApiSheetAudioRepository(client),
                ApiReferenceCatalogRepository(client), InstrumentCatalog(client), now = { 0L })
            assertFailsWith<SheetPracticeNeedsReview> { repository.load("42") }
        } finally {
            client.close()
        }
    }
}
