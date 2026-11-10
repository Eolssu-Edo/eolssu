package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.FileSheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.audio.SheetAudioPerformance
import io.ktor.client.engine.mock.MockEngine
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SheetAudioDeviceStoreTest {
    @Test
    fun savedM4aSurvivesStoreRecreationAndIsIsolatedByAccountSheetAndVersion() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = m4a(1)
        val engine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(bytes, headers = audioHeaders)
        }
        try {
            val stored = assertNotNull(store(directory, engine).save("5", "27", "2", download(bytes)))
            assertContentEquals(bytes, File(stored.localFile.path).readBytes())
            val offlineEngine = MockEngine { error("기기에 저장한 음원은 서버 조회 없이 재생할 수 있다") }
            val reopened = store(directory, offlineEngine)
            val found = assertNotNull(reopened.find("5", "27", "2"))
            assertEquals(stored.localFile.path, found.localFile.path)
            assertEquals(stored.sha256, found.sha256)
            assertEquals(72, found.bpm)
            assertNull(reopened.find("5", "27", "1"))
            assertNull(reopened.find("5", "28", "2"))
            assertNull(reopened.find("6", "27", "2"))
            assertNull(reopened.find("..", "27", "2"))
            reopened.delete("5", "27")
            assertNull(reopened.find("5", "27", "2"))
            assertFalse(File(stored.localFile.path).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun performanceSurvivesRestartOnlyWithItsVerifiedAudioAndVersion() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = m4a(1)
        val info = download(bytes)
        val sourceHash = "b".repeat(64)
        val timeline = buildJsonObject {
            put("sourceSha256", sourceHash)
            put("audioSha256", info.sha256)
            put("scoreVersion", 2)
            put("fixture", JsonPrimitive("x".repeat(2048)))
        }
        val performance = SheetAudioPerformance(sourceHash, timeline)
        val engine = MockEngine { respond(bytes, headers = audioHeaders) }
        try {
            val stored = assertNotNull(store(directory, engine).save("5", "27", "2", info.copy(performance = performance)))
            assertEquals(performance, stored.performance)
            val reopened = store(directory, engine)
            assertEquals(performance, assertNotNull(reopened.find("5", "27", "2")).performance)
            assertNull(reopened.find("5", "27", "3"))
            val replacement = assertNotNull(reopened.save("5", "27", "3", info.copy(performance = performance)))
            assertNull(replacement.performance)
            assertNull(reopened.find("5", "27", "2"))
            assertContentEquals(bytes, File(replacement.localFile.path).readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidAndInterruptedDownloadsKeepThePreviousFileUntilReplacementIsComplete() = runTest {
        val directory = createTempDirectory().toFile()
        val first = m4a(1)
        val second = m4a(2)
        val loading = CompletableDeferred<Unit>()
        var response = first
        var stall = false
        val engine = MockEngine {
            if (stall) {
                loading.complete(Unit)
                awaitCancellation()
            }
            respond(response, headers = audioHeaders)
        }
        try {
            val device = store(directory, engine)
            val initial = assertNotNull(device.save("5", "27", "2", download(first)))
            assertNull(device.save("5", "27", "3", download(second)))
            response = second + byteArrayOf(0)
            assertNull(device.save("5", "27", "3", download(second)))
            val invalidContainer = ByteArray(first.size)
            response = invalidContainer
            assertNull(device.save("5", "27", "3", download(invalidContainer)))
            stall = true
            val pending = launch { device.save("5", "27", "3", download(second)) }
            loading.await()
            pending.cancelAndJoin()
            assertEquals(initial.sha256, assertNotNull(device.find("5", "27", "2")).sha256)
            assertNull(device.find("5", "27", "3"))
            assertContentEquals(first, File(initial.localFile.path).readBytes())
            stall = false
            response = second
            val replacement = assertNotNull(device.save("5", "27", "3", download(second)))
            assertContentEquals(second, File(replacement.localFile.path).readBytes())
            assertFalse(File(initial.localFile.path).exists())
            assertNull(device.find("5", "27", "2"))
            response = first
            assertNull(device.save("5", "27", "2", download(first)))
            assertEquals(replacement.sha256, assertNotNull(device.find("5", "27", "3")).sha256)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun signedDownloadRejectsUnsafeUrlsAndDoesNotFollowRedirects() = runTest {
        val directory = createTempDirectory().toFile()
        val bytes = m4a(1)
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/test"))
        }
        try {
            val device = store(directory, engine)
            for (url in listOf("file:///tmp/file.m4a", "http://127.0.0.1/test", "https://user@media.example/test")) {
                assertNull(device.save("5", "27", "2", download(bytes).copy(url = url)))
            }
            assertEquals(0, calls)
            assertNull(device.save("5", "27", "2", download(bytes)))
            assertEquals(1, calls)
            assertNull(device.find("5", "27", "2"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun store(directory: File, engine: MockEngine) =
        FileSheetAudioDeviceStore(Path(directory.absolutePath), engine, Dispatchers.IO)

    private fun m4a(marker: Byte) = byteArrayOf(0, 0, 0, 16) + "ftypM4A ".encodeToByteArray() + byteArrayOf(marker, 0, 0, 0)

    private fun download(bytes: ByteArray) = SheetAudioDownload(
        url = "https://media.example/download?signature=sample",
        byteSize = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        durationMs = 2100,
        instrumentId = "3",
        bpm = 72,
        withJangdan = true,
    )

    private companion object {
        val audioHeaders = headersOf(HttpHeaders.ContentType, "audio/mp4")
    }
}
