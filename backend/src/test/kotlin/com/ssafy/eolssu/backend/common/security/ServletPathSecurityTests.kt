package com.ssafy.eolssu.backend.common.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 서블릿 경로를 바꿔도 업로드 판정이 로그인 규칙(SecurityConfig)과 같은 경로를 기준으로 하는지 확인한다. */
@SpringBootTest(properties = ["spring.mvc.servlet.path=/app"])
@AutoConfigureMockMvc
class ServletPathSecurityTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `follows the servlet path when accepting the recording upload`() {
        val multipart = MediaType.parseMediaType("multipart/form-data; boundary=test-boundary")
        mockMvc.perform(post("/app/api/practice-sessions/1/recording").servletPath("/app").contentType(multipart).content("--test-boundary--"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("/app/api/auth/sign-in").servletPath("/app").contentType(multipart).content("--test-boundary--"))
            .andExpect(status().isUnsupportedMediaType)
    }
}
