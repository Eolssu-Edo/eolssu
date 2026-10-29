package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

class ResetMember(val id: Long, val email: String, val passwordHash: String)

interface PasswordResetRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findVerification(tokenHash: String): ConsumableVerification?
    fun findVerificationForUpdate(tokenHash: String): ConsumableVerification?
    fun findMember(email: String): ResetMember?
    fun findMemberForUpdate(userId: Long): ResetMember?
    fun updatePasswordHash(userId: Long, passwordHash: String)
    fun markConsumed(verificationId: Long, now: Instant)
    fun revokeAllRefreshTokens(userId: Long, now: Instant)
}
