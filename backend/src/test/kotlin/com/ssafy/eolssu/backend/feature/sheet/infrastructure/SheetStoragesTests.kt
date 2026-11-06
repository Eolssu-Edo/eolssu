package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** 로컬 HTTP 서버를 S3 엔드포인트로 두고 요청 모양만 본다. 실제 R2 확인은 운영 설정 뒤 수동으로 한다. */
class SheetStoragesTests {
    private class Received(val method: String, val path: String, val contentType: String?)

    @TempDir lateinit var dir: Path
    private val received = CopyOnWriteArrayList<Received>()
    @Volatile private var status = 200
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            exchange.requestBody.readAllBytes()
            received += Received(exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestHeaders.getFirst("Content-Type"))
            exchange.sendResponseHeaders(if (exchange.requestMethod == "DELETE" && status == 200) 204 else status, -1)
            exchange.close()
        }
        start()
    }
    private val storage = S3SheetSourceStorage.create(
        SheetStorageProperties(
            endpoint = "http://127.0.0.1:${server.address.port}", bucket = "eolssu-sheets",
            accessKeyId = "test-access-key", secretAccessKey = "test-secret-key",
        ),
        maxAttempts = 1,
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `download signature includes a safe UTF8 title and fifteen minute expiry`() {
        val url=java.net.URI(storage.downloadUrl("sheet-audio/1/test/audio.m4a",java.time.Duration.ofMinutes(15),"아리랑 (내 편곡).m4a"))
        val query=url.rawQuery.split('&').associate { part ->
            val (key,value)=part.split('=',limit=2)
            java.net.URLDecoder.decode(key,Charsets.UTF_8) to java.net.URLDecoder.decode(value,Charsets.UTF_8)
        }
        assertEquals("900",query["X-Amz-Expires"])
        val disposition=query["response-content-disposition"]!!
        assertTrue(disposition.startsWith("attachment;"))
        assertTrue(disposition.contains("filename*=UTF-8''"))
        assertTrue(java.net.URLDecoder.decode(disposition.substringAfter("filename*=UTF-8''"),Charsets.UTF_8).contains("아리랑 (내 편곡).m4a"))
        assertFailsWith<IllegalArgumentException> { storage.downloadUrl("k",java.time.Duration.ofMinutes(15),"bad\r\nname") }
    }

    @Test
    fun `puts each file with its own content type and deletes by key`() {
        val file = Files.write(dir.resolve("a"), byteArrayOf(1, 2))
        storage.put("sheet-sources/7/x/0.png", file, "image/png")
        storage.put("sheet-sources/7/x/1.pdf", file, "application/pdf")
        storage.delete("sheet-sources/7/x/0.png")

        assertEquals(listOf("PUT", "PUT", "DELETE"), received.map { it.method })
        assertEquals("/eolssu-sheets/sheet-sources/7/x/0.png", received[0].path)
        assertEquals(listOf("image/png", "application/pdf"), received.take(2).map { it.contentType })
    }

    @Test
    fun `storage failures become sheet storage errors`() {
        status = 503
        val file = Files.write(dir.resolve("a"), byteArrayOf(1))
        assertFailsWith<SheetSourceStorageException> { storage.put("k", file, "image/png") }
        assertFailsWith<SheetSourceStorageException> { storage.delete("k") }
    }

    @Test
    fun `local storage keeps keys inside its directory and unconfigured storage is unavailable`() {
        val local = LocalSheetSourceStorage(dir.resolve("store"))
        val file = Files.write(dir.resolve("src"), byteArrayOf(9, 8))
        local.put("sheet-sources/1/a/0.png", file, "image/png")
        assertContentEquals(byteArrayOf(9, 8), Files.readAllBytes(dir.resolve("store/sheet-sources/1/a/0.png")))
        local.delete("sheet-sources/1/a/0.png")
        assertFalse(Files.exists(dir.resolve("store/sheet-sources/1/a/0.png")))
        assertFailsWith<SheetSourceStorageException> { local.put("../escape", file, "image/png") }

        assertFalse(UnavailableSheetSourceStorage().available)
        assertFailsWith<SheetSourceStorageException> { UnavailableSheetSourceStorage().put("k", file, "image/png") }
    }

    @Test
    fun `source download time limit includes a stalled response body`() {
        val stalled=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val release=java.util.concurrent.CountDownLatch(1)
        stalled.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200,10); exchange.responseBody.write(1); exchange.responseBody.flush()
            release.await(2,java.util.concurrent.TimeUnit.SECONDS); exchange.close()
        }
        stalled.start()
        val source=object: com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage {
            override val available=true
            override fun put(key:String,file:Path,contentType:String) {}
            override fun delete(key:String) {}
            override fun readUrl(key:String,ttl:java.time.Duration)="http://127.0.0.1:${stalled.address.port}/"
        }
        try {
            val error=assertFailsWith<SheetSourceStorageException> { source.download("key",dir.resolve("download"),java.time.Duration.ofMillis(200)) }
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is java.util.concurrent.TimeoutException || it is java.net.http.HttpTimeoutException })
        } finally { release.countDown(); stalled.stop(0) }
    }
}
