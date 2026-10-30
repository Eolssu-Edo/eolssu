package com.ssafy.eolssu.backend.common.security

import jakarta.servlet.http.Cookie
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request

/**
 * API 명세의 Request Header(공개 / 회원 인증 필수) 표. 업무 컨트롤러가 아직 없어 공개 경로는 404, 로그인이 필요한 경로는 401이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAccessRulesTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `opens only the public APIs listed in the spec`() {
        val failures = (PUBLIC.map { it to 404 } + PROTECTED.map { it to 401 } + HEALTH).flatMap { (endpoint, expected) ->
            listOf(false, true).mapNotNull { withInvalidCookie ->
                val (method, path) = endpoint
                val builder = request(HttpMethod.valueOf(method), path)
                if (method != "GET") builder.contentType(MediaType.APPLICATION_JSON).content("{}")
                if (withInvalidCookie) builder.cookie(Cookie("access_token", "invalid.jwt"))
                val actual = mockMvc.perform(builder).andReturn().response.status
                if (actual == expected) null else "$method $path: expected $expected, was $actual (invalid cookie=$withInvalidCookie)"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        val HEALTH = listOf(("GET" to "/api/actuator/health") to 200)

        val PUBLIC = listOf(
            "POST" to "/api/auth/sign-in", "POST" to "/api/auth/sign-up", "POST" to "/api/auth/refresh",
            "POST" to "/api/auth/password-reset", "POST" to "/api/auth/oauth/kakao", "POST" to "/api/auth/oauth/merge",
            "POST" to "/api/auth/oauth/kakao/start",
            "POST" to "/api/auth/email-verifications", "POST" to "/api/auth/email-verifications/301/confirm",
            "GET" to "/api/terms", "GET" to "/api/songs", "GET" to "/api/songs/1", "GET" to "/api/songs/1/score",
            "GET" to "/api/songs/1/model-audio", "GET" to "/api/instruments", "GET" to "/api/instruments/1/fingerings",
            "GET" to "/api/pitches", "GET" to "/api/jangdans", "GET" to "/api/jangdans/1", "GET" to "/api/notation-symbols",
        )

        val PROTECTED = listOf(
            "POST" to "/api/auth/sign-out", "GET" to "/api/users/me", "PATCH" to "/api/users/me", "DELETE" to "/api/users/me",
            "PATCH" to "/api/users/me/preferences", "PATCH" to "/api/users/me/password",
            "POST" to "/api/users/me/social-accounts/kakao", "DELETE" to "/api/users/me/social-accounts/kakao",
            "POST" to "/api/users/me/social-accounts/kakao/start",
            "POST" to "/api/practice-sessions", "GET" to "/api/practice-sessions/1", "POST" to "/api/practice-sessions/1/retry",
            "PUT" to "/api/practice-sessions/1/record", "GET" to "/api/practice-sessions/1/evaluation",
            "GET" to "/api/users/me/practice-records", "GET" to "/api/missions", "GET" to "/api/users/me/growth/summary",
            "POST" to "/api/users/me/score-views",
            // 공개 경로 아래에 나중에 생길 수 있는 다른 경로는 공개하지 않는다.
            "GET" to "/api/songs/1/members", "GET" to "/api/instruments/1/notes", "GET" to "/api/jangdans/1/members",
            "POST" to "/api/auth/oauth/kakao/extra", "POST" to "/api/auth/email-verifications/301/other",
            "POST" to "/api/songs", "POST" to "/api/terms",
        )
    }
}
