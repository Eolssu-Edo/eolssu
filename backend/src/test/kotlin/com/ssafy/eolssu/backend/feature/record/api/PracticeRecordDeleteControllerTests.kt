package com.ssafy.eolssu.backend.feature.record.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.record.UnusedPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.DeletePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.GetPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.domain.DeletableRecord
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PracticeRecordDeleteControllerTests {
    private var storageFails = false
    private val records = object : UnusedPracticeRecords() {
        override fun findDeletable(sessionId: Long, userId: Long) =
            DeletableRecord("recordings/501/a.m4a").takeIf { sessionId == 501L && userId == 1L }

        override fun markDeleted(sessionId: Long, userId: Long, deletedAt: LocalDateTime) = true
    }
    private val columns = ScoreColumns { _, _ -> null }
    private val mvc = MockMvcBuilders.standaloneSetup(
        PracticeRecordController(
            SavePracticeRecord(records, columns, Clock.systemUTC()),
            GetPracticeRecords(records, columns),
            DeletePracticeRecord(
                records,
                { if (storageFails) throw IllegalStateException("storage down") },
                Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneOffset.UTC),
            ),
        ),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `answers what was deleted and when`() {
        mvc.perform(delete("/api/practice-sessions/501/record")).andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.result.sessionId").value("501"))
            .andExpect(jsonPath("$.result.deleted").value(true))
            .andExpect(jsonPath("$.result.recordingDeleted").value(true))
            .andExpect(jsonPath("$.result.deletedAt").value("2026-11-02T04:00:00Z"))
    }

    @Test
    fun `hides other sessions and reports storage outages`() {
        mvc.perform(delete("/api/practice-sessions/999/record")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        storageFails = true
        mvc.perform(delete("/api/practice-sessions/501/record")).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.result.errorCode").value("SERVICE_UNAVAILABLE"))
    }
}
