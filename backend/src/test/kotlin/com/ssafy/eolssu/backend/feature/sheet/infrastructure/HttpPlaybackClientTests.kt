package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import tools.jackson.databind.json.JsonMapper

class HttpPlaybackClientTests {
    private val json=JsonMapper.builder().build()
    private val m4a=byteArrayOf(0,0,0,20)+"ftypM4A ".toByteArray()+ByteArray(8)
    private val midi="MThd".toByteArray()+ByteArray(10)
    private fun bundle(extra: String?=null,audio: ByteArray=m4a,performance: String?=null): ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val files=linkedMapOf("audio.m4a" to audio,"score.mid" to midi,"result.json" to """{"durationMs":2250}""".toByteArray())
            if(extra!=null) files[extra]=byteArrayOf(1)
            if(performance!=null) files["performance.common.json"]=performance.toByteArray()
            files.forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
        return output.toByteArray()
    }
    @Test fun `actual HTTP consumes only a bounded valid generated pair`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var request=""
        server.createContext("/v1/render") { exchange ->
            request=exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val bytes=bundle()
            exchange.responseHeaders.add("Content-Type","application/zip")
            exchange.sendResponseHeaders(200,bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result=HttpPlaybackClient("http://127.0.0.1:${server.address.port}",Duration.ofSeconds(2),json).generate("{}")
            assertContentEquals(m4a,result.m4a); assertContentEquals(midi,result.midi)
            assertEquals(2250,result.durationMs); assertEquals("{}",request)
        } finally { server.stop(0) }
    }
    @Test fun `actual HTTP binds the optional performance to the requested score and returned audio`() {
        val score = """{"score":{"scoreVersion":2,"sourceId":"sheet-42","instrument":"DANSO"},"music":{"bpmUnit":"QUARTER"}}"""
        val body = """{"score":$score,"instrument":"sogeum","bpm":60,"withJangdan":false}"""
        val sourceHash = MessageDigest.getInstance("SHA-256").digest(score.toByteArray()).toHexString()
        val audioHash = MessageDigest.getInstance("SHA-256").digest(m4a).toHexString()
        val performance = """{"schema":"common-score-performance/v1","policy":"educational-preview/v1","sourceSha256":"$sourceHash","scoreVersion":2,"sourceId":"sheet-42","instrument":"DANSO","bpm":60,"bpmUnit":"QUARTER","audioSha256":"$audioHash","durationMs":1700,"segments":[{}]}"""
        var timeline: String? = performance
        var requestedHash: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/render") { exchange ->
            requestedHash = exchange.requestHeaders.getFirst("X-Eolssu-Performance-Source-Sha256")
            val bytes = bundle(performance = timeline)
            exchange.responseHeaders.add("Content-Type", "application/zip")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = HttpPlaybackClient("http://127.0.0.1:${server.address.port}", Duration.ofSeconds(2), json)
            assertEquals(json.readTree(performance), client.generate(body).performance)
            assertEquals(sourceHash, requestedHash)
            timeline = performance.replace(audioHash, "0".repeat(64))
            assertNull(client.generate(body).performance)
            timeline = null
            assertNull(client.generate(body).performance)
        } finally { server.stop(0) }
    }
    @Test fun `a generator already rendering is reported as busy rather than as a failure`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/render") { exchange ->
            exchange.requestBody.readAllBytes()
            val bytes="""{"errorCode":"AUDIO_IN_PROGRESS"}""".toByteArray()
            exchange.sendResponseHeaders(409,bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client=HttpPlaybackClient("http://127.0.0.1:${server.address.port}",Duration.ofSeconds(2),json)
            assertEquals("BUSY",assertFailsWith<PlaybackFailure> { client.generate("{}") }.reason)
        } finally { server.stop(0) }
    }
    @Test fun `zip paths and decompressed oversize audio never escape the transport boundary`() {
        val client=HttpPlaybackClient("",Duration.ofSeconds(1),json)
        assertEquals("SERVER_ERROR",assertFailsWith<PlaybackFailure> { client.decode(bundle("../private")) }.reason)
        assertEquals("TOO_LARGE",assertFailsWith<PlaybackFailure> { client.decode(bundle(audio=ByteArray(15*1024*1024+1))) }.reason)
        assertEquals("SERVER_ERROR",assertFailsWith<PlaybackFailure> { client.decode(bundle(audio=ByteArray(30))) }.reason)
    }
    @Test fun `generation deadline includes a stalled response body`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val release=java.util.concurrent.CountDownLatch(1)
        server.createContext("/v1/render") { exchange ->
            exchange.responseHeaders.add("Content-Type","application/zip")
            exchange.sendResponseHeaders(200,100)
            exchange.responseBody.write(1); exchange.responseBody.flush()
            release.await(2,java.util.concurrent.TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val client=HttpPlaybackClient("http://127.0.0.1:${server.address.port}",Duration.ofMillis(150),json)
            assertEquals("TIMEOUT",assertFailsWith<PlaybackFailure> { client.generate("{}") }.reason)
        } finally { release.countDown(); server.stop(0) }
    }
}
