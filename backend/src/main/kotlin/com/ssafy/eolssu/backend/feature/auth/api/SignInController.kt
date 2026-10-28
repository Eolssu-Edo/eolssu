package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.SignInResult
import com.ssafy.eolssu.backend.feature.auth.application.SignInService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class SignInController(private val service: SignInService, private val cookies: AuthCookies) {
    @PostMapping("/api/auth/sign-in")
    fun signIn(@Valid @RequestBody request: SignInRequest): ResponseEntity<*> = when (val result = service.signIn(request.email, request.password)) {
        is SignInResult.SignedIn -> ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, *cookies.issue(result.tokens).toTypedArray())
            .body(SuccessResponse.of(HttpStatus.OK, "로그인 성공", SignedIn(result)))
        SignInResult.InvalidCredentials -> error(ErrorCode.INVALID_CREDENTIALS)
        is SignInResult.TooManyAttempts -> error(ErrorCode.TOO_MANY_REQUESTS, RetryDetails(result.retryAvailableAt))
        SignInResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
    }

    private fun error(code: ErrorCode, details: Any? = null) = ResponseEntity.status(code.status).body(ErrorResponse.of(code, details))
}

class SignInRequest(
    @field:NotBlank @field:Email @field:Size(max = 320) val email: String,
    // 가입 규칙은 글자(코드 포인트) 128자다. 보충 문자는 UTF-16으로 2칸이라 여기서는 두 배까지 받고 판정은 비밀번호 비교에 맡긴다.
    @field:NotBlank @field:Size(max = 256) val password: String,
)

data class SignedIn(val userId: String, val nickname: String, val instrumentId: String, val notationMode: String, val withdrawalCancelled: Boolean) {
    constructor(result: SignInResult.SignedIn) : this(
        result.member.id.toString(), result.member.nickname, result.member.instrumentId.toString(), result.member.notationMode, result.withdrawalCancelled,
    )
}

data class RetryDetails(val retryAvailableAt: Instant)
