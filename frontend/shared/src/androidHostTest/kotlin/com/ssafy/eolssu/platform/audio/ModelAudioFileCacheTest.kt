package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.ModelAudioFileCache
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path

class ModelAudioFileCacheTest {
    @Test fun samePublicationSurvivesScreenAndCacheRecreationWithoutAnotherDownload() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = ByteArray(48) { it.toByte() }
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.Authorization])
            if (request.url.host == "api.test") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://media.test/model.m4a?signed=one"))
            else respond(bytes)
        }
        try {
            val hash = hash(bytes)
            val first = assertNotNull(ModelAudioFileCache(Path(directory.path), engine, Dispatchers.IO)
                .fileFor("https://api.test/play", hash, M4A))
            assertContentEquals(bytes, File(first.path).readBytes())
            val offline = MockEngine { error("a repeated publication must use the file") }
            val next = assertNotNull(ModelAudioFileCache(Path(directory.path), offline, Dispatchers.IO)
                .fileFor("https://api.test/play?fresh-signature=two", hash, M4A))
            assertEquals(first.path, next.path)
            assertEquals(2, requests, "one redirect and one file download across visits")
        } finally { directory.deleteRecursively() }
    }

    @Test fun corruptionAndSystemRemovalDownloadAgainAndFailedBytesAreNeverPublished() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = ByteArray(48) { it.toByte() }
        var served = bytes
        var calls = 0
        val cache = ModelAudioFileCache(Path(directory.path), MockEngine { calls++; respond(served) }, Dispatchers.IO)
        try {
            val first = assertNotNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
            File(first.path).writeBytes(ByteArray(bytes.size))
            assertNotNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
            assertEquals(2, calls)
            assertContentEquals(bytes, File(first.path).readBytes())
            File(first.path).delete()
            served = ByteArray(bytes.size)
            assertNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
            assertFalse(directory.listFiles().orEmpty().any { it.extension in setOf("m4a", "audio", "part") })
            served = bytes
            assertNotNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
            assertEquals(4, calls)
        } finally { directory.deleteRecursively() }
    }

    @Test fun concurrentVisitsDownloadOnlyOnceAndEvictTheLeastRecentlyUsedFile() = runTest {
        val directory = createTempDirectory().toFile()
        var calls = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = ModelAudioFileCache(Path(directory.path), MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { calls++; respond(it.url.encodedPath.encodeToByteArray()) }
        }), dispatcher, maxFiles = 2)
        suspend fun visit(name: String) = assertNotNull(cache.fileFor("https://media.test/$name", hash("/$name".encodeToByteArray()), M4A))
        try {
            val together = listOf(async { visit("a") }, async { visit("a") }).awaitAll()
            assertEquals(together[0].path, together[1].path)
            assertEquals(1, calls)
            val b = visit("b")
            visit("a")
            visit("c")
            assertEquals(3, calls)
            assertFalse(File(b.path).exists())
            assertTrue(File(together[0].path).exists())
            assertEquals(2, directory.listFiles().orEmpty().count { it.extension == "m4a" })
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancellationAndTimeoutLeaveNoPlayableFileAndTheNextVisitRetries() = runTest {
        val directory = createTempDirectory().toFile()
        val entered = CompletableDeferred<Unit>()
        val bytes = ByteArray(32) { it.toByte() }
        var blocked = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler {
                if (blocked) { entered.complete(Unit); awaitCancellation() }
                respond(bytes)
            }
        })
        val cache = ModelAudioFileCache(Path(directory.path), engine, dispatcher, downloadTimeoutMs = 100)
        try {
            val request = launch { cache.fileFor("https://media.test/a", hash(bytes), M4A) }
            entered.await()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
            blocked = false
            assertNotNull(cache.fileFor("https://media.test/a", hash(bytes), M4A))
        } finally { directory.deleteRecursively() }
    }

    @Test fun sizeLimitInvalidRedirectAndServerFailureKeepTheRemoteFallbackAvailable() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = ByteArray(33) { it.toByte() }
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            when (request.url.encodedPath) {
                "/oversized" -> respond(bytes)
                "/redirect" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://media.test/a"))
                else -> respond("", HttpStatusCode.ServiceUnavailable)
            }
        }
        val cache = ModelAudioFileCache(Path(directory.path), engine, Dispatchers.IO, maxFileBytes = 32)
        try {
            for (path in listOf("oversized", "redirect", "error")) {
                assertNull(cache.fileFor("https://media.test/$path", hash(bytes), M4A))
            }
            assertNull(cache.fileFor("https://media.test/error", "../escape", M4A))
            assertNull(cache.fileFor("http://media.test/error", hash(bytes), M4A))
            assertEquals(3, calls)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun fileNameCarriesTheFormatExtensionAndReplacesAnUnplayableLegacyFile() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = ByteArray(48) { it.toByte() }
        var calls = 0
        val cache = ModelAudioFileCache(Path(directory.path), MockEngine { calls++; respond(bytes) }, Dispatchers.IO)
        try {
            val hash = hash(bytes)
            // iOS AVPlayer cannot open a local `.audio` file, so an old cached file is downloaded again under a playable name.
            File(directory, "$hash.audio").writeBytes(bytes)
            val file = assertNotNull(cache.fileFor("https://media.test/a", hash, M4A))
            assertEquals("$hash.m4a", File(file.path).name)
            assertEquals(1, calls)
            assertFalse(File(directory, "$hash.audio").exists())
            assertEquals("mp3", File(assertNotNull(cache.fileFor("https://media.test/a", hash, "audio/mpeg")).path).extension)
            assertNull(cache.fileFor("https://media.test/a", hash, "audio/flac"))
            assertEquals(2, calls, "an unknown format keeps the remote playback path without downloading")
        } finally { directory.deleteRecursively() }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private companion object {
        const val M4A = "audio/mp4"
    }
}
