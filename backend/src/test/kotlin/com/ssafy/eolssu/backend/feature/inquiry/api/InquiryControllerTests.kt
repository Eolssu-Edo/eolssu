package com.ssafy.eolssu.backend.feature.inquiry.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.inquiry.application.InquiryService
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMail
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailUnavailable
import com.ssafy.eolssu.backend.feature.user.api.MemoryMyInfoRepository
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class InquiryControllerTests {
    private val members = MemoryMyInfoRepository()
    private val sent = mutableListOf<InquiryMail>()
    private var failing = false
    private var now = Instant.parse("2026-11-22T03:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val service = InquiryService(members, { mail -> if (failing) throw InquiryMailUnavailable() else sent += mail }, clock)
    private val mvc = MockMvcBuilders
        .standaloneSetup(InquiryController(service))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    // 실제 토큰 검증은 보안 필터 검사가 맡는다. 여기서는 검증을 통과한 회원 7로 둔다.
    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
        members.members[7] = MyInfo(7, "me@example.test", "연습자", 3, "HANJA", true)
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun send(title: String = "녹음이 저장되지 않아요", content: String = "저장을 눌렀는데 기록에 없어요.") =
        mvc.perform(
            post("/api/users/me/inquiries").contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":${json(title)},"content":${json(content)}}"""),
        )

    private fun json(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

    @Test
    fun `sends trimmed title and content with the member who wrote it`() {
        send(title = "  녹음이 저장되지 않아요 ", content = "\n저장을 눌렀는데\n기록에 없어요.  ")
            .andExpect(status().isOk)
            .andExpect(content().json("""{"httpStatus":"OK","isSuccess":true,"code":200,"message":"문의 보내기 성공","result":{"sentAt":"2026-11-22T03:00:00Z"}}""", true))

        val mail = sent.single()
        assertEquals(7, mail.userId)
        assertEquals("연습자", mail.nickname)
        assertEquals("me@example.test", mail.email)
        assertEquals("녹음이 저장되지 않아요", mail.title)
        assertEquals("저장을 눌렀는데\n기록에 없어요.", mail.content)
    }

    @Test
    fun `social members without an email can still send`() {
        members.members[7] = MyInfo(7, null, "소셜 회원", 3, "HANJA", false)
        send().andExpect(status().isOk)
        assertNull(sent.single().email)
    }

    @Test
    fun `rejects blank, too long or multi-line titles and blank or too long content`() {
        for ((title, content) in listOf(
            " " to "내용", "제목" to " \n ", "가".repeat(101) to "내용", "제목" to "가".repeat(2001), "첫 줄\n둘째 줄" to "내용",
        )) {
            send(title, content).andExpect(status().isBadRequest).andExpect(content().json("""{"result":{"errorCode":"INVALID_REQUEST"}}"""))
        }
        mvc.perform(post("/api/users/me/inquiries").contentType(MediaType.APPLICATION_JSON).content("""{"title":"제목"}"""))
            .andExpect(status().isBadRequest)
        assertTrue(sent.isEmpty())

        send("가".repeat(100), "가".repeat(2000)).andExpect(status().isOk)
    }

    @Test
    fun `allows five inquiries per member each hour`() {
        repeat(5) { send().andExpect(status().isOk) }
        send().andExpect(status().isTooManyRequests).andExpect(content().json("""{"result":{"errorCode":"TOO_MANY_REQUESTS"}}"""))

        // 다른 회원은 따로 센다.
        members.members[8] = MyInfo(8, "other@example.test", "다른 회원", 3, "HANJA", true)
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("8").build())
        send().andExpect(status().isOk)

        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
        now = now.plus(Duration.ofMinutes(60))
        send().andExpect(status().isOk)
        assertEquals(7, sent.size)
    }

    @Test
    fun `delivery failure is 503 and does not use up the limit`() {
        failing = true
        repeat(6) { send().andExpect(status().isServiceUnavailable).andExpect(content().json("""{"result":{"errorCode":"SERVICE_UNAVAILABLE"}}""")) }
        failing = false
        repeat(5) { send().andExpect(status().isOk) }
    }

    @Test
    fun `inactive or missing members must sign in again`() {
        members.members.remove(7)
        send().andExpect(status().isUnauthorized).andExpect(content().json("""{"result":{"errorCode":"UNAUTHENTICATED"}}"""))
        assertTrue(sent.isEmpty())
    }
}
