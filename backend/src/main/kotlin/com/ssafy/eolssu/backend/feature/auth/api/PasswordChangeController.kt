package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.PasswordChangeResult
import com.ssafy.eolssu.backend.feature.auth.application.PasswordChangeService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** USR-04. 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
@RestController
@Profile("db | ops")
class PasswordChangeController(private val service: PasswordChangeService, private val cookies: AuthCookies) {
    @PatchMapping("/api/users/me/password")
    fun change(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: PasswordChangeRequest): ResponseEntity<*> =
        when (val result = service.change(requireNotNull(jwt.subject).toLong(), request.currentPassword, request.newPassword)) {
            is PasswordChangeResult.Changed -> ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, *cookies.issue(result.tokens).toTypedArray())
                .body(SuccessResponse.of(HttpStatus.OK, "비밀번호 변경 성공", PasswordChanged(true)))
            PasswordChangeResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
            PasswordChangeResult.CurrentPasswordMismatch -> error(ErrorCode.CURRENT_PASSWORD_MISMATCH)
            is PasswordChangeResult.TooManyAttempts -> error(ErrorCode.TOO_MANY_REQUESTS, RetryDetails(result.retryAvailableAt))
            // 토큰은 유효하지만 회원 행이 없거나 ACTIVE가 아니면 다시 로그인하게 한다(회원 행이 없을 때 401은 GRO-04와 같음).
            PasswordChangeResult.NotFound -> error(ErrorCode.UNAUTHENTICATED)
            PasswordChangeResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
        }

    private fun error(code: ErrorCode, details: Any? = null) = ResponseEntity.status(code.status).body(ErrorResponse.of(code, details))
}

/** 새 비밀번호 규칙은 서비스의 PasswordPolicy가 판정한다. 보충 문자는 UTF-16으로 2칸이라 128자의 두 배까지 받는다. */
class PasswordChangeRequest(
    @field:NotBlank @field:Size(max = 256) val currentPassword: String,
    @field:NotBlank @field:Size(max = 256) val newPassword: String,
)

data class PasswordChanged(val changed: Boolean)
