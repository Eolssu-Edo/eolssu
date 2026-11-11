package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.practice.ApiEvaluationRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class HistoricalEvaluationTest {
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

internal fun revisionSongResponse(version: Int) =
    """{"songId":"42","title":"작은 검사 악보","instrumentId":"1","difficulty":1,"defaultBpm":60,"jangdan":null,"isScorable":true,"noteCount":2,"scoreVersion":$version}"""

internal fun revisionScoreResponse(version: Int): String {
    val data = smallScoreData().let { it.copy(score = it.score.copy(scoreVersion = version),
        music = if (version == 1) it.music else it.music.copy(ticksPerBeat = 6)) }
    val events = data.events.mapIndexed { index, event ->
        val note = if (event.kind == "NOTE") index.toString() else "null"
        val pitch = if (event.kind == "NOTE") "\"${index + 1}\"" else "null"
        """{"eventId":"${event.id}","kind":"${event.kind}","noteIdx":$note,"pitchId":$pitch,"startTick":${event.startTick},"durationTick":${event.durationTick},"columnIdx":${if (index == 2) 1 else 0},"cellIdx":0}"""
    }
    return """{"songId":"42","scoreVersion":$version,"schemaVersion":1,"noteCount":2,"defaultBpm":60,"bpmUnit":"DOTTED_QUARTER","score":${Json.encodeToString(data)},"events":[${events.joinToString(",")}] }"""
}
