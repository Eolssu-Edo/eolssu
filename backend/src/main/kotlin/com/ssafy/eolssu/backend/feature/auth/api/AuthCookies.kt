package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.domain.AuthTokenPolicy
import com.ssafy.eolssu.backend.feature.auth.domain.IssuedTokens
import java.time.Duration
import org.springframework.http.ResponseCookie

/**
 * 로그인 쿠키. 모두 HttpOnly·SameSite=Lax이고 Secure는 AUTH_COOKIE_SECURE(기본 true)를 따른다.
 * refresh_token은 재발급·로그아웃 요청에만 실리도록 Path를 /api/auth로 좁힌다.
 */
class AuthCookies(private val secure: Boolean) {
    fun issue(tokens: IssuedTokens): List<String> = listOf(
        cookie(ACCESS, tokens.accessToken, ACCESS_PATH, if (tokens.accessTokenExpiresAt == null) NON_EXPIRING_COOKIE_AGE else AuthTokenPolicy.ACCESS_TOKEN_TTL),
        cookie(REFRESH, tokens.refreshToken, REFRESH_PATH, if (tokens.refreshTokenExpiresAt == null) NON_EXPIRING_COOKIE_AGE else AuthTokenPolicy.REFRESH_TOKEN_TTL),
    )

    fun clear(): List<String> = listOf(cookie(ACCESS, "", ACCESS_PATH, Duration.ZERO), cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO))

    private fun cookie(name: String, value: String, path: String, maxAge: Duration) = ResponseCookie.from(name, value)
        .path(path).maxAge(maxAge).secure(secure).httpOnly(true).sameSite("Lax").build().toString()

    companion object {
        // 서버 토큰은 무기한이지만 브라우저의 쿠키 보관 상한은 없앨 수 없다.
        private val NON_EXPIRING_COOKIE_AGE = Duration.ofDays(400)
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        private const val ACCESS_PATH = "/api"
        private const val REFRESH_PATH = "/api/auth"
    }
}
