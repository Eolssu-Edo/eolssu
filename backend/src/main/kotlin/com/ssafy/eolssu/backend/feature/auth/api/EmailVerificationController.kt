package com.ssafy.eolssu.backend.feature.auth.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.auth.application.*
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.annotation.JsonDeserialize
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/api/auth/email-verifications")
@Profile("db")
class EmailVerificationController(private val service: EmailVerificationService) {
    @PostMapping
    fun send(@Valid @RequestBody request: SendVerificationRequest): ResponseEntity<*> = when (val result = service.send(request.email, request.purpose)) {
        is SendVerificationResult.Accepted -> {
            if (result.deliveryFailed) LoggerFactory.getLogger(javaClass).warn("Verification mail delivery unavailable")
            ResponseEntity.status(HttpStatus.ACCEPTED).body(
                VerificationResponse("ACCEPTED", true, "인증번호를 보냈어요", 202, SentVerification(result.verificationId.toString(), result.expiresAt, result.resendAvailableAt)),
            )
        }
        SendVerificationResult.AlreadyRegistered -> error(ErrorCode.EMAIL_ALREADY_EXISTS)
        SendVerificationResult.InvalidEmail -> error(ErrorCode.INVALID_REQUEST)
        is SendVerificationResult.RateLimited -> error(ErrorCode.TOO_MANY_REQUESTS, ResendDetails(result.resendAvailableAt))
        SendVerificationResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
    }

    @PostMapping("/{verificationId}/confirm")
    fun confirm(@PathVariable @Positive verificationId: Long, @Valid @RequestBody request: ConfirmVerificationRequest): ResponseEntity<*> =
        when (val result = service.confirm(verificationId, request.code)) {
            is ConfirmVerificationResult.Verified -> ResponseEntity.ok(
                VerificationResponse("OK", true, "이메일 인증 완료", 200, VerifiedEmail(result.verificationToken, result.email, result.expiresAt)),
            )
            ConfirmVerificationResult.Missing -> error(ErrorCode.RESOURCE_NOT_FOUND)
            ConfirmVerificationResult.Expired -> error(ErrorCode.VERIFICATION_EXPIRED)
            ConfirmVerificationResult.AttemptsExceeded -> error(ErrorCode.TOO_MANY_REQUESTS, AttemptDetails(0))
            is ConfirmVerificationResult.Mismatch -> error(ErrorCode.VERIFICATION_CODE_MISMATCH, AttemptDetails(result.remainingAttempts))
            ConfirmVerificationResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
        }

    private fun error(code: ErrorCode, details: Any? = null) = ResponseEntity.status(code.status).body(ErrorResponse.of(code, details))
}

data class SendVerificationRequest(@field:NotBlank @field:Email @field:Size(max = 320) val email: String, val purpose: VerificationPurpose)
data class ConfirmVerificationRequest(@param:JsonDeserialize(using = VerificationCodeDeserializer::class) @field:Pattern(regexp = "[0-9]{6}") val code: String)
data class VerificationResponse<T>(val httpStatus: String, @get:JsonProperty("isSuccess") val isSuccess: Boolean, val message: String, val code: Int, val result: T)
data class SentVerification(val verificationId: String, val expiresAt: Instant, val resendAvailableAt: Instant)
class VerifiedEmail(val verificationToken: String, val email: String, val expiresAt: Instant)
data class ResendDetails(val resendAvailableAt: Instant)
data class AttemptDetails(val remainingAttempts: Int)
