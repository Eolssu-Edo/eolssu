package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.audio.SheetAudioPerformance
import com.ssafy.eolssu.feature.practice.modelMediaPosition
import com.ssafy.eolssu.feature.practice.modelPointAt
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetPracticeRepositoryTest {
    @Test fun playsTheSavedFileForTheCurrentAccountAndScoreVersionWithoutAnAudioRequest() = runTest {
        val original = smallScoreData()
        val score = Json.encodeToJsonElement(original)
        val requests = mutableListOf<String>()
        val file = savedAudio()
        val deviceAudio = PracticeDeviceAudio { accountId, sheetId, scoreVersion ->
            file.takeIf { accountId == "7" && sheetId == "42" && scoreVersion == "1" }
        }
        val member = SignedInMember(FakeKeyValueStore()).apply { set("7") }
        val client = createHttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            val path = request.url.encodedPath
            requests += path
            val result = when (path) {
                "/api/users/me/sheets/42/score" -> """{"sheetId":"42","title":"내 악보","jangdanId":"9","bpm":60,"scoreVersion":"1","reviewStatus":"EVALUABLE","conversionStatus":"DONE","scoreData":$score,"draft":{"title":"미확정","bpm":100,"score":$score,"baseScoreVersion":"1","draftRevision":4}}"""
                "/api/instruments" -> """{"items":[{"instrumentId":"2","code":"DANSO","name":"단소"}]}"""
                "/api/jangdans/9" -> """{"jangdanId":"9","patternCellEighths":1,"name":"세마치","defaultBpm":60,"bpmUnit":"DOTTED_QUARTER","pattern":{"beats":["덩","","","쿵","","","덕","",""],"group":[3,3,3]}}"""
                else -> error("Unexpected request $path")
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repository = practiceRepository(client, deviceAudio, member)
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
            assertEquals("", audio.audioUrl)
            assertEquals(file.localFile, audio.sheetPlayback?.localFile)
            assertTrue(audio.matches(song))
            assertTrue(audio.sheetPlayback?.withJangdan == true)

            assertNull(repository.get(song.copy(scoreVersion = 2)))
            assertNull(repository.get(song.copy(id = "43")))
            member.set("8")
            assertNull(repository.get(song))
            member.set(null)
            assertNull(repository.get(song))
            assertTrue(requests.none { it.endsWith("/audio") }, "기기에 저장한 음원을 재생할 때 서버 음원 상태를 조회하지 않는다")
            assertTrue(requests.none { it.startsWith("/api/songs") })
        } finally {
            client.close()
        }
    }

    @Test fun usesTheSavedPerformanceForRepeatedHighlightsAndSeekWithLegacyFallback() = runTest {
        val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
        val sourceHash = "b".repeat(64)
        val audioHash = "a".repeat(64)
        val spans = """[
            {"occurrenceId":"1","eventId":"c0-j0-r0-u0","cellId":"c0-j0","row":0,"col":0,"startTick":0,"endTick":1,"startMs":0,"endMs":200,"sigimsae":[]},
            {"occurrenceId":"2","eventId":"c0-j0-r0-u1","cellId":"c0-j0","row":0,"col":1,"startTick":1,"endTick":2,"startMs":200,"endMs":400,"sigimsae":["니레"]},
            {"occurrenceId":"3","eventId":"c0-j0-r0-u1","cellId":"c0-j0","row":1,"col":0,"startTick":2,"endTick":4,"startMs":400,"endMs":600,"sigimsae":[]},
            {"occurrenceId":"4","eventId":"c0-j0-r0-u1","cellId":"c0-j1","row":0,"col":0,"startTick":4,"endTick":8,"startMs":600,"endMs":1100,"sigimsae":[]},
            {"occurrenceId":"5","eventId":"c1-j0-r0-u0","cellId":"c1-j0","row":0,"col":0,"startTick":8,"endTick":12,"startMs":1100,"endMs":2000,"sigimsae":[]},
            {"occurrenceId":"6","eventId":"c0-j0-r0-u0","cellId":"c0-j0","row":0,"col":0,"startTick":0,"endTick":1,"startMs":2000,"endMs":2200,"sigimsae":[]}
        ]"""
        val timeline = Json.parseToJsonElement("""{"schema":"common-score-performance/v1","policy":"educational-preview/v1",
            "sourceSha256":"$sourceHash","audioSha256":"$audioHash","scoreVersion":1,"sourceId":"unit-test-score",
            "instrument":"DANSO","bpmUnit":"DOTTED_QUARTER","bpm":72,"durationMs":2200,"segments":$spans}""").jsonObject
        val local = savedAudio()
        var file = local.copy(performance = SheetAudioPerformance(sourceHash, timeline))
        val client = createHttpClient(MockEngine { error("Practice must use the saved audio and performance") }, ServerAddress.Production)
        try {
            val repository = practiceRepository(client, PracticeDeviceAudio { _, _, _ -> file })
            val audio = assertNotNull(repository.get(song))
            assertEquals(2200.0, assertNotNull(audio.performance).durationMs)
            assertEquals(0, song.modelPointAt(audio, 2100.0).position)
            assertEquals(1100L, song.modelMediaPosition(audio, 8.0))

            file = local.copy(performance = SheetAudioPerformance("c".repeat(64), timeline))
            assertNull(assertNotNull(repository.get(song)).performance)
            file = local
            val legacy = assertNotNull(repository.get(song))
            assertNull(legacy.performance)
            assertEquals(2, song.modelPointAt(legacy, 2100.0).position)
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
            val repository = practiceRepository(client)
            assertFailsWith<SheetPracticeNeedsReview> { repository.load("42") }
        } finally {
            client.close()
        }
    }

    @Test fun discardsADeviceLookupWhenTheSameAccountSignsInAgain() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("7") }
        val lookupStarted = CompletableDeferred<Unit>()
        val finishLookup = CompletableDeferred<Unit>()
        val deviceAudio = PracticeDeviceAudio { _, _, _ ->
            lookupStarted.complete(Unit)
            finishLookup.await()
            savedAudio()
        }
        val client = createHttpClient(MockEngine { error("Device lookup must not make a request") }, ServerAddress.Production)
        try {
            val repository = practiceRepository(client, deviceAudio, member)
            val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
            val pending = async { repository.get(song) }
            lookupStarted.await()
            member.set("7")
            finishLookup.complete(Unit)
            assertFailsWith<CancellationException> { pending.await() }
        } finally {
            client.close()
        }
    }

    private fun practiceRepository(
        client: HttpClient,
        deviceAudio: SheetAudioDeviceStore = PracticeDeviceAudio { _, _, _ -> null },
        member: SignedInMember = SignedInMember(FakeKeyValueStore()).apply { set("7") },
    ) = SheetPracticeRepository(ApiSheetDocumentRepository(client), deviceAudio, ApiReferenceCatalogRepository(client),
        InstrumentCatalog(client), member, ApiSheetAudioRepository(client), { 0L })

    private fun savedAudio() = DeviceSheetAudio(
        localFile = LocalAudioFile("/app/files/sheet-audio/42.m4a"),
        instrumentId = "2",
        bpm = 72,
        withJangdan = true,
        sha256 = "a".repeat(64),
        durationMs = 3200,
    )

    private class PracticeDeviceAudio(
        private val lookup: suspend (String, String, String) -> DeviceSheetAudio?,
    ) : SheetAudioDeviceStore {
        override val available = true
        override suspend fun find(accountId: String, sheetId: String, scoreVersion: String) =
            lookup(accountId, sheetId, scoreVersion)
        override suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload) =
            error("Practice does not download audio")
        override suspend fun delete(accountId: String, sheetId: String) = error("Practice does not delete audio")
    }
}
