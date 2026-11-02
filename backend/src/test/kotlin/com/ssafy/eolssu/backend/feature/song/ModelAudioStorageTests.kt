package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioUnavailable
import com.ssafy.eolssu.backend.feature.song.infrastructure.storage.ModelPerformanceReader
import com.ssafy.eolssu.backend.feature.song.infrastructure.storage.StoredModelAudioFiles
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import kotlin.test.*
import org.junit.jupiter.api.Test

class ModelAudioStorageTests {
    private val key = "model-audio/drafts/${"1".repeat(64)}/cases/jeongak-daegeum-019-p1/jeongak-daegeum.m4a"
    private val audio = ModelAudio(19,1,key,96,"QUARTER",29550,0,"audio/mp4","2".repeat(64),
        key.substringBeforeLast('/')+"/performance.common.json","3".repeat(64))

    @Test fun `only bound model keys are signed and read using the configured endpoint`() {
        val signed = mutableListOf<Pair<String,Duration>>()
        val received = mutableListOf<URI>()
        val files = StoredModelAudioFiles(URI("https://storage.example.test"), { value,ttl ->
            signed += value to ttl
            "https://storage.example.test/bucket/$value?secret=signed"
        }, { received += it; "{}".encodeToByteArray() })
        assertTrue(files.playbackUrl(audio).contains(key))
        assertContentEquals("{}".encodeToByteArray(),files.performance(audio))
        assertEquals(listOf(key,audio.performanceObjectKey),signed.map { it.first })
        assertTrue(signed.all { it.second == Duration.ofMinutes(15) })
        assertEquals(1,received.size)
        for (candidate in listOf(audio.copy(objectKey="recordings/user.m4a"),
            audio.copy(performanceObjectKey=key.substringBeforeLast('/')+"/source.common.json"),
            audio.copy(sourceCommonSha256=null))) {
            assertFailsWith<ModelAudioUnavailable> { files.playbackUrl(candidate) }
        }
        assertEquals(2,signed.size)
    }

    @Test fun `unavailable signer wrong host and provider exceptions do not disclose addresses`() {
        val outputs = listOf("http://storage.example.test/bucket/audio", "https://other.example.test/file",
            "https://user:password@storage.example.test/file", "https://storage.example.test/file#secret")
        for (output in outputs) {
            val files = StoredModelAudioFiles(URI("https://storage.example.test"), { _,_ -> output }, { error("must not fetch") })
            val error=assertFailsWith<ModelAudioUnavailable> { files.performance(audio) }
            assertNull(error.cause)
            assertFalse(error.message!!.contains("secret"))
        }
        assertFailsWith<ModelAudioUnavailable> { StoredModelAudioFiles(null,null,{byteArrayOf()}).playbackUrl(audio) }
        val failed = StoredModelAudioFiles(URI("https://storage.example.test"), { _,_ -> error("secret signed URL") }, {byteArrayOf()})
        assertEquals("Model audio storage is unavailable",assertFailsWith<ModelAudioUnavailable> { failed.playbackUrl(audio) }.message)
    }

    @Test fun `performance HTTP reader limits bytes rejects redirects errors wrong types and empty responses`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var redirected = false
        server.createContext("/ok") { exchange ->
            val body="{\"schemaVersion\":1}".encodeToByteArray()
            exchange.responseHeaders.add("Content-Type","application/json; charset=utf-8")
            exchange.sendResponseHeaders(200,body.size.toLong());exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/large") { exchange ->
            val body=ByteArray(ModelPerformanceReader.MAX_BYTES+1)
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(200,body.size.toLong())
            runCatching { exchange.responseBody.use { it.write(body) } };exchange.close()
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location","/target");exchange.sendResponseHeaders(302,-1);exchange.close()
        }
        server.createContext("/target") { exchange -> redirected=true;exchange.sendResponseHeaders(200,-1);exchange.close() }
        server.createContext("/error") { exchange -> exchange.sendResponseHeaders(503,-1);exchange.close() }
        server.createContext("/empty") { exchange -> exchange.responseHeaders.add("Content-Type","application/json");exchange.sendResponseHeaders(200,-1);exchange.close() }
        server.createContext("/html") { exchange ->
            exchange.responseHeaders.add("Content-Type","text/html");exchange.sendResponseHeaders(200,2);exchange.responseBody.use { it.write("{}".encodeToByteArray()) }
        }
        server.start()
        try {
            ModelPerformanceReader().use { reader ->
                fun uri(path: String)=URI("http://127.0.0.1:${server.address.port}/$path")
                assertContentEquals("{\"schemaVersion\":1}".encodeToByteArray(),reader.read(uri("ok")))
                for(path in listOf("large","redirect","error","empty","html"))
                    assertFailsWith<ModelAudioUnavailable>(path) { reader.read(uri(path)) }
                assertFalse(redirected)
            }
        } finally { server.stop(0) }
    }
}
