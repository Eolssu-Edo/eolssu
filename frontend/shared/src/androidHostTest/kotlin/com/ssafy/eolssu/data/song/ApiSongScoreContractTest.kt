package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ApiSongScoreContractTest {
    private val score = Json.parseToJsonElement(requireNotNull(javaClass.getResourceAsStream("/scores/ngc-standard-2022-p173.score.json"))
        .bufferedReader().use { it.readText() })

    private fun detail(version: Int = 1, notes: Int = 61) = buildJsonObject {
        put("songId", "9007199254740993")
        put("instrumentId", "42")
        put("title", "아리랑")
        put("description", JsonNull)
        put("difficulty", 1)
        put("defaultBpm", 96)
        put("isScorable", true)
        put("noteCount", notes)
        put("scoreVersion", version)
        put("jangdan", Json.parseToJsonElement("""{"jangdanId":"3","name":"세마치","bpmUnit":"DOTTED_QUARTER","pattern":{"beats":["떵","","","떵","","덕","쿵","덕",""],"group":[3,3,3]}}"""))
        put("score", score)
    }

    private fun scoreResult(version: Int = 1, firstTick: Long = 0, firstNote: Int = 0, songId: String = "9007199254740993") = buildJsonObject {
        put("songId", songId)
        put("scoreVersion", version)
        put("schemaVersion", 1)
        put("noteCount", 61)
        put("defaultBpm", 96)
        put("bpmUnit", "DOTTED_QUARTER")
        put("score", score)
        var noteIndex = 0
        put("events", buildJsonArray {
            score.jsonObject.getValue("events").jsonArray.forEachIndexed { index, raw ->
                val event = raw.jsonObject
                val note = event.getValue("kind").jsonPrimitive.content == "NOTE"
                val position = event.getValue("cellId").jsonPrimitive.content.removePrefix("c").split("-j")
                add(buildJsonObject {
                    put("eventId", event.getValue("id")); put("kind", event.getValue("kind"))
                    put("startTick", if (index == 0) JsonPrimitive(firstTick) else event.getValue("startTick"))
                    put("durationTick", event.getValue("durationTick"))
                    put("columnIdx", position[0].toInt()); put("cellIdx", position[1].toInt())
                    put("noteIdx", if (note) JsonPrimitive(if (index == 0) firstNote.also { noteIndex++ } else noteIndex++) else JsonNull)
                    put("pitchId", if (note) JsonPrimitive("17") else JsonNull)
                })
            }
        })
    }

    @Test
    fun mergedControllerContractKeepsOriginalScoreAndUsesTicksForDuration() = runTest {
        val client = createHttpClient(MockEngine { request ->
            val result = when (request.url.encodedPath) {
                "/api/songs/9007199254740993" -> detail()
                "/api/songs/9007199254740993/score" -> scoreResult()
                else -> error("unexpected request")
            }
            respond(buildJsonObject { put("isSuccess", true); put("result", result) }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val song = assertNotNull(ApiSongRepository(client).getSong("9007199254740993"))
            assertEquals(61, song.noteCount)
            assertEquals(1, song.scoreVersion)
            assertEquals("42", song.instrumentId)
            assertEquals("c0-j0-r0-u0", song.timeline.first().id)
            assertEquals(30, song.durationSeconds())
            assertEquals(listOf(12, 12, 12, 12), song.score.columns.map { it.size })
        } finally { client.close() }
    }

    @Test
    fun staleVersionAndWrongNoteCountAreNotDisplayedAsMatchingScore() = runTest {
        for (item in listOf(detail(version = 2), detail(notes = 62))) {
            val client = createHttpClient(MockEngine { request ->
                respond(buildJsonObject { put("isSuccess", true); put("result", if (request.url.encodedPath.endsWith("/score")) scoreResult() else item) }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(client).getSong("9007199254740993") } }
            finally { client.close() }
        }
    }

    @Test
    fun scoreEndpointCannotChangeIdentityNoteIndexOrIntegerTimeline() = runTest {
        for (projected in listOf(scoreResult(version = 2), scoreResult(firstTick = 1), scoreResult(firstNote = 4), scoreResult(songId = "2"))) {
            val client = createHttpClient(MockEngine { request ->
                respond(buildJsonObject { put("isSuccess", true); put("result", if (request.url.encodedPath.endsWith("/score")) projected else detail()) }.toString(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(client).getSong("9007199254740993") } }
            finally { client.close() }
        }
    }
}
