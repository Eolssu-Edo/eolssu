package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.TokenRefreshResult
import com.ssafy.eolssu.backend.feature.auth.application.TokenRefreshService
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db")
class TokenController(private val refreshService: TokenRefreshService, private val cookies: AuthCookies) {
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
}

data class Refreshed(val accessTokenExpiresAt: Instant)
