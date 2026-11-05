package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class JangdanSampleCacheTest {
    @Test
    fun downloadsOnceAndReusesVerifiedFileOfflineAcrossCacheInstances() = runTest {
        val directory = createTempDirectory().toFile()
        val wav = ByteArray(46) { it.toByte() }
        val digest = sha256(wav)
        val manifest = manifest(digest)
        var online = true
        val requests = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            val host = request.url.host
            requests += host
            if (!online) throw IOException("offline")
            if (host == "api.test") {
                assertEquals("/api/jangdan-samples/deong/$digest", request.url.encodedPath)
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://r2.test/wav"))
            } else {
                assertEquals("r2.test", host)
                respond(ByteReadChannel(wav))
            }
        }, ServerAddress("https://api.test"))
        try {
            val file = JangdanSampleCache(directory, client) { manifest }.fileFor(JangdanSample.Deong)
            assertContentEquals(wav, file.readBytes())
            online = false
            assertEquals(file, JangdanSampleCache(directory, client) { manifest }.fileFor(JangdanSample.Deong))
            assertEquals(listOf("api.test", "r2.test"), requests)

            file.writeBytes(wav.reversedArray())
            assertFailsWith<IOException> {
                JangdanSampleCache(directory, client) { manifest }.fileFor(JangdanSample.Deong)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            online = true
            assertContentEquals(wav, JangdanSampleCache(directory, client) { manifest }.fileFor(JangdanSample.Deong).readBytes())
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedOrWrongHashDownloadsNeverBecomeCacheFiles() = runTest {
        val directory = createTempDirectory().toFile()
        val wav = ByteArray(46) { it.toByte() }
        val manifest = manifest(sha256(wav))
        var payload = wav + ByteArray(1024)
        val client = createHttpClient(MockEngine { respond(ByteReadChannel(payload)) }, ServerAddress("https://api.test"))
        try {
            val cache = JangdanSampleCache(directory, client) { manifest }
            assertFailsWith<IllegalStateException> { cache.fileFor(JangdanSample.Deong) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            payload = wav.reversedArray()
            assertFailsWith<IllegalStateException> { cache.fileFor(JangdanSample.Deong) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    private fun manifest(digest: String) =
        """{"assets":[{"file":"deong.wav","sha256":"$digest","frames":1}]}""".encodeToByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
