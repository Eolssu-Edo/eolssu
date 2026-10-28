package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * AUTH-04. refresh_token 쿠키로만 인증하고, 재발급마다 이전 토큰을 폐기한다(회전).
 * 재발급으로 폐기된(ROTATED) 토큰이 다시 오면 탈취로 보고 그 회원의 토큰을 모두 폐기한다. 두 탭이 동시에 재발급하면 한쪽이 여기에 걸려 로그아웃될 수 있다.
 * 비밀번호 변경·로그아웃·탈퇴로 폐기된 토큰은 다른 기기에 남은 정상 토큰일 수 있어 401만 준다. 전부 폐기하면 비밀번호를 바꾼 기기까지 로그아웃된다.
 */
class TokenRefreshService(
    private val repository: TokenRefreshRepository,
    private val tokenIssuer: AuthTokenIssuer,
    private val secrets: VerificationSecrets,
    private val clock: Clock,
) {
    fun refresh(refreshToken: String?): TokenRefreshResult {
        if (refreshToken.isNullOrBlank()) return TokenRefreshResult.Unauthenticated
        return try {
            repository.inTransaction { rotate(refreshToken) }
        } catch (_: TokenIssuingUnavailable) {
            TokenRefreshResult.Unavailable
        }
    }

    private fun rotate(refreshToken: String): TokenRefreshResult {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val stored = repository.findForUpdate(secrets.hashToken(refreshToken)) ?: return TokenRefreshResult.Unauthenticated
        if (stored.revokeReason == RevokeReason.ROTATED) {
            repository.revokeAll(stored.userId, RevokeReason.REUSE_DETECTED, now)
            return TokenRefreshResult.Unauthenticated
        }
        if (stored.revoked) return TokenRefreshResult.Unauthenticated
        if (!stored.expiresAt.isAfter(now) || repository.memberStatus(stored.userId) != MemberStatus.ACTIVE) {
            return TokenRefreshResult.Unauthenticated
        }
        repository.revoke(stored.id, RevokeReason.ROTATED, now)
        return TokenRefreshResult.Refreshed(tokenIssuer.issue(stored.userId))
    }
}

sealed interface TokenRefreshResult {
    class Refreshed(val tokens: IssuedTokens) : TokenRefreshResult
    data object Unauthenticated : TokenRefreshResult
    data object Unavailable : TokenRefreshResult
}
