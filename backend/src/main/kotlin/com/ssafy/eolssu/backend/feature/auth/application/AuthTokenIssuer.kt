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

    fun issue(userId: Long): IssuedTokens {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val nonExpiring = isNonExpiringUser(userId)
        val accessExpiresAt = if (nonExpiring) null else now.plus(AuthTokenPolicy.ACCESS_TOKEN_TTL)
        val refreshExpiresAt = if (nonExpiring) null else now.plus(AuthTokenPolicy.REFRESH_TOKEN_TTL)
        val refresh = secrets.generateToken()
        val sessionHash = secrets.hashToken(refresh)
        // 서명할 수 없으면 리프레시 토큰도 남기지 않는다.
        val access = if (nonExpiring) signer.signNonExpiring(userId, now, sessionHash)
            else signer.sign(userId, now, requireNotNull(accessExpiresAt))
        refreshTokens.insert(userId, sessionHash, refreshExpiresAt, now)
        return IssuedTokens(access, accessExpiresAt, refresh, refreshExpiresAt)
    }
}
