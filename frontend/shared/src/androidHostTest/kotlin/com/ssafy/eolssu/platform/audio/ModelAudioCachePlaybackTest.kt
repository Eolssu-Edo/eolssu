package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.ModelAudioFileCache
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.ApiModelAudioRepository
import com.ssafy.eolssu.data.song.audioFixture
import com.ssafy.eolssu.data.song.smallScoreSong
import com.ssafy.eolssu.feature.practice.FakeAudioPlayer
import com.ssafy.eolssu.feature.practice.ModelAudioSession
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.io.files.Path

@OptIn(ExperimentalCoroutinesApi::class)
class ModelAudioCachePlaybackTest {
    @Test fun newScreenReusesFileButStillChecksPublishedMetadataAndAudioReplacement() = runTest {
        val directory = createTempDirectory().toFile()
        var bytes = ByteArray(32) { it.toByte() }
        fun metadata() = audioFixture().copy(
            audioUrl = "/api/songs/42/model-audio/play", playbackRevisionSupported = true,
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
        )
        var published = true
        var metadataCalls = 0
        var downloads = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = createHttpClient(MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler {
                metadataCalls++
                respond("""{"isSuccess":true,"result":${Json.encodeToString(metadata())}}""",
                    if (published) HttpStatusCode.OK else HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }), ServerAddress.Production)
        var mediaAvailable = true
        val engine = MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { downloads++; respond(bytes, if (mediaAvailable) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable) }
        })
        val cache = ModelAudioFileCache(Path(directory.path), engine, dispatcher)
        val repository = ApiModelAudioRepository(api, cache)
        val song = smallScoreSong().copy(id = "42", scoreVersion = 1)
        try {
            val firstPlayer = FakeAudioPlayer()
            val first = ModelAudioSession(repository, firstPlayer, backgroundScope)
            first.play(song, song.baseBpm)
            runCurrent()
            val file = assertNotNull(firstPlayer.requestedSource?.localFile)
            assertEquals("", firstPlayer.requestedSource?.url)
            firstPlayer.emit(AudioPlaybackStatus.Playing, 200)
            runCurrent()
            assertEquals(ModelAudioStatus.Playing, first.state.value.status)
            first.release()
            assertTrue(firstPlayer.released)

            val nextPlayer = FakeAudioPlayer()
            val next = ModelAudioSession(repository, nextPlayer, backgroundScope)
            next.play(song, song.baseBpm)
            runCurrent()
            assertEquals(file.path, nextPlayer.requestedSource?.localFile?.path)
            assertEquals(1, downloads)
            assertEquals(2, metadataCalls)
            next.release()

            bytes = ByteArray(32) { (it + 1).toByte() }
            val changed = assertNotNull(repository.get(song))
            val replacement = assertNotNull(changed.localFile)
            assertTrue(file.path != replacement.path)
            assertContentEquals(bytes, File(replacement.path).readBytes())
            assertEquals(2, downloads)
            bytes = ByteArray(32) { (it + 2).toByte() }
            mediaAvailable = false
            val remote = assertNotNull(repository.get(song))
            assertNull(remote.localFile)
            assertTrue(remote.audioUrl.startsWith("https://"))
            published = false
            assertNull(repository.get(song), "hidden publications cannot be played from the file cache")
        } finally { api.close(); directory.deleteRecursively() }
    }
}
