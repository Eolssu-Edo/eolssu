package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.sheet.application.EditUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SheetSourceCleaner
import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraft
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraftUpdate
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetScoreUpdate
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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class UserSheetControllerTests {
    private val updatedAt = Instant.parse("2026-11-02T05:00:00Z")
    private val stored = mutableMapOf<Long, UserSheet>()
    private val pages = mutableListOf<Triple<String?, Int, Int>>()
    private val repository = object : UserSheetRepository {
        override fun findJangdan(jangdanId: Long) = SheetJangdan(jangdanId, "JANGDAN_BEAT", "세마치").takeIf { jangdanId == 3L }
        override fun find(sheetId: Long, userId: Long) = stored[sheetId]?.takeIf { it.userId == userId }

        override fun page(userId: Long, kind: String?, page: Int, size: Int): SheetPage {
            pages += Triple(kind, page, size)
            return SheetPage(stored.values.filter { it.userId == userId }, 21)
        }

        override fun updateScore(update: SheetScoreUpdate): Boolean = error("not used")
        override fun updateDraft(update: SheetDraftUpdate): Boolean = error("not used")
        override fun delete(sheetId: Long, userId: Long): Boolean = error("not used")
    }
    private val scores = object : SheetScores {
        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) = emptyList<Nothing>()
        override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore = error("not used")
        override fun unfilledCellCount(scoreJson: String): Int? = error("not used")
    }
    private val mvc = MockMvcBuilders.standaloneSetup(
        UserSheetController(
            GetUserSheets(repository, scores),
            EditUserSheet(repository, scores, Clock.fixed(updatedAt, ZoneOffset.UTC)),
            JsonMapper.builder().build(),
            // 원본 파일이 없는 MANUAL 악보만 다룬다. 원본 삭제는 SheetImportsTests·DB 테스트가 본다.
            SheetSourceCleaner { _, _ -> true },
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    private fun sheet(id: Long, title: String, json: String?, userId: Long = 1) = UserSheet(
        id, userId, title, "JEONGGANBO", "MANUAL", SheetStatus.NEEDS_REVIEW, 3, "타령", 60, "JANGDAN_BEAT", 8, 1, json, 0, 0, updatedAt,
    )

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

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
    fun `list returns the owner's effective score instrument and keeps blank sheets`() {
        val danso = """{"score":{"instrument":"DANSO"}}"""
        stored[702] = sheet(702, "단소 악보", danso)
        stored[703] = sheet(703, "소금 초안", danso).copy(
            draft = SheetDraft("""{"score":{"score":{"instrument":"SOGEUM"}}}""", 1, updatedAt),
        )
        stored[704] = sheet(704, "빈 악보", null)
        stored[705] = sheet(705, "남의 단소 악보", danso, userId = 2)
        stored[706] = sheet(706, "악기 없는 초안", danso).copy(
            draft = SheetDraft("""{"score":{"score":{"instrument":""}}}""", 1, updatedAt),
        )

        mvc.perform(get("/api/users/me/sheets")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items.length()").value(4))
            .andExpect(jsonPath("$.result.items[0].instrumentCode").value("DANSO"))
            .andExpect(jsonPath("$.result.items[1].instrumentCode").value("SOGEUM"))
            .andExpect(jsonPath("$.result.items[1].hasDraft").value(true))
            .andExpect(jsonPath("$.result.items[2].instrumentCode").doesNotExist())
            .andExpect(jsonPath("$.result.items[3].instrumentCode").doesNotExist())
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
