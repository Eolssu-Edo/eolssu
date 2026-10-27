package com.ssafy.eolssu.backend.common.security

import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 운영처럼 Nginx가 HTTPS를 끝내고 http로 넘기는 경우. Host에는 포트가 없고(proxy_set_header Host $host),
 * 개발 주소(SECURITY_ALLOWED_ORIGINS)는 비어 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxiedRequestSecurityTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `accepts a same site HTTPS request forwarded over HTTP`() {
        mockMvc.perform(jsonPost(host = "k15e102.p.ssafy.io", origin = "https://k15e102.p.ssafy.io"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }

    @Test
    fun `accepts the same host on a non default port when the proxy drops the port`() {
        mockMvc.perform(jsonPost(host = "localhost", origin = "http://localhost:8080"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `rejects other sites including look-alike hosts`() {
        for (origin in listOf("https://evil.example", "https://k15e102.p.ssafy.io.evil.example", "https://p.ssafy.io")) {
            mockMvc.perform(jsonPost(host = "k15e102.p.ssafy.io", origin = origin))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.result.errorCode").value("FORBIDDEN"))
        }
    }

    @Test
    fun `keeps CORS closed without configured development origins`() {
        val response = mockMvc.perform(
            options("/api/auth/sign-in").behindProxy("k15e102.p.ssafy.io")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"),
        ).andReturn().response

        assertNull(response.getHeader("Access-Control-Allow-Origin"))
    }

    private fun jsonPost(host: String, origin: String) = post("/api/auth/sign-in").behindProxy(host)
        .header("Origin", origin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")

    private fun MockHttpServletRequestBuilder.behindProxy(host: String) = header("Host", host).with { request ->
        request.scheme = "http"
        request.serverName = host
        request.serverPort = 80
        request
    }
}
