package com.ssafy.eolssu.backend.feature.practice.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.practice.application.InMemoryRecordingStorage
import com.ssafy.eolssu.backend.feature.practice.application.InMemoryRecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.application.InMemoryRecordingUploadRepository.Session
import com.ssafy.eolssu.backend.feature.practice.application.UploadRecording
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbe
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeResult
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class RecordingUploadControllerTests {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val repository = InMemoryRecordingUploadRepository()
    private val storage = InMemoryRecordingStorage()
    private val probedFiles = mutableListOf<Path>()
    private var probeResult: AudioProbeResult? = AudioProbeResult(setOf("mov", "mp4", "m4a"), "aac", 44_100, 2, 3.0)
    private val validator = RecordingFileValidator(AudioProbe { file -> probedFiles.add(file); probeResult })
    private val upload = UploadRecording(repository, validator, storage, Clock.fixed(now, ZoneOffset.UTC), {})
    private val mockMvc = MockMvcBuilders.standaloneSetup(RecordingUploadController(upload))
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    // 단독 MockMvc에는 보안 필터가 없어 검증을 마친 토큰을 직접 넣는다. 인증·multipart 허용 경로는 보안 테스트가 맡는다.
    private fun signIn(userId: String) {
        val token = Jwt.withTokenValue("test").header("alg", "HS256").subject(userId).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(token)
    }

    private fun session(id: Long = 501, status: String = "RECORDING", failReason: String? = null, startedAt: LocalDateTime = LocalDateTime.parse("2026-10-28T02:59:00")) {
        repository.sessions[id] = Session(1, RecordingUploadTarget(status, failReason, startedAt))
    }

    private fun post(sessionId: Long = 501, bytes: ByteArray = byteArrayOf(1, 2, 3), part: String = "audio"): ResultActions {
        signIn("1")
        return mockMvc.perform(multipart("/api/practice-sessions/$sessionId/recording").file(MockMultipartFile(part, "practice.m4a", "audio/mp4", bytes)))
    }

    private fun ResultActions.andExpectError(status: Int, errorCode: String) = andExpect(status().`is`(status))
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(status))
        .andExpect(jsonPath("$.result.errorCode").value(errorCode))

    @Test
    fun `accepts the recording with 202 in the API spec shape`() {
        session()

        post()
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.httpStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value(202))
            .andExpect(jsonPath("$.result.sessionId").value("501"))
            .andExpect(jsonPath("$.result.status").value("PENDING"))
            .andExpect(jsonPath("$.result.evaluationId").isEmpty)
            .andExpect(jsonPath("$.result.pollAfterMs").value(2000))

        assertContentEquals(byteArrayOf(1, 2, 3), storage.objects.values.single())
        assertEquals(3, repository.sessions.getValue(501).recording?.metadata?.bytes)
        // 검사용 임시 파일은 요청이 끝나면 지운다.
        assertFalse(Files.exists(probedFiles.single()))
    }

    @Test
    fun `answers each rejection with its error code in the common error shape`() {
        session(id = 502, status = "DONE")
        session(id = 503, status = "FAILED", failReason = "UPLOAD_TIMEOUT")
        session()

        post(sessionId = 999).andExpectError(404, "SESSION_NOT_FOUND")
        post(sessionId = 503).andExpectError(410, "SESSION_EXPIRED")
        post(sessionId = 502).andExpectError(409, "SESSION_NOT_RECORDING")
        post(bytes = ByteArray(20 * 1024 * 1024 + 1)).andExpectError(413, "RECORDING_TOO_LARGE")
        probeResult = null
        post().andExpectError(415, "RECORDING_UNSUPPORTED_FORMAT")
        probeResult = AudioProbeResult(setOf("mp4"), "aac", 44_100, 2, null)
        post().andExpectError(422, "RECORDING_INVALID_DURATION")
        probeResult = AudioProbeResult(setOf("mp4"), "aac", 44_100, 2, 3.0)
        storage.failPuts = true
        post().andExpectError(503, "RECORDING_STORAGE_UNAVAILABLE")
        storage.failPuts = false
        repository.failUpdates = true
        post().andExpectError(500, "INTERNAL_SERVER_ERROR")

        assertEquals(emptyMap(), storage.objects)
        assertEquals("RECORDING", repository.sessions.getValue(501).target.status)
    }

    @Test
    fun `deletes the temporary file when the upload fails with an exception`() {
        session()
        repository.failUpdates = true

        post().andExpectError(500, "INTERNAL_SERVER_ERROR")

        assertFalse(Files.exists(probedFiles.single()))
    }

    @Test
    fun `answers 400 without the audio part`() {
        session()
        post(part = "file").andExpectError(400, "INVALID_REQUEST")
    }
}
