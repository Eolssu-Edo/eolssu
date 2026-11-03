package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opt-in local corpus verification. Raw external scores/audio stay outside the repository. */
class GeneratedCatalogBundleTest {
    @Test fun all107GeneratedScoresBindToTheirPublishedPerformanceOverHttp() = runTest {
        val directory = System.getenv("EOLSSU_MODEL_AUDIO_BUNDLE")
        assumeTrue("Generated corpus not configured", directory != null)
        val root = File(requireNotNull(directory))
        val cases = Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonObject.getValue("cases").jsonArray
        assertEquals(107, cases.size)
        val instruments = mutableMapOf<String, Int>()
        for ((index, element) in cases.withIndex()) {
            val case = element.jsonObject
            fun value(key: String) = case.getValue(key).jsonPrimitive.content
            val caseId = value("caseId")
            val bytes = File(root, "cases/$caseId/source.common.json").readBytes()
            val sourceHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(value("sourceCommonSha256"), sourceHash, caseId)
            val common = CommonScoreData.decode(bytes.toString(Charsets.UTF_8))
            val song = Song((index + 1).toString(), common.score.title, SongLevel.Intro, null,
                value("defaultBpm").toDouble().toInt(), "", false, null, common.toScore(), scoreVersion = 1,
                noteCount = value("noteCount").toInt())
            instruments[common.score.instrument] = (instruments[common.score.instrument] ?: 0) + 1
            val audioData = case.getValue("audio").jsonObject
            fun audioValue(key: String) = audioData.getValue(key).jsonPrimitive.content
            val metadata = ModelAudio(song.id, 1, "/api/songs/${song.id}/model-audio/play", song.baseBpm,
                common.music.bpmUnit, audioValue("durationMs").toLong(), audioValue("startOffsetMs").toLong(),
                audioValue("contentType"), audioValue("sha256"), "/api/songs/${song.id}/model-audio/performance", sourceHash)
            val performanceJson = File(root, "cases/$caseId/performance.common.json").readText()
            val client = createHttpClient(MockEngine {
                respond(if (it.url.encodedPath.endsWith("/performance")) performanceJson
                    else """{"isSuccess":true,"result":${Json.encodeToString(metadata)}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            try {
                val audio = assertNotNull(ApiModelAudioRepository(client).get(song), caseId)
                val performance = assertNotNull(audio.performance, caseId)
                assertTrue(audio.matches(song), caseId)
                assertEquals(common.events.count { it.kind == "NOTE" }, song.noteCount, caseId)
                assertEquals(common.music.jangdan, song.jangdanName, caseId)
                if (caseId == "jeongak-daegeum-019-p1") {
                    assertEquals(28000.0, song.durationMs())
                    assertEquals(29000.014499999997, performance.durationMs)
                }
            } finally { client.close() }
        }
        assertEquals(mapOf("DANSO" to 46, "SOGEUM" to 23, "JEONGAK_DAEGEUM" to 28, "SANJO_DAEGEUM" to 10), instruments)
    }
}
