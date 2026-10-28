package com.ssafy.eolssu.backend.feature.practice.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.practice.application.GetPracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/practice-sessions")
@Profile("db")
class PracticeSessionController(private val getPracticeSession: GetPracticeSession) {
    /** API 명세 `GET /practice-sessions/{sessionId}`(PRA-03 연습 세션·분석 상태 조회). 없거나 다른 회원의 세션은 404. */
    @GetMapping("/{sessionId}")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable @Positive sessionId: Long): ResponseEntity<*> {
        val detail = getPracticeSession.execute(requireNotNull(jwt.subject).toLong(), sessionId)
            ?: return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
        return ResponseEntity.ok(PracticeSessionResponse(result = PracticeSessionResult.of(detail)))
    }
}

data class PracticeSessionResponse(
    val httpStatus: String = "OK",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "연습 세션·분석 상태 조회 성공",
    val code: Int = 200,
    val result: PracticeSessionResult,
)

/** DB ID는 문자열, 시각은 UTC ISO8601(API 명세 공통 타입). */
data class PracticeSessionResult(
    val sessionId: String,
    val songId: String,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val status: String,
    val evaluationId: String?,
    val failReason: String?,
    val recordingAvailable: Boolean,
    val startedAt: Instant,
    val endedAt: Instant?,
    val retryable: Boolean,
    val savedAt: Instant?,
    val memo: String?,
) {
    companion object {
        fun of(detail: PracticeSessionDetail) = with(detail) {
            PracticeSessionResult(
                sessionId.toString(), songId.toString(), bpm, bpmUnit, startNoteIdx, endNoteIdx, status, evaluationId?.toString(),
                failReason, recordingAvailable, startedAt.utc(), endedAt?.utc(), retryable, savedAt?.utc(), memo,
            )
        }

        private fun LocalDateTime.utc() = toInstant(ZoneOffset.UTC)
    }
}
