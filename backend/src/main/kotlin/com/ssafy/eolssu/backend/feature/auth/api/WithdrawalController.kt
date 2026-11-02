package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalResult
import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalService
import com.ssafy.eolssu.backend.feature.auth.application.SocialAuthService
import com.ssafy.eolssu.backend.feature.auth.application.SocialAuthFailure
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.Pattern
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** USR-05. 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
@RestController
@Profile("db | ops")
class WithdrawalController(private val service: WithdrawalService, private val cookies: AuthCookies,private val social:SocialAuthService) {
    @DeleteMapping("/api/users/me")
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: WithdrawalRequest,http:HttpServletRequest): ResponseEntity<*> {
        val hasPassword=!request.currentPassword.isNullOrBlank()
        val hasTicket=!request.withdrawalTicket.isNullOrBlank()
        if(hasPassword==hasTicket) return error(ErrorCode.INVALID_REQUEST)
        val result=try {
            if(hasTicket) {
                val bindings=listOf("kakao","google","naver").mapNotNull{provider->http.cookies?.filter{it.name=="oauth_flow_$provider"}?.singleOrNull()?.value?.let{provider.uppercase() to it}}.toMap()
                social.withdraw(requireNotNull(jwt.subject).toLong(),checkNotNull(request.withdrawalTicket),bindings)
            } else service.withdraw(requireNotNull(jwt.subject).toLong(),checkNotNull(request.currentPassword))
        } catch(failure:SocialAuthFailure){return error(failure.code,failure.details)}
        return when (result) {
            // 대기 중에는 로그인 외 회원 API를 쓸 수 없으므로 두 쿠키를 지운다.
            is WithdrawalResult.Withdrawn -> ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, *cookies.clear().toTypedArray())
                .body(SuccessResponse.of(HttpStatus.OK, "회원탈퇴 성공", Withdrawn(true, result.requestedAt, result.purgeAt)))
            WithdrawalResult.CurrentPasswordMismatch -> error(ErrorCode.CURRENT_PASSWORD_MISMATCH)
            is WithdrawalResult.TooManyAttempts -> error(ErrorCode.TOO_MANY_REQUESTS, RetryDetails(result.retryAvailableAt))
            // 토큰은 유효하지만 회원 행이 없거나 ACTIVE가 아니면 다시 로그인하게 한다.
            WithdrawalResult.NotFound -> error(ErrorCode.UNAUTHENTICATED)
        }

    }

    private fun error(code: ErrorCode, details: Any? = null) = ResponseEntity.status(code.status).body(ErrorResponse.of(code, details))
}

/** 보충 문자는 UTF-16으로 2칸이라 비밀번호 규칙 128자의 두 배까지 받는다. */
class WithdrawalRequest(
    @field:Size(max = 256) val currentPassword:String?=null,
    @field:Pattern(regexp="[A-Za-z0-9_-]{43}") val withdrawalTicket:String?=null,
)

data class Withdrawn(val withdrawn: Boolean, val withdrawnAt: Instant, val purgeAt: Instant)
