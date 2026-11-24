package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.audio.SheetAudioPerformance
import com.ssafy.eolssu.core.audio.UnavailableSheetAudioDeviceStore
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetPracticeRepositoryTest {
    @Test fun exposesTheConfirmedModelDurationWithoutChangingWrittenTimingOrReadingTheDraft() = runTest {
        val original = smallScoreData()
        val score = Json.encodeToJsonElement(original)
        val confirmed = """{"performancePlan":{"tempos":[{"gak":1,"cell":2,"bpm":120}]}}"""
        val draft = """{"performancePlan":{"tempos":[{"gak":1,"cell":1,"bpm":30}]}}"""
        val client = createHttpClient(MockEngine { request ->
            val result = when (request.url.encodedPath) {
                "/api/users/me/sheets/42/score" -> """{"sheetId":"42","title":"구간 빠르기","bpm":60,"scoreVersion":"1","reviewStatus":"EVALUABLE","scoreData":$score,"expressions":$confirmed,"draft":{"title":"미확정","bpm":90,"score":$score,"baseScoreVersion":"1","draftRevision":1,"expressions":$draft}}"""
                "/api/instruments" -> """{"items":[{"instrumentId":"2","code":"DANSO","name":"단소"}]}"""
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val song = assertNotNull(practiceRepository(client).load("42"))
            assertEquals(60, song.baseBpm)
            assertEquals(2000.0, song.estimatedModelDurationMs)
            assertEquals(3000.0, song.durationMs())
            assertEquals(3000.0, song.timeline.sumOf { song.eventDurationMs(it) })
            assertEquals(original.events.map { it.startTick to it.durationTick }, song.timeline.map { it.startTick to it.durationTick })
        } finally { client.close() }
    }

    @Test fun readsConfirmedBasicAccompanimentWithoutInventingACatalogLink() = runTest {
        val original = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "", bpmUnit = "QUARTER")) }
        val score = Json.encodeToJsonElement(original)
        val requests = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            requests += request.url.encodedPath
            val result = when (request.url.encodedPath) {
                "/api/users/me/sheets/42/score" -> """{"sheetId":"42","title":"5박 악보","bpm":60,"scoreVersion":"1","reviewStatus":"EVALUABLE","scoreData":$score,"authoring":{"schema":"sheet-authoring/v1","accompaniment":{"schema":"basic-accompaniment/v1","groups":[2,3],"pulseUnit":"EIGHTH","startTick":4}},"draft":{"title":"미확정","bpm":60,"score":$score,"baseScoreVersion":"1","draftRevision":1,"authoring":{"accompaniment":null}}}"""
                "/api/instruments" -> """{"items":[{"instrumentId":"2","code":"DANSO","name":"단소"}]}"""
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val song = assertNotNull(practiceRepository(client).load("42"))
            val setting = assertNotNull(song.basicAccompaniment)
            assertEquals(listOf(2, 3), setting.groups)
            assertEquals(4L, setting.startTick)
            assertNull(song.jangdanPlayback)
            assertEquals(original.events.map { it.id }, song.timeline.map { it.id })
            assertTrue(requests.none { it.startsWith("/api/jangdans") })
        } finally { client.close() }
    }
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
            assertNull(song.estimatedModelDurationMs)
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

    @Test fun webStreamsTheCurrentVersionsSignedLinkWithoutTheDeviceStore() = runTest {
        // S15P31E102-1261: 웹은 기기 저장소가 없어도 SHT-13의 완료된 최신 M4A 링크를 바로 튼다.
        val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
        var response = HttpStatusCode.OK to sheetAudioJson()
        var now = StreamExpiresAt - 60_000
        val requests = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            requests += request.url.encodedPath
            assertEquals("/api/users/me/sheets/42/audio", request.url.encodedPath)
            respond(response.second, response.first, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repository = practiceRepository(client, UnavailableSheetAudioDeviceStore, streamsAudio = true, now = { now })
            assertTrue(repository.personalAudioPlayable)
            val audio = assertNotNull(repository.get(song))
            assertEquals(StreamUrl, audio.audioUrl)
            assertNull(audio.localFile)
            assertNull(assertNotNull(audio.sheetPlayback).localFile)
            assertTrue(audio.streamsSheetAudio)
            assertTrue(audio.sheetPlayback.withJangdan)
            assertEquals(66, audio.bpm, "음원을 만든 빠르기로 속도를 맞춘다")
            assertEquals("9f".repeat(32), audio.sha256)
            assertEquals(3300L, audio.durationMs)
            assertEquals("audio/mp4", audio.contentType)
            assertTrue(audio.matches(song))
            assertEquals(1, requests.size)

            response = HttpStatusCode.OK to sheetAudioJson(outdated = true, currentScoreVersion = "2")
            assertNull(repository.get(song), "보관 중인 이전 판본 파일은 틀지 않는다")
            response = HttpStatusCode.OK to sheetAudioJson(scoreVersion = "2", currentScoreVersion = "2")
            assertNull(repository.get(song), "다른 판본의 파일은 틀지 않는다")
            response = HttpStatusCode.OK to sheetAudioJson(status = "RUNNING")
            assertNull(repository.get(song), "만드는 중에는 틀 링크가 없다")
            response = HttpStatusCode.OK to sheetAudioJson(status = "FAILED", failureCode = "AUDIO_EXPIRED")
            assertNull(repository.get(song), "완료 10분 뒤 서버가 지운 파일은 없는 것으로 본다")
            response = HttpStatusCode.OK to sheetAudioJson(contentType = "audio/midi")
            assertNull(repository.get(song))
            response = HttpStatusCode.NotFound to ""
            assertNull(repository.get(song), "만든 적 없는 음원")

            response = HttpStatusCode.OK to sheetAudioJson()
            now = StreamExpiresAt - STREAM_START_MARGIN_MS
            assertNull(repository.get(song), "곧 지워질 링크로는 재생을 시작하지 않는다")
            now = StreamExpiresAt - STREAM_START_MARGIN_MS - 1
            assertNotNull(repository.get(song))

            response = HttpStatusCode.ServiceUnavailable to ""
            assertFailsWith<CatalogUnavailable> { repository.get(song) }
        } finally {
            client.close()
        }
    }

    @Test fun platformsWithoutTheDeviceStoreOrStreamingNeitherPlayNorAskTheServer() = runTest {
        val client = createHttpClient(MockEngine { error("기기 저장도 스트리밍도 없으면 음원 상태를 묻지 않는다") }, ServerAddress.Production)
        try {
            val repository = practiceRepository(client, UnavailableSheetAudioDeviceStore)
            val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
            assertFalse(repository.personalAudioPlayable)
            assertNull(repository.get(song))
            assertNull(repository.generationStatus(song))
            assertEquals(SheetPracticeDownload.Failed, repository.download(song))
        } finally {
            client.close()
        }
    }

    private fun sheetAudioJson(
        status: String = "DONE",
        scoreVersion: String = "1",
        currentScoreVersion: String = scoreVersion,
        outdated: Boolean = false,
        contentType: String = "audio/mp4",
        failureCode: String? = null,
    ): String {
        val audio = if (status == "DONE") {
            """{"url":"$StreamUrl","urlExpiresAt":"$StreamExpiresAtText","contentType":"$contentType","byteSize":4096,""" +
                """"sha256":"${"9f".repeat(32)}","durationMs":3300,"fileName":"내 악보.m4a"}"""
        } else "null"
        val poll = if (status == "RUNNING") "3000" else "null"
        val failure = failureCode?.let { "\"$it\"" } ?: "null"
        return """{"isSuccess":true,"code":200,"result":{"sheetId":"42","status":"$status","scoreVersion":"$scoreVersion",""" +
            """"currentScoreVersion":"$currentScoreVersion","outdated":$outdated,"instrumentId":"2","withJangdan":true,"bpm":66,""" +
            """"failureCode":$failure,"pollAfterMs":$poll,"audio":$audio,"midi":null}}"""
    }

    private fun practiceRepository(
        client: HttpClient,
        deviceAudio: SheetAudioDeviceStore = PracticeDeviceAudio { _, _, _ -> null },
        member: SignedInMember = SignedInMember(FakeKeyValueStore()).apply { set("7") },
        streamsAudio: Boolean = false,
        now: () -> Long = { 0L },
    ) = SheetPracticeRepository(ApiSheetDocumentRepository(client), deviceAudio, ApiReferenceCatalogRepository(client),
        InstrumentCatalog(client), member, ApiSheetAudioRepository(client), now, streamsAudio)

    private companion object {
        const val StreamUrl = "https://r2.example/sheet-audio/42.m4a"
        const val StreamExpiresAtText = "2026-11-24T03:10:00Z"
        val StreamExpiresAt = Instant.parse(StreamExpiresAtText).toEpochMilliseconds()
    }

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
