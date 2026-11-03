package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper

class SheetBodyLimitFilterTests {
    private val json = JsonMapper.builder().build()
    private val filter = SheetBodyLimitFilter(8, ErrorResponseWriter(json))

    // MockHttpServletRequest는 본문 길이를 그대로 알려 준다. 길이 없이 오는 chunked 본문은 길이를 -1로 돌려 흉내 낸다.
    private fun put(uri: String, body: ByteArray, chunked: Boolean = false): MockHttpServletRequest {
        val request = if (chunked) {
            object : MockHttpServletRequest("PUT", uri) {
                override fun getContentLength() = -1
                override fun getContentLengthLong() = -1L
            }
        } else {
            MockHttpServletRequest("PUT", uri)
        }
        request.setContent(body)
        return request
    }

    @Test
    fun `a body within the limit reaches the controller unchanged`() {
        val chain = MockFilterChain()
        filter.doFilter(put("/api/users/me/sheets/701/score", "12345678".toByteArray()), MockHttpServletResponse(), chain)

        assertEquals("12345678", chain.request!!.inputStream.readAllBytes().decodeToString())
    }

    @Test
    fun `a declared or streamed body over the limit is 413 before it is read`() {
        for ((uri, chunked) in listOf("/api/users/me/sheets/701/score" to false, "/api/users/me/sheets/701/score/draft" to true)) {
            val chain = MockFilterChain()
            val response = MockHttpServletResponse()
            filter.doFilter(put(uri, "123456789".toByteArray(), chunked), response, chain)

            assertEquals(413, response.status, uri)
            assertEquals("PAYLOAD_TOO_LARGE", json.readTree(response.contentAsString).path("result").path("errorCode").asString())
            assertNull(chain.request)
        }
    }

    @Test
    fun `a declared length over the limit is rejected without reading the body`() {
        val request = object : MockHttpServletRequest("PUT", "/api/users/me/sheets/701/score") {
            override fun getContentLengthLong() = 9L
            override fun getInputStream() = error("the body must not be read")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(413, response.status)
    }

    @Test
    fun `an encoded path that Spring MVC decodes to a score route is limited too`() {
        for (uri in listOf("/api/users/me/sheets/701/%73core", "/api/users/me/sheets/701/score/%64raft")) {
            val response = MockHttpServletResponse()
            filter.doFilter(put(uri, "123456789".toByteArray()), response, MockFilterChain())
            assertEquals(413, response.status, uri)
        }
    }

    @Test
    fun `the buffered body can also be read as characters`() {
        val chain = MockFilterChain()
        filter.doFilter(put("/api/users/me/sheets/701/score", "{\"a\":1}".toByteArray()).apply { characterEncoding = "UTF-8" }, MockHttpServletResponse(), chain)

        assertEquals("{\"a\":1}", (chain.request as jakarta.servlet.http.HttpServletRequest).reader.readText())
    }

    @Test
    fun `other sheet routes and methods are not limited`() {
        for (request in listOf(
            MockHttpServletRequest("POST", "/api/users/me/sheets").apply { setContent(ByteArray(20)) },
            MockHttpServletRequest("GET", "/api/users/me/sheets/701/score"),
            MockHttpServletRequest("PUT", "/api/users/me/sheets/701/other").apply { setContent(ByteArray(20)) },
        )) {
            val chain = MockFilterChain()
            filter.doFilter(request, MockHttpServletResponse(), chain)
            assertEquals(request, chain.request)
        }
    }
}
