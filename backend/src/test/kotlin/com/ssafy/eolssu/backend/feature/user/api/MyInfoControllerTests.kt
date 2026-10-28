package com.ssafy.eolssu.backend.feature.user.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.user.application.MyInfoService
import com.ssafy.eolssu.backend.feature.user.domain.LinkedProvider
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MyInfoControllerTests {
    private val repository = MemoryMyInfoRepository()
    private val mvc = MockMvcBuilders
        .standaloneSetup(MyInfoController(MyInfoService(repository)))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    // 실제 토큰 검증은 보안 필터 검사(AccessTokenSecurityTests)가 맡는다. 여기서는 검증을 통과한 회원 7로 둔다.
    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `returns my info with linked providers and no email`() {
        repository.members[7] = MyInfo(7, "연습자", 3, "HANJA")
        repository.providers[7] = listOf(LinkedProvider("KAKAO", Instant.parse("2026-10-28T00:00:00Z")))

        mvc.perform(get("/api/users/me"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"message":"내 정보·학습 설정 조회 성공","result":{"userId":"7","nickname":"연습자",
                    "instrumentId":"3","notationMode":"HANJA","linkedProviders":[{"provider":"KAKAO","linkedAt":"2026-10-28T00:00:00Z"}]}}""",
                    true,
                ),
            )
    }

    @Test
    fun `no linked provider is an empty array`() {
        repository.members[7] = MyInfo(7, "연습자", 3, "HANGUL")

        mvc.perform(get("/api/users/me"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"result":{"linkedProviders":[]}}"""))
    }

    @Test
    fun `a member that is not active is not found`() {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isNotFound)
            .andExpect(content().json("""{"isSuccess":false,"code":404,"result":{"errorCode":"RESOURCE_NOT_FOUND"}}"""))
    }
}

internal class MemoryMyInfoRepository : MyInfoRepository {
    val members = mutableMapOf<Long, MyInfo>()
    val providers = mutableMapOf<Long, List<LinkedProvider>>()

    override fun findActive(userId: Long) = members[userId]
    override fun linkedProviders(userId: Long) = providers[userId].orEmpty()
}
