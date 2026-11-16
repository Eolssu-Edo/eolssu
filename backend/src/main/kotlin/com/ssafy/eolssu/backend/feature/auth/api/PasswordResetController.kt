package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordPolicy
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.auth.application.PasswordResetResult
import com.ssafy.eolssu.backend.feature.auth.application.PasswordResetService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class PasswordResetController(private val service: PasswordResetService) {
    @PostMapping("/api/auth/password-reset")
    fun reset(@Valid @RequestBody request: PasswordResetRequest): ResponseEntity<*> = when (service.reset(request.verificationToken, request.newPassword)) {
        PasswordResetResult.Reset -> ResponseEntity.noContent().build<Void>()
        PasswordResetResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
        PasswordResetResult.EmailNotVerified -> error(ErrorCode.EMAIL_NOT_VERIFIED)
    }
    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

class PasswordResetRequest(
    @field:NotBlank @field:Size(max = 256) val verificationToken: String,
    @field:NotBlank @field:Size(max = 2 * PasswordPolicy.MAX_LENGTH) val newPassword: String,
)
