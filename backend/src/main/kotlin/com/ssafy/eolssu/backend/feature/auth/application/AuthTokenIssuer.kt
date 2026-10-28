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
) {
    fun issue(userId: Long): IssuedTokens {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val accessExpiresAt = now.plus(AuthTokenPolicy.ACCESS_TOKEN_TTL)
        // 서명할 수 없으면 리프레시 토큰도 남기지 않는다.
        val access = signer.sign(userId, now, accessExpiresAt)
        val refresh = secrets.generateToken()
        val refreshExpiresAt = now.plus(AuthTokenPolicy.REFRESH_TOKEN_TTL)
        refreshTokens.insert(userId, secrets.hashToken(refresh), refreshExpiresAt, now)
        return IssuedTokens(access, accessExpiresAt, refresh, refreshExpiresAt)
    }
}
