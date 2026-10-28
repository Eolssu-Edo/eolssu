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

enum class RevokeReason { ROTATED, SIGN_OUT, PASSWORD_CHANGED, WITHDRAWN, REUSE_DETECTED }

class StoredRefreshToken(val id: Long, val userId: Long, val expiresAt: Instant, val revoked: Boolean)

/** 재발급·로그아웃용 리프레시 토큰 저장소. */
interface TokenRefreshRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findForUpdate(tokenHash: String): StoredRefreshToken?
    fun revoke(id: Long, reason: RevokeReason, now: Instant)
    fun revokeAll(userId: Long, reason: RevokeReason, now: Instant)
    fun memberStatus(userId: Long): MemberStatus?
}
