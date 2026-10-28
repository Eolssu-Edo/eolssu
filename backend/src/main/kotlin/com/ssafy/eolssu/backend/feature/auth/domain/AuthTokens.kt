package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Duration
import java.time.Instant

object AuthTokenPolicy {
    val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(30)
    val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(14)
}

/** 로그인 쿠키로만 내려보낸다. 토큰 원문이 로그에 남지 않게 data class로 두지 않는다. */
class IssuedTokens(val accessToken: String, val accessTokenExpiresAt: Instant, val refreshToken: String, val refreshTokenExpiresAt: Instant)

interface AccessTokenSigner {
    fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant): String
}

/** 서명 키가 없어 로그인 토큰을 발급할 수 없다(공통 503). */
class TokenIssuingUnavailable : RuntimeException("Access token signing unavailable")

interface RefreshTokenRepository {
    fun insert(userId: Long, tokenHash: String, expiresAt: Instant, now: Instant): Long
}
