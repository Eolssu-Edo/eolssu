package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.RevokeReason
import com.ssafy.eolssu.backend.feature.auth.domain.TokenRefreshRepository
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import java.time.Clock
import java.time.temporal.ChronoUnit

/** AUTH-03. 현재 기기의 리프레시 토큰만 폐기한다. 다른 회원의 토큰이 쿠키로 와도 건드리지 않는다. */
class SignOutService(
    private val repository: TokenRefreshRepository,
    private val secrets: VerificationSecrets,
    private val clock: Clock,
) {
    fun signOut(userId: Long, refreshToken: String?, sessionHash: String? = null) {
        val tokenHash = sessionHash ?: refreshToken?.takeIf { it.isNotBlank() }?.let(secrets::hashToken) ?: return
        repository.inTransaction {
            val stored = repository.findForUpdate(tokenHash)
            if (stored != null && stored.userId == userId && !stored.revoked) {
                repository.revoke(stored.id, RevokeReason.SIGN_OUT, clock.instant().truncatedTo(ChronoUnit.MICROS))
            }
        }
    }
}
