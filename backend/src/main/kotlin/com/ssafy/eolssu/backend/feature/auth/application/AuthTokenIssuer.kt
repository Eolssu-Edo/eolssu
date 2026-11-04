package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Clock
import java.time.temporal.ChronoUnit

/** 가입·로그인·재발급 공통. 호출한 쪽의 트랜잭션 안에서 리프레시 토큰 해시를 저장한다. */
class AuthTokenIssuer(
    private val signer: AccessTokenSigner,
    private val refreshTokens: RefreshTokenRepository,
    private val secrets: VerificationSecrets,
    private val clock: Clock,
    private val nonExpiringUserIds: Set<Long> = emptySet(),
) {
    fun isNonExpiringUser(userId: Long) = userId in nonExpiringUserIds

    /** [sessionId]가 없으면 새 로그인 세션을 만들고, refresh 회전은 이전 세션 ID를 넘겨 이어받는다. */
    fun issue(userId: Long, sessionId: String? = null): IssuedTokens {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val nonExpiring = isNonExpiringUser(userId)
        val accessExpiresAt = if (nonExpiring) null else now.plus(AuthTokenPolicy.ACCESS_TOKEN_TTL)
        val refreshExpiresAt = if (nonExpiring) null else now.plus(AuthTokenPolicy.REFRESH_TOKEN_TTL)
        val refresh = secrets.generateToken()
        val sessionHash = secrets.hashToken(refresh)
        val session = sessionId ?: secrets.generateToken()
        // 서명할 수 없으면 리프레시 토큰도 남기지 않는다.
        val access = if (nonExpiring) signer.signNonExpiring(userId, now, sessionHash, session)
            else signer.sign(userId, now, requireNotNull(accessExpiresAt), session)
        refreshTokens.insert(userId, sessionHash, session, refreshExpiresAt, now)
        return IssuedTokens(access, accessExpiresAt, refresh, refreshExpiresAt)
    }
}
