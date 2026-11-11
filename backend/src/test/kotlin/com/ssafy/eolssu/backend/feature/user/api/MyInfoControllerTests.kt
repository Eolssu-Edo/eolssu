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
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
    fun `returns my info with linked providers and password presence`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
        repository.providers[7] = listOf(LinkedProvider("KAKAO", Instant.parse("2026-10-28T00:00:00Z")))

        mvc.perform(get("/api/users/me"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"message":"내 정보·학습 설정 조회 성공","result":{"userId":"7","email":"me@example.test","hasPassword":true,"nickname":"연습자",
                    "instrumentId":"3","notationMode":"HANJA","profileImage":"HELLO","linkedProviders":[{"provider":"KAKAO","linkedAt":"2026-10-28T00:00:00Z"}]}}""",
                    true,
                ),
            )
    }

    @Test
    fun `social members return a boolean false with or without an email`() {
        for (email in listOf("verified@example.test", null)) {
            repository.members[7] = MyInfo(7, email, "소셜 회원", 3, "HANJA", false)
            repository.providers[7] = listOf(LinkedProvider("KAKAO", Instant.parse("2026-10-28T00:00:00Z")))
            mvc.perform(get("/api/users/me"))
                .andExpect(status().isOk)
                .andExpect(content().json("""{"result":{"hasPassword":false}}"""))
                .andExpect { result ->
                    kotlin.test.assertFalse(result.response.contentAsString.contains("passwordHash"))
                    kotlin.test.assertFalse(result.response.contentAsString.contains("password_hash"))
                }
        }
    }

    @Test
    fun `no linked provider is an empty array`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANGUL", true)

        mvc.perform(get("/api/users/me"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"result":{"linkedProviders":[]}}"""))
    }

    @Test
    fun `a member that is not active is unauthenticated`() {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"isSuccess":false,"code":401,"result":{"errorCode":"UNAUTHENTICATED"}}"""))
    }

    @Test
    fun `nickname is changed and my info is returned`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)

        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"새 닉네임"}"""))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"message":"회원정보 수정 성공",
                    "result":{"userId":"7","nickname":"새 닉네임","instrumentId":"3","notationMode":"HANJA"}}""",
                    true,
                ),
            )
        kotlin.test.assertEquals("새 닉네임", repository.members.getValue(7).nickname)
    }

    @Test
    fun `blank too long or missing nickname is an invalid request`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
        for (body in listOf("""{"nickname":" "}""", """{"nickname":"${"가".repeat(11)}"}""", "{}")) {
            mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(content().json("""{"result":{"errorCode":"INVALID_REQUEST"}}"""))
        }
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"${"가".repeat(10)}"}"""))
            .andExpect(status().isOk)
        kotlin.test.assertEquals("가".repeat(10), repository.members.getValue(7).nickname)
    }

    @Test
    fun `nickname of a member that is not active is unauthenticated`() {
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"새 닉네임"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"result":{"errorCode":"UNAUTHENTICATED"}}"""))
    }

    @Test
    fun `profile image is changed to one of the twelve tiger poses`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)

        mvc.perform(patch("/api/users/me/profile-image").contentType(MediaType.APPLICATION_JSON).content("""{"profileImage":"DANSO"}"""))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"message":"프로필 그림 저장 성공","result":{"userId":"7","profileImage":"DANSO"}}""",
                    true,
                ),
            )
        kotlin.test.assertEquals("DANSO", repository.members.getValue(7).profileImage)
        mvc.perform(get("/api/users/me"))
            .andExpect(content().json("""{"result":{"profileImage":"DANSO"}}"""))
    }

    @Test
    fun `unknown blank or missing profile image is an invalid request`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
        for (body in listOf("{}", """{"profileImage":""}""", """{"profileImage":"hello"}""", """{"profileImage":"PURPLE"}""", """{"profileImage":null}""")) {
            mvc.perform(patch("/api/users/me/profile-image").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(content().json("""{"result":{"errorCode":"INVALID_REQUEST"}}"""))
        }
        kotlin.test.assertEquals("HELLO", repository.members.getValue(7).profileImage)
    }

    @Test
    fun `profile image of a member that is not active is unauthenticated`() {
        mvc.perform(patch("/api/users/me/profile-image").contentType(MediaType.APPLICATION_JSON).content("""{"profileImage":"DONE"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"result":{"errorCode":"UNAUTHENTICATED"}}"""))
    }

    @Test
    fun `preferences change only the given fields`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
        repository.activeInstruments += setOf(3L, 4L)

        mvc.perform(patch("/api/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"notationMode":"HANGUL"}"""))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"message":"선택 악기·기본 악보 표기 저장 성공",
                    "result":{"userId":"7","instrumentId":"3","notationMode":"HANGUL"}}""",
                    true,
                ),
            )
        mvc.perform(patch("/api/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"instrumentId":"4"}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"result":{"userId":"7","instrumentId":"4","notationMode":"HANGUL"}}"""))
    }

    @Test
    fun `an instrument that is missing or inactive cannot be chosen`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)

        mvc.perform(patch("/api/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"instrumentId":"9","notationMode":"HANGUL"}"""))
            .andExpect(status().isUnprocessableContent)
            .andExpect(content().json("""{"code":422,"result":{"errorCode":"UNPROCESSABLE_INPUT"}}"""))
        kotlin.test.assertEquals("HANJA", repository.members.getValue(7).notationMode)
    }

    @Test
    fun `empty or malformed preferences are an invalid request`() {
        repository.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
        for (body in listOf("{}", """{"notationMode":"KOREAN"}""", """{"instrumentId":"0"}""", """{"instrumentId":"abc"}""")) {
            mvc.perform(patch("/api/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(content().json("""{"result":{"errorCode":"INVALID_REQUEST"}}"""))
        }
    }

    @Test
    fun `preferences of a member that is not active are unauthenticated`() {
        mvc.perform(patch("/api/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content("""{"notationMode":"HANGUL"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"result":{"errorCode":"UNAUTHENTICATED"}}"""))
    }
}

internal class MemoryMyInfoRepository : MyInfoRepository {
    val members = mutableMapOf<Long, MyInfo>()
    val providers = mutableMapOf<Long, List<LinkedProvider>>()
    val activeInstruments = mutableSetOf<Long>()

    override fun findActive(userId: Long) = members[userId]
    override fun linkedProviders(userId: Long) = providers[userId].orEmpty()
    override fun updateNickname(userId: Long, nickname: String): Boolean {
        val member = members[userId] ?: return false
        members[userId] = MyInfo(member.userId, member.email, nickname, member.instrumentId, member.notationMode, member.hasPassword, member.profileImage)
        return true
    }
    override fun activeInstrumentExists(instrumentId: Long) = instrumentId in activeInstruments
    override fun updatePreferences(userId: Long, instrumentId: Long?, notationMode: String?): Boolean {
        val member = members[userId] ?: return false
        members[userId] = MyInfo(member.userId, member.email, member.nickname, instrumentId ?: member.instrumentId, notationMode ?: member.notationMode, member.hasPassword, member.profileImage)
        return true
    }
    override fun updateProfileImage(userId: Long, profileImage: String): Boolean {
        val member = members[userId] ?: return false
        members[userId] = MyInfo(member.userId, member.email, member.nickname, member.instrumentId, member.notationMode, member.hasPassword, profileImage)
        return true
    }
}
