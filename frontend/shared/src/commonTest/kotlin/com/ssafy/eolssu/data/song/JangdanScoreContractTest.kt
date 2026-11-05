package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JangdanScoreContractTest {
    @Test fun additiveMetadataIsOptionalAndDoesNotInferFromSongOrJangdanName() = runTest {
        for (metadata in listOf("", ",\"jangdanPlayback\":null")) {
            val client = client(metadata)
            try {
                val song = ApiSongRepository(client).getSong("42")!!
                assertEquals("세마치", song.jangdanName)
                assertNull(song.jangdanPlayback)
                assertEquals(3, song.timeline.size)
            } finally { client.close() }
        }
    }

    @Test fun reviewedTickScaleAndPatternSurviveWithoutLegacySongForeignKey() = runTest {
        val client = client(",\"jangdanPlayback\":$MAPPING")
        try {
            val song = ApiSongRepository(client).getSong("42")!!
            val mapping = song.jangdanPlayback!!
            assertEquals("3", mapping.jangdanId)
            assertEquals(9L, mapping.cycleTicks)
            assertEquals(0L, mapping.startTick)
            assertEquals(listOf(3, 3, 3), mapping.groups)
            assertEquals(listOf("떵", "", "", "떵", "", "덕", "쿵", "덕", ""), mapping.beats)
            assertEquals(60, song.baseBpm)
            assertNull(song.jangdan)
        } finally { client.close() }
    }

    @Test fun wrongNameNegativeStartAndInvalidGroupsCannotScheduleSound() = runTest {
        for (bad in listOf(MAPPING.replace("세마치", "굿거리"), MAPPING.replace("\"startTick\":0", "\"startTick\":-1"),
            MAPPING.replace("[3,3,3]", "[3,3,2]"))) {
            val client = client(",\"jangdanPlayback\":$bad")
            try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(client).getSong("42") } }
            finally { client.close() }
        }
    }

    @Test fun supplementalPlaybackUsesTheReviewedDisplayNameWithoutChangingTheScore() = runTest {
        val client = client(",\"supplementalJangdanPlayback\":$MAPPING", supplemental = true)
        try {
            val song = ApiSongRepository(client).getSong("42")!!
            assertEquals("세마치", song.jangdanName)
            assertEquals("", song.score.timing?.jangdan)
            assertEquals(9L, song.jangdanPlayback?.cycleTicks)
        } finally { client.close() }
    }

    private fun client(metadata: String, supplemental: Boolean = false) = createHttpClient(MockEngine { request ->
        val result = if (request.url.encodedPath.endsWith("/score")) {
            val data = smallScoreData()
            val events = data.events.mapIndexed { index, event ->
                val pitch = if (event.kind == "NOTE") "\"${index + 1}\"" else "null"
                val note = if (event.kind == "NOTE") index.toString() else "null"
                """{"eventId":"${event.id}","kind":"${event.kind}","noteIdx":$note,"pitchId":$pitch,"startTick":${event.startTick},"durationTick":${event.durationTick},"columnIdx":${if (index == 2) 1 else 0},"cellIdx":0}"""
            }
            val score = Json.encodeToString(data).let { if (supplemental) it.replace("\"jangdan\":\"세마치\"", "\"jangdan\":\"\"") else it }
            """{"songId":"42","scoreVersion":1,"schemaVersion":1,"noteCount":2,"defaultBpm":60,"bpmUnit":"DOTTED_QUARTER","score":$score,"events":[${events.joinToString(",")}]$metadata}"""
        } else {
            val displayName = if (supplemental) ",\"jangdanName\":\"세마치\"" else ""
            """{"songId":"42","title":"작은 검사 악보","instrumentId":"1","difficulty":1,"defaultBpm":60,"jangdan":null,"isScorable":false,"noteCount":2,"scoreVersion":1$displayName}"""
        }
        respond("""{"isSuccess":true,"result":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }, ServerAddress.Production)

    private companion object {
        const val MAPPING = """{"jangdanId":"3","name":"세마치","startTick":0,"cycleTicks":9,"pattern":{"beats":["떵","","","떵","","덕","쿵","덕",""],"group":[3,3,3]}}"""
    }
}
