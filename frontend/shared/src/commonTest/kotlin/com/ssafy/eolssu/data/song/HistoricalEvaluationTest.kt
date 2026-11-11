package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.ApiEvaluationRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class HistoricalEvaluationTest {
    @Test
    fun aSlowScoreDoesNotDelayOrReplaceTheNextSong() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        val firstScoreRequested = CompletableDeferred<Unit>()
        val releaseFirstScore = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val result = when (path) {
                "/api/songs/42" -> revisionSongResponse(1)
                "/api/songs/42/score" -> {
                    firstScoreRequested.complete(Unit)
                    releaseFirstScore.await()
                    revisionScoreResponse(1)
                }
                "/api/songs/43" -> revisionSongResponse(1).replace("\"songId\":\"42\"", "\"songId\":\"43\"")
                "/api/songs/43/score" -> revisionScoreResponse(1).replace("\"songId\":\"42\"", "\"songId\":\"43\"")
                else -> error(path)
            }
            respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client, member)
            val first = async { songs.getSong("42") }
            firstScoreRequested.await()
            val secondSong = try {
                withContext(Dispatchers.Default) {
                    assertNotNull(withTimeout(5_000) { songs.getSong("43") })
                }
            } finally {
                releaseFirstScore.complete(Unit)
            }
            assertEquals("43", secondSong.id)
            assertEquals("42", first.await()?.id)
            assertSame(secondSong, songs.retainedSong("43"))
        } finally { client.close() }
    }

    @Test
    fun changedSongDetailsInvalidateTheSameScoreRevision() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        var title = REVISION_TITLE
        var scoreRequests = 0
        val client = createHttpClient(MockEngine { request ->
            val result = when (val path = request.url.encodedPath) {
                "/api/songs/42" -> revisionSongResponse(1, title)
                "/api/songs/42/score" -> {
                    scoreRequests++
                    revisionScoreResponse(1, title)
                }
                else -> error(path)
            }
            respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client, member)
            val original = assertNotNull(songs.getSong("42", 1))
            assertEquals(1, songs.getPublishedScoreVersion("42"))
            assertSame(original, songs.retainedSong("42"))

            title = "제목을 고친 검사 악보"
            assertEquals(1, songs.getPublishedScoreVersion("42"))
            assertNull(songs.retainedSong("42"))
            assertNull(songs.retainedSong("42", 1))
            assertEquals(1, scoreRequests)

            val refreshed = assertNotNull(songs.getSong("42", 1))
            assertEquals(title, refreshed.title)
            assertEquals(title, refreshed.score.source?.title)
            assertSame(refreshed, songs.retainedSong("42", 1))
            assertEquals(2, scoreRequests)
        } finally { client.close() }
    }

    @Test
    fun publishedReuseEndsOnLogoutAndSameMemberRelogin() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        var latest = 1
        var requests = 0
        val client = createHttpClient(MockEngine { request ->
            requests++
            val version = request.url.parameters["scoreVersion"]?.toInt() ?: latest
            val result = if (request.url.encodedPath.endsWith("/score")) revisionScoreResponse(version) else revisionSongResponse(version)
            respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client, member)
            val detail = checkNotNull(songs.getSong("42"))
            assertSame(detail, songs.retainedSong("42"))
            assertNull(songs.retainedSong("43"))
            assertEquals(2, requests)

            latest = 2
            val newDetail = checkNotNull(songs.getSong("42"))
            assertEquals(2, newDetail.scoreVersion)
            assertSame(newDetail, songs.retainedSong("42"))
            member.set(null)
            assertNull(songs.retainedSong("42"))
            member.set("A")
            assertNull(songs.retainedSong("42"))
            assertEquals(2, songs.getSong("42", 2)?.scoreVersion)
            assertEquals(6, requests)
        } finally { client.close() }
    }

    @Test
    fun cancelledAndFailedScoreRequestsAllowACompleteRetry() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        val inScore = CompletableDeferred<Unit>()
        var details = 0
        var scores = 0
        val client = createHttpClient(MockEngine { request ->
            val result = if (request.url.encodedPath.endsWith("/score")) {
                when (++scores) {
                    1 -> { inScore.complete(Unit); awaitCancellation() }
                    2 -> return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
                    else -> revisionScoreResponse(1)
                }
            } else {
                details++
                revisionSongResponse(1)
            }
            respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client, member)
            val loading = launch { songs.getSong("42") }
            inScore.await()
            loading.cancelAndJoin()
            assertNull(songs.retainedSong("42"))
            assertFailsWith<CatalogUnavailable> { songs.getSong("42") }
            assertNull(songs.retainedSong("42"))
            val song = checkNotNull(songs.getSong("42"))
            assertSame(song, songs.retainedSong("42"))
            assertEquals(3, details)
            assertEquals(3, scores)
        } finally { client.close() }
    }

    @Test fun missingOrWrongRevisionNeverFallsBackToCurrentScore() = runTest {
        for (missing in listOf(true, false)) {
            var requests = 0
            val client = createHttpClient(MockEngine { request ->
                requests++
                assertEquals("1", request.url.parameters["scoreVersion"])
                respond(if (missing) "{}" else """{"isSuccess":true,"result":${revisionSongResponse(2)}}""",
                    if (missing) HttpStatusCode.NotFound else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try {
                val songs = ApiSongRepository(client)
                if (missing) assertNull(songs.getSong("42", 1))
                else assertFailsWith<CatalogUnavailable> { songs.getSong("42", 1) }
                assertEquals(1, requests)
                assertFailsWith<CatalogUnavailable> { songs.getSong("42", 0) }
                assertEquals(1, requests)
            } finally { client.close() }
        }
    }

    @Test fun completedEvaluationUsesItsArchivedScoreAfterCurrentRevisionChanges() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val requested = request.url.parameters["scoreVersion"]
            requests += path to requested
            val version = requested?.toInt() ?: 2
            val result = when {
                path.endsWith("/evaluation") -> """{"sessionId":"41","songId":"42","scoreVersion":1,"bpm":60,"startedAt":"2026-10-29T00:00:00Z","totalScore":80,"pitchScore":80,"timingScore":80,"durationScore":80,"notes":[{"noteIdx":1,"verdict":"PITCH"}]}"""
                path.endsWith("/score") -> revisionScoreResponse(version)
                else -> revisionSongResponse(version)
            }
            respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val songs = ApiSongRepository(client)
            val screen = assertNotNull(ApiEvaluationRepository(client, songs).resultForScreen("41", null))
            val (result, historicalSong) = assertNotNull(screen.loadDetails())
            assertEquals(1, result.scoreVersion)
            assertEquals(1, result.errors.size)
            assertEquals(mapOf(1 to listOf(com.ssafy.eolssu.core.model.ScoreErrorKind.Pitch)), result.eventErrors)
            assertEquals(result.errors, screen.scores.withScore(checkNotNull(historicalSong)).errors)
            assertEquals(result.eventErrors, screen.scores.withScore(historicalSong).eventErrors)
            assertEquals(4, historicalSong?.score?.timing?.ticksPerBeat)
            assertEquals(listOf(null, "1", "1"), requests.map { it.second })
            val current = assertNotNull(songs.getSong("42"))
            assertEquals(2, current.scoreVersion, "새 연습은 현재 판본을 사용한다")
            assertEquals(6, current.score.timing?.ticksPerBeat)
        } finally { client.close() }
    }
}

private const val REVISION_TITLE = "작은 검사 악보"

internal fun revisionSongResponse(version: Int, title: String = REVISION_TITLE) =
    """{"songId":"42","title":${Json.encodeToString(title)},"instrumentId":"1","difficulty":1,"defaultBpm":60,"jangdan":null,"isScorable":true,"noteCount":2,"scoreVersion":$version}"""

internal fun revisionScoreResponse(version: Int, title: String = REVISION_TITLE): String {
    val data = smallScoreData().let { it.copy(score = it.score.copy(scoreVersion = version, title = title),
        music = if (version == 1) it.music else it.music.copy(ticksPerBeat = 6)) }
    val events = data.events.mapIndexed { index, event ->
        val note = if (event.kind == "NOTE") index.toString() else "null"
        val pitch = if (event.kind == "NOTE") "\"${index + 1}\"" else "null"
        """{"eventId":"${event.id}","kind":"${event.kind}","noteIdx":$note,"pitchId":$pitch,"startTick":${event.startTick},"durationTick":${event.durationTick},"columnIdx":${if (index == 2) 1 else 0},"cellIdx":0}"""
    }
    return """{"songId":"42","scoreVersion":$version,"schemaVersion":1,"noteCount":2,"defaultBpm":60,"bpmUnit":"DOTTED_QUARTER","score":${Json.encodeToString(data)},"events":[${events.joinToString(",")}] }"""
}
