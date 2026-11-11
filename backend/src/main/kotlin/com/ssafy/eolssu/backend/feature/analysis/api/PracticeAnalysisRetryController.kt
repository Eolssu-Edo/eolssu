package com.ssafy.eolssu.backend.feature.analysis.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.analysis.application.RetryOutcome
import com.ssafy.eolssu.backend.feature.analysis.application.RetryPracticeAnalysis
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.util.concurrent.RejectedExecutionException
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/practice-sessions")
@Profile("db | ops")
class PracticeAnalysisRetryController(private val retryPracticeAnalysis: RetryPracticeAnalysis) {
    /**
     * API 명세 `POST /practice-sessions/{sessionId}/retry`(PRA-03 분석 다시 시도).
     * 이미 분석 중이면 새 시도 없이 같은 202로 답한다. 재녹음이 필요하거나 끝났거나 업로드 전이면 409, 잠시 후 다시면 503이다.
     * 유스케이스는 트랜잭션 밖에서 불러야 하므로 이 메서드에 트랜잭션을 걸지 않는다.
     */
    @PostMapping("/{sessionId}/retry")
    fun retry(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable @Positive sessionId: Long,
        // 명세상 필수지만 저장하지 않는다. 같은 요청을 다시 보내도 세션이 이미 PENDING이라 새 시도가 생기지 않는다.
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,100}") idempotencyKey: String,
        @RequestHeader(EvaluationTimingId.HEADER, required = false) timingId: String? = null,
    ): ResponseEntity<*> {
        val outcome = try {
            retryPracticeAnalysis.retry(
                requireNotNull(jwt.subject).toLong(), sessionId, EvaluationTimingId.parse(timingId),
            )
        } catch (e: RejectedExecutionException) {
            // 분석을 실행기에 넘기지 못했다(대기열 가득 참, 분석 연결 전). 유스케이스가 그 시도를 SERVER_ERROR로 끝냈다.
            return error(ErrorCode.SERVICE_UNAVAILABLE)
        }
        return when (outcome) {
            is RetryOutcome.Started, is RetryOutcome.AlreadyPending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(RetryResponse(result = RetryResult(sessionId.toString(), "PENDING", retryable = false)))
            is RetryOutcome.NotRetryable -> error(ErrorCode.STATE_CONFLICT)
            RetryOutcome.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            RetryOutcome.Busy -> error(ErrorCode.SERVICE_UNAVAILABLE)
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class RetryResponse(
    val httpStatus: String = "ACCEPTED",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "실패한 연주 분석 재시도 성공",
    val code: Int = 202,
    val result: RetryResult,
)

/** DB ID는 문자열(API 명세 공통 타입). 분석 중이라 같은 녹음으로 다시 시도할 수 없다. */
data class RetryResult(val sessionId: String, val status: String, val retryable: Boolean)
