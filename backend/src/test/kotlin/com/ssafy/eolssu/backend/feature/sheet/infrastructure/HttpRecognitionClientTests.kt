package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class HttpRecognitionClientTests {
    private val json=JsonMapper.builder().build()
    @Test fun `sends contract JSON and returns engine failures without retrying HTTP`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var calls=0
        server.createContext("/v1/recognitions") { request ->
            calls++
            assertEquals("POST",request.requestMethod)
            val body=json.readTree(request.requestBody.readBytes())
            assertEquals("sheet-712-1",body.path("conversionId").asString())
            assertEquals("STAFF",body.path("notation").asString())
            val response="""{"conversionId":"sheet-712-1","status":"FAILED","failReason":"ENGINE_ERROR"}""".toByteArray()
            request.sendResponseHeaders(200,response.size.toLong()); request.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client=HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json)
            val result=client.recognize(json.readTree("""{"conversionId":"sheet-712-1","notation":"STAFF"}"""))
            assertEquals("ENGINE_ERROR",result.path("failReason").asString())
            assertEquals(1,calls)
        } finally { server.stop(0) }
    }
    @Test fun `wrong conversion identity is a server failure`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/recognitions") { request ->
            val response="""{"conversionId":"other","status":"DONE"}""".toByteArray()
            request.sendResponseHeaders(200,response.size.toLong()); request.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client=HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json)
            assertFailsWith<RecognitionFailure> { client.recognize(json.readTree("""{"conversionId":"sheet-712-1"}""")) }
        } finally { server.stop(0) }
    }
    @Test fun `timeout also covers a response body that stops after its headers`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val release=java.util.concurrent.CountDownLatch(1)
        server.createContext("/v1/recognitions") { request ->
            request.sendResponseHeaders(200,100)
            request.responseBody.write('{'.code); request.responseBody.flush()
            release.await(2,java.util.concurrent.TimeUnit.SECONDS)
            request.close()
        }
        server.start()
        try {
            val client=HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json,java.time.Duration.ofMillis(200))
            val error=assertFailsWith<RecognitionFailure> { client.recognize(json.readTree("""{"conversionId":"sheet-712-1"}""")) }
            assertEquals("TIMEOUT",error.reason)
        } finally { release.countDown(); server.stop(0) }
    }
}
