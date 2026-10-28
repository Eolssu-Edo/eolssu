package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.SignOutService
import com.ssafy.eolssu.backend.feature.auth.application.TokenRefreshResult
import com.ssafy.eolssu.backend.feature.auth.application.TokenRefreshService
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class TokenController(
    private val refreshService: TokenRefreshService,
    private val signOutService: SignOutService,
    private val cookies: AuthCookies,
) {
    @PostMapping("/api/auth/refresh")
    fun refresh(@CookieValue(AuthCookies.REFRESH, required = false) refreshToken: String?): ResponseEntity<*> =
        when (val result = refreshService.refresh(refreshToken)) {
            is TokenRefreshResult.Refreshed -> ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, *cookies.issue(result.tokens).toTypedArray())
                .body(SuccessResponse.of(HttpStatus.OK, "토큰 재발급 성공", Refreshed(result.tokens.accessTokenExpiresAt)))
            // 쓸 수 없는 쿠키는 지워 앱이 다시 로그인하게 한다.
            TokenRefreshResult.Unauthenticated -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, *cookies.clear().toTypedArray())
                .body(ErrorResponse.of(ErrorCode.UNAUTHENTICATED))
            TokenRefreshResult.Unavailable -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE))
        }

    /** AUTH-03. 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
    @PostMapping("/api/auth/sign-out")
    fun signOut(@AuthenticationPrincipal jwt: Jwt, @CookieValue(AuthCookies.REFRESH, required = false) refreshToken: String?): ResponseEntity<*> {
        signOutService.signOut(requireNotNull(jwt.subject).toLong(), refreshToken)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, *cookies.clear().toTypedArray())
            .body(SuccessResponse.of(HttpStatus.OK, "로그아웃 성공", SignedOut(true)))
    }
}

data class Refreshed(val accessTokenExpiresAt: Instant)

data class SignedOut(val signedOut: Boolean)
