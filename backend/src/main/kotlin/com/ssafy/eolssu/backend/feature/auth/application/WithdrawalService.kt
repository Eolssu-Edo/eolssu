package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPolicy
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * USR-05. 현재 비밀번호를 다시 확인하고 탈퇴 대기로 바꾼 뒤 이 회원의 리프레시 토큰을 모두 폐기한다.
 * 현재 비밀번호 실패는 비밀번호 변경(USR-04)과 같은 잠금(attempts, 회원 키)으로 함께 센다.
 */
class WithdrawalService(
    private val repository: WithdrawalRepository,
    private val passwordEncoder: PasswordEncoder,
    private val attempts: LoginAttemptLimiter,
    private val clock: Clock,
    /** 탈퇴 신청 커밋 뒤 제공자 토큰(Apple)을 해지한다. 소셜 기능이 없는 구성에서는 null. */
    private val revocation: ProviderTokenRevocation? = null,
) {
    fun withdraw(userId: Long, currentPassword: String): WithdrawalResult {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val lockKey = "member:$userId"
        attempts.lockedUntil(lockKey, now)?.let { return WithdrawalResult.TooManyAttempts(it) }
        val verifiedHash = repository.findActivePasswordHash(userId) ?: return WithdrawalResult.NotFound
        // Argon2 계산은 DB 연결·행 잠금 밖에서 끝낸다.
        if (!passwordEncoder.matches(currentPassword, verifiedHash)) {
            attempts.recordFailure(lockKey, now)?.let { return WithdrawalResult.TooManyAttempts(it) }
            return WithdrawalResult.CurrentPasswordMismatch
        }
        val result = repository.inTransaction { markPending(userId, verifiedHash, now) }
        if (result is WithdrawalResult.Withdrawn) {
            attempts.reset(lockKey)
            revocation?.revokeAll(userId)
        }
        return result
    }

    /** 확인한 뒤 비밀번호가 바뀌었으면 탈퇴하지 않는다. users → refresh_tokens 순서로 잠근다. */
    private fun markPending(userId: Long, verifiedHash: String, now: Instant): WithdrawalResult {
        val lockedHash = repository.findActivePasswordHashForUpdate(userId) ?: return WithdrawalResult.NotFound
        if (lockedHash != verifiedHash) return WithdrawalResult.CurrentPasswordMismatch
        val purgeAt = WithdrawalPolicy.purgeAt(now)
        repository.markWithdrawalPending(userId, now, purgeAt)
        repository.revokeAllRefreshTokens(userId, now)
        return WithdrawalResult.Withdrawn(now, purgeAt)
    }
}

sealed interface WithdrawalResult {
    data class Withdrawn(val requestedAt: Instant, val purgeAt: Instant) : WithdrawalResult
    data object CurrentPasswordMismatch : WithdrawalResult
    data class TooManyAttempts(val retryAvailableAt: Instant) : WithdrawalResult
    data object NotFound : WithdrawalResult
}
