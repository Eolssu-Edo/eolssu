package com.ssafy.eolssu.core.audio

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.util.sha256
import io.ktor.utils.io.ByteReadChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray

class JangdanSampleFilesTest {
    private val fs = SystemFileSystem

    @Test
    fun downloadsOnceAndReusesVerifiedFileOfflineAcrossCacheInstances() = runTest {
        val directory = tempDirectory()
        val m4a = ByteArray(46) { it.toByte() }
        val digest = sha256(m4a).toHexString()
        val manifest = manifest(digest)
        var online = true
        val requests = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            val host = request.url.host
            requests += host
            if (!online) throw IllegalStateException("offline")
            if (host == "api.test") {
                assertEquals("/api/jangdan-samples/deong/$digest", request.url.encodedPath)
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://r2.test/m4a"))
            } else {
                assertEquals("r2.test", host)
                respond(ByteReadChannel(m4a))
            }
        }, ServerAddress("https://api.test"))
        try {
            val file = JangdanSampleFiles(directory, client, Dispatchers.IO) { manifest }.fileFor(JangdanSample.Deong)
            assertContentEquals(m4a, read(file.path))
            assertEquals(1, file.frames)
            online = false
            assertEquals(file, JangdanSampleFiles(directory, client, Dispatchers.IO) { manifest }.fileFor(JangdanSample.Deong))
            assertEquals(listOf("api.test", "r2.test"), requests)

            write(file.path, m4a.reversedArray())
            assertFails { JangdanSampleFiles(directory, client, Dispatchers.IO) { manifest }.fileFor(JangdanSample.Deong) }
            assertTrue(fs.list(directory).isEmpty())
            online = true
            val restored = JangdanSampleFiles(directory, client, Dispatchers.IO) { manifest }.fileFor(JangdanSample.Deong)
            assertContentEquals(m4a, read(restored.path))
        } finally {
            client.close()
            deleteRecursively(directory)
        }
    }

    @Test
    fun oversizedOrWrongHashDownloadsNeverBecomeCacheFiles() = runTest {
        val directory = tempDirectory()
        val m4a = ByteArray(46) { it.toByte() }
        val manifest = manifest(sha256(m4a).toHexString())
        var payload = m4a + ByteArray(1024)
        val client = createHttpClient(MockEngine { respond(ByteReadChannel(payload)) }, ServerAddress("https://api.test"))
        try {
            val cache = JangdanSampleFiles(directory, client, Dispatchers.IO) { manifest }
            assertFailsWith<IllegalStateException> { cache.fileFor(JangdanSample.Deong) }
            assertTrue(!fs.exists(directory) || fs.list(directory).isEmpty())
            payload = m4a.reversedArray()
            assertFailsWith<IllegalStateException> { cache.fileFor(JangdanSample.Deong) }
            assertTrue(!fs.exists(directory) || fs.list(directory).isEmpty())
        } finally {
            client.close()
            deleteRecursively(directory)
        }
    }

    private fun manifest(digest: String) =
        """{"assets":[{"file":"deong.m4a","sha256":"$digest","bytes":46,"frames":1}]}""".encodeToByteArray()

    private fun tempDirectory() = Path(SystemTemporaryDirectory, "jangdan-test-${Random.nextLong().toULong()}")

    private fun read(path: Path) = fs.source(path).buffered().use { it.readByteArray() }

    private fun write(path: Path, bytes: ByteArray) = fs.sink(path).buffered().use { it.write(bytes) }

    private fun deleteRecursively(path: Path) {
        if (!fs.exists(path)) return
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteRecursively)
        fs.delete(path)
    }
}
