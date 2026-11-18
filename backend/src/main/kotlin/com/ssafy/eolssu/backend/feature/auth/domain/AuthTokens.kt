package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Duration
import java.time.Instant

object AuthTokenPolicy {
    val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(30)
    val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(14)
}

/** 로그인 쿠키로만 내려보낸다. 토큰 원문이 로그에 남지 않게 data class로 두지 않는다. */
class IssuedTokens(val accessToken: String, val accessTokenExpiresAt: Instant?, val refreshToken: String, val refreshTokenExpiresAt: Instant?)

interface AccessTokenSigner {
    fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant): String
    fun signNonExpiring(userId: Long, issuedAt: Instant, sessionHash: String): String = throw TokenIssuingUnavailable()
    /** 로그인 세션 ID를 sid 클레임으로 넣는다. 세션을 끊으면 인증 필터가 만료 전이라도 거절한다. */
    fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant, sessionId: String): String = sign(userId, issuedAt, expiresAt)
    fun signNonExpiring(userId: Long, issuedAt: Instant, sessionHash: String, sessionId: String): String = signNonExpiring(userId, issuedAt, sessionHash)
}

/** 서명 키가 없어 로그인 토큰을 발급할 수 없다(공통 503). */
class TokenIssuingUnavailable : RuntimeException("Access token signing unavailable")

interface RefreshTokenRepository {
    fun insert(userId: Long, tokenHash: String, expiresAt: Instant?, now: Instant): Long
    /** [sessionId]는 refresh 회전이 이어받는 로그인 세션 ID다. */
    fun insert(userId: Long, tokenHash: String, sessionId: String, expiresAt: Instant?, now: Instant): Long = insert(userId, tokenHash, expiresAt, now)
}

/** 만료 없는 access token도 서버의 활성 리프레시 세션에 묶어 폐기할 수 있어야 한다. */
fun interface NonExpiringSessionCheck {
    fun isActive(userId: Long, sessionHash: String): Boolean
}

enum class RevokeReason { ROTATED, SIGN_OUT, PASSWORD_CHANGED, WITHDRAWN, REUSE_DETECTED }

class StoredRefreshToken(val id: Long, val userId: Long, val expiresAt: Instant?, val revokeReason: RevokeReason?, val sessionId: String? = null) {
    val revoked get() = revokeReason != null
}

/** 재발급·로그아웃용 리프레시 토큰 저장소. */
interface TokenRefreshRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    /** 잠그지 않고 찾는다. 회원 행을 먼저 잠그려고 회원 ID만 얻을 때 쓴다. */
    fun find(tokenHash: String): StoredRefreshToken?
    fun findForUpdate(tokenHash: String): StoredRefreshToken?
    fun revoke(id: Long, reason: RevokeReason, now: Instant)
    fun revokeAll(userId: Long, reason: RevokeReason, now: Instant)
    /** 회원 행을 잠그고 상태를 읽는다. 로그인·비밀번호 변경처럼 users → refresh_tokens 순서로 잠가 교착을 막는다. */
    fun memberStatusForUpdate(userId: Long): MemberStatus?
}
