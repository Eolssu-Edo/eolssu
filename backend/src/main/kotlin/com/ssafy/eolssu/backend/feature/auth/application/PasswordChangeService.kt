package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.IssuedTokens
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordChangeRepository
import com.ssafy.eolssu.backend.feature.auth.domain.PasswordPolicy
import com.ssafy.eolssu.backend.feature.auth.domain.TokenIssuingUnavailable
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * USR-04. 현재 비밀번호를 다시 확인하고 새 해시를 저장한 뒤, 이 회원의 리프레시 토큰을 모두 폐기하고 현재 기기에만 새로 발급한다.
 * 현재 비밀번호 실패는 로그인과 같은 규칙(15분 5회 → 15분 잠금)으로 세되, 로그인 잠금과는 따로 센다.
 */
class PasswordChangeService(
    private val repository: PasswordChangeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: AuthTokenIssuer,
    private val attempts: LoginAttemptLimiter,
    private val clock: Clock,
) {
    fun change(userId: Long, currentPassword: String, newPassword: String): PasswordChangeResult {
        if (newPassword == currentPassword || !PasswordPolicy.isValid(newPassword)) return PasswordChangeResult.InvalidRequest
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val lockKey = "member:$userId"
        attempts.lockedUntil(lockKey, now)?.let { return PasswordChangeResult.TooManyAttempts(it) }
        val verifiedHash = repository.findActivePasswordHash(userId) ?: return PasswordChangeResult.NotFound
        // Argon2 계산은 DB 연결·행 잠금 밖에서 끝낸다.
        if (!passwordEncoder.matches(currentPassword, verifiedHash)) {
            attempts.recordFailure(lockKey, now)?.let { return PasswordChangeResult.TooManyAttempts(it) }
            return PasswordChangeResult.CurrentPasswordMismatch
        }
        val newHash = checkNotNull(passwordEncoder.encode(newPassword))
        val result = try {
            repository.inTransaction { save(userId, verifiedHash, newHash, now) }
        } catch (_: TokenIssuingUnavailable) {
            return PasswordChangeResult.Unavailable
        }
        if (result is PasswordChangeResult.Changed) attempts.reset(lockKey)
        return result
    }

    /** 확인한 뒤 비밀번호가 바뀌었으면 저장하지 않는다. 폐기를 먼저 해야 새로 발급한 토큰이 함께 폐기되지 않는다. */
    private fun save(userId: Long, verifiedHash: String, newHash: String, now: Instant): PasswordChangeResult {
        val lockedHash = repository.findActivePasswordHashForUpdate(userId) ?: return PasswordChangeResult.NotFound
        if (lockedHash != verifiedHash) return PasswordChangeResult.CurrentPasswordMismatch
        repository.updatePasswordHash(userId, newHash)
        repository.revokeAllRefreshTokens(userId, now)
        return PasswordChangeResult.Changed(tokenIssuer.issue(userId))
    }
}

sealed interface PasswordChangeResult {
    class Changed(val tokens: IssuedTokens) : PasswordChangeResult
    data object InvalidRequest : PasswordChangeResult
    data object CurrentPasswordMismatch : PasswordChangeResult
    data class TooManyAttempts(val retryAvailableAt: Instant) : PasswordChangeResult
    data object NotFound : PasswordChangeResult
    data object Unavailable : PasswordChangeResult
}
