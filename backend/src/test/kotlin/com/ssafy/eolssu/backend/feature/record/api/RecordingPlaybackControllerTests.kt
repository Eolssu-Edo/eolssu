package com.ssafy.eolssu.backend.feature.record.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.record.application.GetRecordingPlayback
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.Clock
import java.time.Duration
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class RecordingPlaybackControllerTests {
    private var storageFails = false
    private val records = object : PracticeRecords {
        override fun lockStatus(sessionId: Long, userId: Long): String? = error("not used")

        override fun findSession(sessionId: Long, userId: Long): RecordSession? = error("not used")

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) = error("not used")

        override fun songSummaries(userId: Long): List<SongRecordSummary> = error("not used")

        override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> = error("not used")

        override fun findRecording(sessionId: Long, userId: Long) =
            RecordingFile("recordings/501/a.m4a", null).takeIf { sessionId == 501L && userId == 1L }
    }
    private val mvc = MockMvcBuilders.standaloneSetup(
        RecordingPlaybackController(
            GetRecordingPlayback(
                records,
                { key, _ -> if (storageFails) throw IllegalStateException("storage down") else "https://storage.test/$key?X-Amz-Signature=s" },
                Duration.ofMinutes(15),
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
    fun `answers the signed address with its expiry and audio type`() {
        mvc.perform(get("/api/practice-sessions/501/recording")).andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.result.sessionId").value("501"))
            .andExpect(jsonPath("$.result.playbackUrl").value("https://storage.test/recordings/501/a.m4a?X-Amz-Signature=s"))
            .andExpect(jsonPath("$.result.urlExpiresAt").value("2026-11-02T04:15:00Z"))
            .andExpect(jsonPath("$.result.durationMs").isEmpty)
            .andExpect(jsonPath("$.result.contentType").value("audio/mp4"))
    }

    @Test
    fun `hides recordings the member cannot play and reports storage outages`() {
        mvc.perform(get("/api/practice-sessions/999/recording")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        storageFails = true
        mvc.perform(get("/api/practice-sessions/501/recording")).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.result.errorCode").value("SERVICE_UNAVAILABLE"))
    }
}
