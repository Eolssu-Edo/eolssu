package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.sheet.application.CopiedScore
import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.NewUserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetPage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class UserSheetControllerTests {
    private val updatedAt = Instant.parse("2026-11-02T05:00:00Z")
    private val stored = mutableMapOf<Long, UserSheet>()
    private val pages = mutableListOf<Triple<String?, Int, Int>>()
    private val repository = object : UserSheetRepository {
        override fun findByIdempotencyKey(userId: Long, idempotencyKey: String): UserSheet? = null
        override fun findJangdan(jangdanId: Long) = SheetJangdan(jangdanId, "JANGDAN_BEAT").takeIf { jangdanId == 3L }
        override fun findSongSource(songId: Long) = null
        override fun find(sheetId: Long, userId: Long) = stored[sheetId]?.takeIf { it.userId == userId }
        override fun insert(sheet: NewUserSheet): Long {
            stored[701] = sheet(701, sheet.title, null)
            return 701
        }

        override fun page(userId: Long, kind: String?, page: Int, size: Int): SheetPage {
            pages += Triple(kind, page, size)
            return SheetPage(stored.values.filter { it.userId == userId }, 21)
        }
    }
    private val scores = object : SheetScores {
        override fun copy(scoreJson: String, scoreVersion: Int): CopiedScore? = null
        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) = emptyList<Nothing>()
    }
    private val mvc = MockMvcBuilders.standaloneSetup(
        UserSheetController(
            CreateUserSheet(repository, scores, Clock.fixed(updatedAt, ZoneOffset.UTC)),
            GetUserSheets(repository),
            JsonMapper.builder().build(),
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    private fun sheet(id: Long, title: String, json: String?, userId: Long = 1) = UserSheet(
        id, userId, title, "JEONGGANBO", "MANUAL", SheetStatus.NEEDS_REVIEW, 3, "타령", 60, "JANGDAN_BEAT", 8, 1, json, 0, 0, "h", updatedAt,
    )

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private val blankBody = """{"title":"새 악보 1","startType":"BLANK","jangdanId":"3","bpm":60,"columnCount":8,"copyFromSongId":null,"copyFromSheetId":null,"pdfSheetId":null}"""

    @Test
    fun `creates a blank sheet in the API spec shape`() {
        mvc.perform(post("/api/users/me/sheets").header("Idempotency-Key", "create-sheet-1").contentType(MediaType.APPLICATION_JSON).content(blankBody))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.httpStatus").value("CREATED"))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.result.sheetId").value("701"))
            .andExpect(jsonPath("$.result.kind").value("JEONGGANBO"))
            .andExpect(jsonPath("$.result.status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.result.scoreVersion").value("1"))
            .andExpect(jsonPath("$.result.events").isEmpty)
            .andExpect(jsonPath("$.result.updatedAt").value("2026-11-02T05:00:00Z"))
    }

    @Test
    fun `create rejects coerced types, a missing key, PDF start and unknown jangdan`() {
        val invalid = listOf(
            """{"title":"a","startType":"BLANK","jangdanId":3,"bpm":60,"columnCount":8}""",
            """{"title":"a","startType":"BLANK","jangdanId":"3","bpm":"60","columnCount":8}""",
            """{"title":"a","startType":"BLANK","jangdanId":"3","bpm":60,"columnCount":8.5}""",
            """{"title":1,"startType":"BLANK","jangdanId":"3","bpm":60,"columnCount":8}""",
            """{"title":"a","startType":0,"jangdanId":"3","bpm":60,"columnCount":8}""",
            """{"title":"a","startType":"blank","jangdanId":"3","bpm":60,"columnCount":8}""",
            """{"title":"a","startType":"BLANK","jangdanId":"03","bpm":60,"columnCount":8}""",
            """{"startType":"BLANK","jangdanId":"3","bpm":60,"columnCount":8}""",
            """{"title":"a","startType":"PDF","jangdanId":"3","bpm":60,"columnCount":8,"pdfSheetId":"5"}""",
        )
        invalid.forEachIndexed { index, body ->
            mvc.perform(post("/api/users/me/sheets").header("Idempotency-Key", "k$index").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        mvc.perform(post("/api/users/me/sheets").contentType(MediaType.APPLICATION_JSON).content(blankBody)).andExpect(status().isBadRequest)
        mvc.perform(post("/api/users/me/sheets").header("Idempotency-Key", "한글").contentType(MediaType.APPLICATION_JSON).content(blankBody))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/api/users/me/sheets").header("Idempotency-Key", "j").contentType(MediaType.APPLICATION_JSON).content(blankBody.replace("\"3\"", "\"4\"")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        assertEquals(emptyMap(), stored)
    }

    @Test
    fun `score answers the owner's sheet with its raw common score`() {
        stored[702] = sheet(702, "아리랑 연습", """{"schemaVersion":1,"events":[]}""")
        stored[703] = sheet(703, "빈 악보", null)
        stored[704] = sheet(704, "남의 악보", null, userId = 2)

        mvc.perform(get("/api/users/me/sheets/702/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("공통 악보 조회 성공"))
            .andExpect(jsonPath("$.result.sheetId").value("702"))
            .andExpect(jsonPath("$.result.title").value("아리랑 연습"))
            .andExpect(jsonPath("$.result.jangdanId").value("3"))
            .andExpect(jsonPath("$.result.bpm").value(60))
            .andExpect(jsonPath("$.result.bpmUnit").value("JANGDAN_BEAT"))
            .andExpect(jsonPath("$.result.columnCount").value(8))
            .andExpect(jsonPath("$.result.scoreVersion").value("1"))
            .andExpect(jsonPath("$.result.reviewStatus").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.result.scoreData.schemaVersion").value(1))
            .andExpect(jsonPath("$.result.source").doesNotExist())
            .andExpect(jsonPath("$.result.issues").isEmpty)
        mvc.perform(get("/api/users/me/sheets/703/score")).andExpect(status().isOk).andExpect(jsonPath("$.result.scoreData").doesNotExist())
        for (id in listOf("704", "999")) {
            mvc.perform(get("/api/users/me/sheets/$id/score")).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        }
        for (id in listOf("0", "-1", "07", "abc", "9999999999999999999")) {
            mvc.perform(get("/api/users/me/sheets/$id/score")).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `list pages the owner's sheets and validates its query`() {
        stored[702] = sheet(702, "아리랑 연습", null)

        mvc.perform(get("/api/users/me/sheets").param("kind", "JEONGGANBO").param("page", "1").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items[0].sheetId").value("702"))
            .andExpect(jsonPath("$.result.items[0].kind").value("JEONGGANBO"))
            .andExpect(jsonPath("$.result.items[0].title").value("아리랑 연습"))
            .andExpect(jsonPath("$.result.items[0].status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.result.items[0].jangdanId").value("3"))
            .andExpect(jsonPath("$.result.items[0].jangdanName").value("타령"))
            .andExpect(jsonPath("$.result.items[0].source").value("MANUAL"))
            .andExpect(jsonPath("$.result.items[0].pendingCellCount").value(0))
            .andExpect(jsonPath("$.result.items[0].updatedAt").value("2026-11-02T05:00:00Z"))
            .andExpect(jsonPath("$.result.page").value(1))
            .andExpect(jsonPath("$.result.size").value(10))
            .andExpect(jsonPath("$.result.totalElements").value(21))
            .andExpect(jsonPath("$.result.totalPages").value(3))
        mvc.perform(get("/api/users/me/sheets")).andExpect(status().isOk)
        assertEquals(listOf(Triple("JEONGGANBO", 1, 10), Triple(null, 0, 20)), pages)

        for ((name, value) in listOf("kind" to "VIEW_ONLY", "page" to "-1", "size" to "0", "size" to "101", "page" to "x")) {
            mvc.perform(get("/api/users/me/sheets").param(name, value)).andExpect(status().isBadRequest)
        }
    }
}
