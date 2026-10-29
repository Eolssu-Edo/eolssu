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

    @Test
    fun mergedControllerContractKeepsOriginalScoreAndUsesTicksForDuration() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/songs/9007199254740993", request.url.encodedPath)
            respond(buildJsonObject { put("isSuccess", true); put("result", detail()) }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
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
            val client = createHttpClient(MockEngine {
                respond(buildJsonObject { put("isSuccess", true); put("result", item) }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(client).getSong("9007199254740993") } }
            finally { client.close() }
        }
    }
}
